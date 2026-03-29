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

func handleQRCode(localIP string, port int) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		url := fmt.Sprintf("http://%s:%d", localIP, port)
		png, err := qrcode.Encode(url, qrcode.Medium, 256)
		if err != nil {
			http.Error(w, "qr error", http.StatusInternalServerError)
			return
		}
		w.Header().Set("Content-Type", "image/png")
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

func handleWebSocket(hub *Hub) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		conn, err := websocket.Accept(w, r, &websocket.AcceptOptions{
			InsecureSkipVerify: true,
		})
		if err != nil {
			log.Printf("ws accept error: %v", err)
			return
		}

		ctx, cancel := context.WithCancel(r.Context())
		hub.Add(conn, cancel)
		defer hub.Remove(conn)

		log.Printf("ws: client connected from %s", r.RemoteAddr)

		if err := hub.SendFullState(ctx, conn); err != nil {
			log.Printf("ws: send full state error: %v", err)
			return
		}

		for {
			_, data, err := conn.Read(ctx)
			if err != nil {
				if websocket.CloseStatus(err) == websocket.StatusNormalClosure ||
					websocket.CloseStatus(err) == websocket.StatusGoingAway {
					log.Printf("ws: client disconnected normally")
				} else {
					log.Printf("ws: read error: %v", err)
				}
				return
			}

			var msg ClientMessage
			if err := json.Unmarshal(data, &msg); err != nil {
				continue
			}

			switch msg.Type {
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

func handleUpload(imagesDir, videosDir, musicDir string) http.HandlerFunc {
	imageExtsSet := map[string]bool{".jpg": true, ".jpeg": true, ".png": true, ".gif": true, ".webp": true, ".bmp": true}
	videoExtsSet := map[string]bool{".mp4": true, ".mkv": true, ".webm": true, ".mov": true, ".avi": true}
	musicExtsSet := map[string]bool{".mp3": true, ".flac": true, ".ogg": true, ".wav": true, ".m4a": true, ".aac": true}

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
