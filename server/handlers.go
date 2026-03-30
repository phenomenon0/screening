package main

import (
	"context"
	"embed"
	"encoding/json"
	"fmt"
	"io"
	"io/fs"
	"log"
	"net/http"
	"os"
	"path/filepath"
	"strings"

	qrcode "github.com/skip2/go-qrcode"
	"nhooyr.io/websocket"
)

//go:embed web
var webFS embed.FS

//go:embed scene
var sceneFS embed.FS

func handleQRCode(localIP string, port int) http.HandlerFunc {
	// Generate once at startup — content never changes
	url := fmt.Sprintf("http://%s:%d", localIP, port)
	png, _ := qrcode.Encode(url, qrcode.Medium, 256)
	return func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "image/png")
		w.Header().Set("Cache-Control", "public, max-age=86400")
		w.Write(png)
	}
}

func handleHealth(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	w.Write([]byte(`{"ok":true}`))
}

func handleWebUI() http.Handler {
	sub, _ := fs.Sub(webFS, "web")
	return http.FileServer(http.FS(sub))
}

func handleSceneUI() http.Handler {
	sub, _ := fs.Sub(sceneFS, "scene")
	return http.FileServer(http.FS(sub))
}

func handleSceneList(sm *SceneManager) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		// GET /scenes/ → list all scenes
		// GET /scenes/{id} → get specific scene JSON
		path := strings.TrimPrefix(r.URL.Path, "/scenes/")
		if path == "" || path == "/" {
			w.Header().Set("Content-Type", "application/json")
			json.NewEncoder(w).Encode(sm.List())
			return
		}
		// Specific scene
		id := strings.TrimSuffix(path, ".json")
		data, err := sm.Get(id)
		if err != nil {
			http.Error(w, "scene not found", http.StatusNotFound)
			return
		}
		w.Header().Set("Content-Type", "application/json")
		w.Write(data)
	}
}

func handleWebSocket(hub *Hub, fs *FrameStream, ss *SceneStream) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		conn, err := websocket.Accept(w, r, &websocket.AcceptOptions{
			InsecureSkipVerify: true,
		})
		if err != nil {
			log.Printf("ws accept error: %v", err)
			return
		}
		// Allow large binary messages (JPEG frames from scene renderer)
		conn.SetReadLimit(1024 * 1024) // 1MB

		ctx, cancel := context.WithCancel(r.Context())
		clientID := hub.Add(conn, cancel)
		defer hub.Remove(conn)

		log.Printf("ws: client %s connected from %s", clientID, r.RemoteAddr)

		if err := hub.SendFullState(ctx, conn); err != nil {
			log.Printf("ws: send full state error: %v", err)
			return
		}

		for {
			msgType, data, err := conn.Read(ctx)
			if err != nil {
				if websocket.CloseStatus(err) == websocket.StatusNormalClosure ||
					websocket.CloseStatus(err) == websocket.StatusGoingAway {
					log.Printf("ws: client disconnected normally")
				} else {
					log.Printf("ws: read error: %v", err)
				}
				return
			}

			// Binary message = JPEG frame from scene renderer
			if msgType == websocket.MessageBinary {
				if fs != nil {
					fs.PushFrame(data)
				}
				if ss != nil {
					ss.FeedFrame(data)
				}
				continue
			}

			var msg ClientMessage
			if err := json.Unmarshal(data, &msg); err != nil {
				continue
			}

			switch msg.Type {
			case "register":
				hub.Register(conn, msg.DeviceType, msg.DeviceName)
				log.Printf("ws: client %s registered as %s (%s)", clientID, msg.DeviceType, msg.DeviceName)
				// Enable scene stream when a renderer connects (ffmpeg starts on first frame)
				if msg.DeviceType == "scene_renderer" && ss != nil {
					url := ss.Enable()
					log.Printf("scene stream enabled at %s", url)
				}
			case "pong":
			case "todo_toggle":
				if hub.todos.Toggle(msg.ID) {
					hub.BroadcastTodos()
				}
			case "todo_add":
				if msg.Text != "" {
					hub.todos.Add(msg.Text, msg.Priority)
					hub.BroadcastTodos()
				}
			case "todo_delete":
				if hub.todos.Delete(msg.ID) {
					hub.BroadcastTodos()
				}
			case "habit_add":
				if msg.Name != "" {
					hub.habits.Add(msg.Name)
					hub.BroadcastHabits()
				}
			case "habit_toggle":
				hub.habits.Toggle(msg.ID)
				hub.BroadcastHabits()
			case "habit_delete":
				if hub.habits.Delete(msg.ID) {
					hub.BroadcastHabits()
				}
			case "pomodoro_start":
				hub.pomodoro.Start()
				hub.BroadcastPomodoro()
			case "pomodoro_pause":
				hub.pomodoro.Pause()
				hub.BroadcastPomodoro()
			case "pomodoro_reset":
				hub.pomodoro.Reset()
				hub.BroadcastPomodoro()
			case "pomodoro_set":
				if msg.Minutes > 0 {
					hub.pomodoro.Set(msg.Minutes)
					hub.BroadcastPomodoro()
				}
			case "frame_change":
				hub.Broadcast(ServerMessage{Type: "frame_change", Frame: msg.Frame})
			case "scene_load":
				if msg.SceneID != "" {
					hub.Broadcast(ServerMessage{Type: "scene_load", SceneID: msg.SceneID})
					hub.Broadcast(ServerMessage{Type: "frame_change", Frame: 5})
				}
			case "scene_camera_update":
				// Relay raw JSON to scene renderers — camera data passes through untouched
				hub.SendRawToType("scene_renderer", data)
			case "screen_share_start":
				hub.HandleScreenShare(true)
			case "screen_share_stop":
				hub.HandleScreenShare(false)
			default:
				log.Printf("ws: unknown message type: %s", msg.Type)
			}
		}
	}
}

func handleFileServe(dir string) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		parts := strings.SplitN(r.URL.Path, "/", 3)
		if len(parts) < 3 || parts[2] == "" {
			http.Error(w, "missing filename", http.StatusBadRequest)
			return
		}
		filename := parts[2]
		f, err := http.Dir(dir).Open(filename)
		if err != nil {
			http.Error(w, "not found", http.StatusNotFound)
			return
		}
		defer f.Close()
		stat, err := f.Stat()
		if err != nil || stat.IsDir() {
			http.Error(w, "not found", http.StatusNotFound)
			return
		}
		http.ServeContent(w, r, filename, stat.ModTime(), f.(io.ReadSeeker))
	}
}

func handleUpload(imagesDir, videosDir, musicDir, assetsDir string) http.HandlerFunc {
	imageExtsSet := map[string]bool{".jpg": true, ".jpeg": true, ".png": true, ".gif": true, ".webp": true, ".bmp": true}
	videoExtsSet := map[string]bool{".mp4": true, ".mkv": true, ".webm": true, ".mov": true, ".avi": true}
	musicExtsSet := map[string]bool{".mp3": true, ".flac": true, ".ogg": true, ".wav": true, ".m4a": true, ".aac": true}
	assetExtsSet := map[string]bool{".glb": true, ".gltf": true}

	return func(w http.ResponseWriter, r *http.Request) {
		if r.Method != "POST" {
			http.Error(w, "POST only", http.StatusMethodNotAllowed)
			return
		}

		r.ParseMultipartForm(100 << 20) // 100MB max
		file, header, err := r.FormFile("file")
		if err != nil {
			http.Error(w, "no file", http.StatusBadRequest)
			return
		}
		defer file.Close()

		ext := strings.ToLower(filepath.Ext(header.Filename))
		var destDir string
		switch {
		case imageExtsSet[ext]:
			destDir = imagesDir
		case videoExtsSet[ext]:
			destDir = videosDir
		case musicExtsSet[ext]:
			destDir = musicDir
		case assetExtsSet[ext]:
			destDir = assetsDir
		default:
			http.Error(w, fmt.Sprintf("unsupported file type: %s", ext), http.StatusBadRequest)
			return
		}

		destPath := filepath.Join(destDir, filepath.Base(header.Filename))
		dst, err := os.Create(destPath)
		if err != nil {
			http.Error(w, "write error", http.StatusInternalServerError)
			return
		}
		defer dst.Close()
		io.Copy(dst, file)

		log.Printf("upload: saved %s to %s", header.Filename, destDir)
		w.WriteHeader(http.StatusOK)
		w.Write([]byte(`{"ok":true}`))
	}
}
