package main

import (
	"context"
	"encoding/json"
	"io"
	"log"
	"net/http"
	"strings"

	"nhooyr.io/websocket"
)

func handleHealth(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	w.Write([]byte(`{"ok":true}`))
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
				log.Printf("ws: unmarshal error: %v", err)
				continue
			}

			switch msg.Type {
			case "pong":
				// heartbeat
			case "todo_toggle":
				if msg.ID != "" {
					if hub.todos.Toggle(msg.ID) {
						hub.BroadcastTodos()
					}
				}
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
		// Extract filename after the prefix (e.g., /images/foo.jpg → foo.jpg)
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
