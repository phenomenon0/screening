package main

import (
	"context"
	"encoding/json"
	"fmt"
	"log"
	"sync"
	"time"

	"nhooyr.io/websocket"
)

type ClientInfo struct {
	Conn       *websocket.Conn
	Cancel     context.CancelFunc
	DeviceType string // "tv", "mobile", "scene_renderer", "dashboard"
	DeviceName string
	ID         string
}

type Hub struct {
	mu      sync.RWMutex
	clients map[*websocket.Conn]*ClientInfo
	nextID  int

	calendar *CalendarSource
	todos    *TodoSource
	images   *ImageSource
	videos   *VideoSource
	music    *MusicSource
	screen   *ScreenShare
	pomodoro *Pomodoro
	habits   *HabitStore
	weather  *WeatherSource
}

func NewHub(cal *CalendarSource, todos *TodoSource, imgs *ImageSource, vids *VideoSource, mus *MusicSource, scr *ScreenShare, pomo *Pomodoro, hab *HabitStore, wea *WeatherSource) *Hub {
	return &Hub{
		clients:  make(map[*websocket.Conn]*ClientInfo),
		calendar: cal, todos: todos, images: imgs, videos: vids,
		music: mus, screen: scr, pomodoro: pomo, habits: hab, weather: wea,
	}
}

func (h *Hub) Add(conn *websocket.Conn, cancel context.CancelFunc) string {
	h.mu.Lock()
	h.nextID++
	id := fmt.Sprintf("c%d", h.nextID)
	h.clients[conn] = &ClientInfo{Conn: conn, Cancel: cancel, ID: id, DeviceType: "unknown"}
	h.mu.Unlock()
	return id
}

func (h *Hub) Register(conn *websocket.Conn, deviceType, deviceName string) {
	h.mu.Lock()
	if ci, ok := h.clients[conn]; ok {
		ci.DeviceType = deviceType
		ci.DeviceName = deviceName
	}
	h.mu.Unlock()
	h.BroadcastDevices()
}

func (h *Hub) Remove(conn *websocket.Conn) {
	h.mu.Lock()
	if ci, ok := h.clients[conn]; ok {
		ci.Cancel()
		delete(h.clients, conn)
	}
	h.mu.Unlock()
	h.BroadcastDevices()
}

func (h *Hub) Devices() []DeviceInfo {
	h.mu.RLock()
	defer h.mu.RUnlock()
	var out []DeviceInfo
	for _, ci := range h.clients {
		if ci.DeviceType != "unknown" {
			out = append(out, DeviceInfo{
				ID:        ci.ID,
				Type:      ci.DeviceType,
				Name:      ci.DeviceName,
				Connected: true,
			})
		}
	}
	return out
}

func (h *Hub) SendFullState(ctx context.Context, conn *websocket.Conn) error {
	pomoState := h.pomodoro.State()
	msgs := []ServerMessage{
		{Type: "calendar_sync", Events: h.calendar.Events()},
		{Type: "todo_sync", Items: h.todos.Items()},
		{Type: "image_sync", Images: h.images.List()},
		{Type: "video_sync", Videos: h.videos.List()},
		{Type: "music_sync", Music: h.music.List()},
		{Type: "habit_sync", Habits: h.habits.List()},
		{Type: "pomodoro_sync", Pomodoro: &pomoState},
		{Type: "devices_sync", Devices: h.Devices()},
	}
	if h.weather != nil {
		w := h.weather.Current()
		if w.TempF != "" {
			msgs = append(msgs, ServerMessage{Type: "weather_sync", Weather: &w})
		}
	}
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
		return
	}
	h.mu.RLock()
	snapshot := make([]*ClientInfo, 0, len(h.clients))
	for _, ci := range h.clients {
		snapshot = append(snapshot, ci)
	}
	h.mu.RUnlock()
	for _, ci := range snapshot {
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		if err := ci.Conn.Write(ctx, websocket.MessageText, data); err != nil {
			cancel()
			h.Remove(ci.Conn)
			continue
		}
		cancel()
	}
}

// SendToType sends a message only to clients of a specific device type.
func (h *Hub) SendToType(deviceType string, msg ServerMessage) {
	data, err := json.Marshal(msg)
	if err != nil {
		return
	}
	h.mu.RLock()
	snapshot := make([]*ClientInfo, 0, len(h.clients))
	for _, ci := range h.clients {
		if ci.DeviceType == deviceType {
			snapshot = append(snapshot, ci)
		}
	}
	h.mu.RUnlock()
	for _, ci := range snapshot {
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		if err := ci.Conn.Write(ctx, websocket.MessageText, data); err != nil {
			cancel()
			h.Remove(ci.Conn)
			continue
		}
		cancel()
	}
}

// SendRawToType sends pre-serialized bytes to clients of a specific type.
func (h *Hub) SendRawToType(deviceType string, data []byte) {
	h.mu.RLock()
	snapshot := make([]*ClientInfo, 0, len(h.clients))
	for _, ci := range h.clients {
		if ci.DeviceType == deviceType {
			snapshot = append(snapshot, ci)
		}
	}
	h.mu.RUnlock()
	for _, ci := range snapshot {
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		if err := ci.Conn.Write(ctx, websocket.MessageText, data); err != nil {
			cancel()
			h.Remove(ci.Conn)
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
func (h *Hub) BroadcastHabits()   { h.Broadcast(ServerMessage{Type: "habit_sync", Habits: h.habits.List()}) }
func (h *Hub) BroadcastDevices()  { h.Broadcast(ServerMessage{Type: "devices_sync", Devices: h.Devices()}) }
func (h *Hub) BroadcastPomodoro() {
	s := h.pomodoro.State()
	h.Broadcast(ServerMessage{Type: "pomodoro_sync", Pomodoro: &s})
}
func (h *Hub) BroadcastWeather() {
	if h.weather != nil {
		w := h.weather.Current()
		if w.TempF != "" {
			h.Broadcast(ServerMessage{Type: "weather_sync", Weather: &w})
		}
	}
}

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
