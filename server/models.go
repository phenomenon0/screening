package main

import "time"

// ServerMessage is the envelope for all WebSocket messages.
type ServerMessage struct {
	Type   string       `json:"type"`
	Events []Event      `json:"events,omitempty"`
	Items  []TodoItem   `json:"items,omitempty"`
	Images []ImageInfo  `json:"images,omitempty"`
	Videos []VideoInfo  `json:"videos,omitempty"`
	Music  []MusicTrack `json:"music,omitempty"`
	URL      string         `json:"url,omitempty"`
	Habits   []Habit        `json:"habits,omitempty"`
	Pomodoro *PomodoroState `json:"pomodoro,omitempty"`
	Devices  []DeviceInfo   `json:"devices,omitempty"`
	Frame    int            `json:"frame,omitempty"`
	SceneID  string         `json:"scene_id,omitempty"`
	Weather  *WeatherInfo   `json:"weather,omitempty"`
	Present  *Presentation  `json:"presentation,omitempty"`
}

type Event struct {
	ID    string `json:"id"`
	Title string `json:"title"`
	Start string `json:"start"` // ISO 8601
	End   string `json:"end"`
	AllDay bool  `json:"allDay"`
}

type TodoItem struct {
	ID       string `json:"id"`
	Text     string `json:"text"`
	Done     bool   `json:"done"`
	Priority int    `json:"priority"`
}

type ImageInfo struct {
	Filename string `json:"filename"`
	Width    int    `json:"width"`
	Height   int    `json:"height"`
	URL      string `json:"url"`
}

type DeviceInfo struct {
	ID        string `json:"id"`
	Type      string `json:"type"`
	Name      string `json:"name"`
	Connected bool   `json:"connected"`
}

type ClientMessage struct {
	Type       string `json:"type"`
	ID         string `json:"id,omitempty"`
	Text       string `json:"text,omitempty"`
	Name       string `json:"name,omitempty"`
	Priority   int    `json:"priority,omitempty"`
	Minutes    int    `json:"minutes,omitempty"`
	Frame      int    `json:"frame,omitempty"`
	DeviceType string `json:"device_type,omitempty"`
	DeviceName string `json:"device_name,omitempty"`
	SceneID    string `json:"scene_id,omitempty"`
}

type VideoInfo struct {
	Filename string `json:"filename"`
	URL      string `json:"url"`
}

type MusicTrack struct {
	Filename string `json:"filename"`
	URL      string `json:"url"`
}

type Config struct {
	Port       int    `json:"port"`
	ICSPath    string `json:"ics_path"`
	ImagesDir  string `json:"images_dir"`
	VideosDir  string `json:"videos_dir"`
	MusicDir   string `json:"music_dir"`
	TodosFile  string `json:"todos_file"`
}

// ParsedEvent is the internal representation from ICS parsing.
type ParsedEvent struct {
	UID     string
	Summary string
	Start   time.Time
	End     time.Time
	AllDay  bool
}
