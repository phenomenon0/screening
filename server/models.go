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
	URL    string       `json:"url,omitempty"` // for screen_share_active
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

type ClientMessage struct {
	Type string `json:"type"`
	ID   string `json:"id,omitempty"`
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
