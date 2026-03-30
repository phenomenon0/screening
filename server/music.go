package main

import (
	"log"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"

	"github.com/fsnotify/fsnotify"
)

var musicExts = map[string]bool{
	".mp3": true, ".flac": true, ".ogg": true, ".wav": true, ".m4a": true, ".aac": true,
}

type MusicSource struct {
	mu       sync.RWMutex
	tracks   []MusicTrack
	dir      string
	onChange func()
}

func NewMusicSource(dir string, onChange func()) *MusicSource {
	ms := &MusicSource{dir: dir, onChange: onChange}
	if dir != "" {
		os.MkdirAll(dir, 0755)
		ms.scan()
	}
	return ms
}

func (ms *MusicSource) List() []MusicTrack {
	ms.mu.RLock()
	defer ms.mu.RUnlock()
	out := make([]MusicTrack, len(ms.tracks))
	copy(out, ms.tracks)
	return out
}

func (ms *MusicSource) scan() {
	if ms.dir == "" {
		return
	}
	entries, err := os.ReadDir(ms.dir)
	if err != nil {
		log.Printf("music: readdir %s: %v", ms.dir, err)
		return
	}

	var tracks []MusicTrack
	for _, e := range entries {
		if e.IsDir() {
			continue
		}
		ext := strings.ToLower(filepath.Ext(e.Name()))
		if !musicExts[ext] {
			continue
		}
		tracks = append(tracks, MusicTrack{
			Filename: e.Name(),
			URL:      "/music/" + url.PathEscape(e.Name()),
		})
	}

	ms.mu.Lock()
	ms.tracks = tracks
	ms.mu.Unlock()
	log.Printf("music: found %d tracks in %s", len(tracks), ms.dir)
}

func (ms *MusicSource) FilePath(filename string) string {
	return filepath.Join(ms.dir, filepath.Base(filename))
}

func (ms *MusicSource) Watch(done <-chan struct{}) {
	if ms.dir == "" {
		return
	}
	watcher, err := fsnotify.NewWatcher()
	if err != nil {
		log.Printf("music watcher error: %v", err)
		return
	}
	defer watcher.Close()
	if err := watcher.Add(ms.dir); err != nil {
		log.Printf("music watch add error: %v", err)
		return
	}
	log.Printf("music: watching %s", ms.dir)
	var debounce *time.Timer
	for {
		select {
		case <-done:
			if debounce != nil {
				debounce.Stop()
			}
			return
		case event, ok := <-watcher.Events:
			if !ok {
				return
			}
			if event.Has(fsnotify.Create) || event.Has(fsnotify.Remove) || event.Has(fsnotify.Rename) {
				if debounce != nil {
					debounce.Stop()
				}
				debounce = time.AfterFunc(300*time.Millisecond, func() {
					ms.scan()
					if ms.onChange != nil {
						ms.onChange()
					}
				})
			}
		case err, ok := <-watcher.Errors:
			if !ok {
				return
			}
			log.Printf("music watcher error: %v", err)
		}
	}
}
