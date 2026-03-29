package main

import (
	"context"
	"encoding/json"
	"log"
	"sync"
	"time"

	"nhooyr.io/websocket"
)

type Hub struct {
	mu      sync.RWMutex
	clients map[*websocket.Conn]context.CancelFunc

	calendar *CalendarSource
	todos    *TodoSource
	images   *ImageSource
	videos   *VideoSource
	music    *MusicSource
	screen   *ScreenShare
}

func NewHub(cal *CalendarSource, todos *TodoSource, imgs *ImageSource, vids *VideoSource, mus *MusicSource, scr *ScreenShare) *Hub {
	return &Hub{
		clients:  make(map[*websocket.Conn]context.CancelFunc),
		calendar: cal,
		todos:    todos,
		images:   imgs,
		videos:   vids,
		music:    mus,
		screen:   scr,
	}
}

func (h *Hub) Add(conn *websocket.Conn, cancel context.CancelFunc) {
	h.mu.Lock()
	h.clients[conn] = cancel
	h.mu.Unlock()
}

func (h *Hub) Remove(conn *websocket.Conn) {
	h.mu.Lock()
	if cancel, ok := h.clients[conn]; ok {
		cancel()
		delete(h.clients, conn)
	}
	h.mu.Unlock()
}

func (h *Hub) SendFullState(ctx context.Context, conn *websocket.Conn) error {
	msgs := []ServerMessage{
		{Type: "calendar_sync", Events: h.calendar.Events()},
		{Type: "todo_sync", Items: h.todos.Items()},
		{Type: "image_sync", Images: h.images.List()},
		{Type: "video_sync", Videos: h.videos.List()},
		{Type: "music_sync", Music: h.music.List()},
	}
	// If screen share is active, tell the new client
	if h.screen.IsActive() {
		msgs = append(msgs, ServerMessage{Type: "screen_share_active", URL: h.screen.streamURL()})
	}
	for _, msg := range msgs {
		data, err := json.Marshal(msg)
		if err != nil {
			return err
		}
		if err := conn.Write(ctx, websocket.MessageText, data); err != nil {
			return err
		}
	}
	return nil
}

func (h *Hub) Broadcast(msg ServerMessage) {
	data, err := json.Marshal(msg)
	if err != nil {
		log.Printf("broadcast marshal error: %v", err)
		return
	}
	h.mu.RLock()
	defer h.mu.RUnlock()
	for conn := range h.clients {
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		if err := conn.Write(ctx, websocket.MessageText, data); err != nil {
			log.Printf("broadcast write error: %v", err)
			cancel()
			go h.Remove(conn)
			continue
		}
		cancel()
	}
}

func (h *Hub) BroadcastCalendar() { h.Broadcast(ServerMessage{Type: "calendar_sync", Events: h.calendar.Events()}) }
func (h *Hub) BroadcastTodos()    { h.Broadcast(ServerMessage{Type: "todo_sync", Items: h.todos.Items()}) }
func (h *Hub) BroadcastImages()   { h.Broadcast(ServerMessage{Type: "image_sync", Images: h.images.List()}) }
func (h *Hub) BroadcastVideos()   { h.Broadcast(ServerMessage{Type: "video_sync", Videos: h.videos.List()}) }
func (h *Hub) BroadcastMusic()    { h.Broadcast(ServerMessage{Type: "music_sync", Music: h.music.List()}) }

func (h *Hub) HandleScreenShare(start bool) {
	if start {
		url, err := h.screen.Start()
		if err != nil {
			log.Printf("screen share start error: %v", err)
			return
		}
		h.Broadcast(ServerMessage{Type: "screen_share_active", URL: url})
	} else {
		h.screen.Stop()
		h.Broadcast(ServerMessage{Type: "screen_share_stop"})
	}
}

func (h *Hub) StartPing(ctx context.Context) {
	ticker := time.NewTicker(15 * time.Second)
	defer ticker.Stop()
	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			h.Broadcast(ServerMessage{Type: "ping"})
		}
	}
}
