package main

import (
	"image"
	_ "image/gif"
	_ "image/jpeg"
	_ "image/png"
	"log"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"

	"github.com/fsnotify/fsnotify"
)

var imageExts = map[string]bool{
	".jpg": true, ".jpeg": true, ".png": true, ".gif": true, ".webp": true, ".bmp": true,
}

var videoExts = map[string]bool{
	".mp4": true, ".mkv": true, ".webm": true, ".mov": true, ".avi": true,
}

// ImageSource scans a directory for images and watches for changes.
type ImageSource struct {
	mu       sync.RWMutex
	images   []ImageInfo
	dir      string
	onChange func()
}

func NewImageSource(dir string, onChange func()) *ImageSource {
	is := &ImageSource{dir: dir, onChange: onChange}
	if dir != "" {
		os.MkdirAll(dir, 0755)
		is.scan()
	}
	return is
}

func (is *ImageSource) List() []ImageInfo {
	is.mu.RLock()
	defer is.mu.RUnlock()
	out := make([]ImageInfo, len(is.images))
	copy(out, is.images)
	return out
}

func (is *ImageSource) scan() {
	if is.dir == "" {
		return
	}
	entries, err := os.ReadDir(is.dir)
	if err != nil {
		log.Printf("images: readdir %s: %v", is.dir, err)
		return
	}

	var images []ImageInfo
	for _, e := range entries {
		if e.IsDir() {
			continue
		}
		ext := strings.ToLower(filepath.Ext(e.Name()))
		if !imageExts[ext] {
			continue
		}

		w, h := getImageDimensions(filepath.Join(is.dir, e.Name()))
		images = append(images, ImageInfo{
			Filename: e.Name(),
			Width:    w,
			Height:   h,
			URL:      "/images/" + url.PathEscape(e.Name()),
		})
	}

	is.mu.Lock()
	is.images = images
	is.mu.Unlock()
	log.Printf("images: found %d images in %s", len(images), is.dir)
}

func getImageDimensions(path string) (int, int) {
	f, err := os.Open(path)
	if err != nil {
		return 0, 0
	}
	defer f.Close()

	cfg, _, err := image.DecodeConfig(f)
	if err != nil {
		return 0, 0
	}
	return cfg.Width, cfg.Height
}

// FilePath returns the full path to an image file.
func (is *ImageSource) FilePath(filename string) string {
	// Sanitize to prevent path traversal
	clean := filepath.Base(filename)
	return filepath.Join(is.dir, clean)
}

// VideoSource scans a directory for video files.
type VideoSource struct {
	mu       sync.RWMutex
	videos   []VideoInfo
	dir      string
	onChange func()
}

func NewVideoSource(dir string, onChange func()) *VideoSource {
	vs := &VideoSource{dir: dir, onChange: onChange}
	if dir != "" {
		os.MkdirAll(dir, 0755)
		vs.scan()
	}
	return vs
}

func (vs *VideoSource) List() []VideoInfo {
	vs.mu.RLock()
	defer vs.mu.RUnlock()
	out := make([]VideoInfo, len(vs.videos))
	copy(out, vs.videos)
	return out
}

func (vs *VideoSource) scan() {
	if vs.dir == "" {
		return
	}
	entries, err := os.ReadDir(vs.dir)
	if err != nil {
		log.Printf("videos: readdir %s: %v", vs.dir, err)
		return
	}

	var videos []VideoInfo
	for _, e := range entries {
		if e.IsDir() {
			continue
		}
		ext := strings.ToLower(filepath.Ext(e.Name()))
		if !videoExts[ext] {
			continue
		}
		videos = append(videos, VideoInfo{
			Filename: e.Name(),
			URL:      "/videos/" + url.PathEscape(e.Name()),
		})
	}

	vs.mu.Lock()
	vs.videos = videos
	vs.mu.Unlock()
	log.Printf("videos: found %d videos in %s", len(videos), vs.dir)
}

func (vs *VideoSource) FilePath(filename string) string {
	return filepath.Join(vs.dir, filepath.Base(filename))
}

func (vs *VideoSource) Watch(done <-chan struct{}) {
	if vs.dir == "" {
		return
	}
	watcher, err := fsnotify.NewWatcher()
	if err != nil {
		log.Printf("videos watcher error: %v", err)
		return
	}
	defer watcher.Close()
	if err := watcher.Add(vs.dir); err != nil {
		log.Printf("videos watch add error: %v", err)
		return
	}
	log.Printf("videos: watching %s", vs.dir)
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
					vs.scan()
					if vs.onChange != nil {
						vs.onChange()
					}
				})
			}
		case err, ok := <-watcher.Errors:
			if !ok {
				return
			}
			log.Printf("videos watcher error: %v", err)
		}
	}
}

// Watch watches the image directory for changes.
func (is *ImageSource) Watch(done <-chan struct{}) {
	if is.dir == "" {
		return
	}
	watcher, err := fsnotify.NewWatcher()
	if err != nil {
		log.Printf("images watcher error: %v", err)
		return
	}
	defer watcher.Close()

	if err := watcher.Add(is.dir); err != nil {
		log.Printf("images watch add error: %v", err)
		return
	}
	log.Printf("images: watching %s", is.dir)

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
					is.scan()
					if is.onChange != nil {
						is.onChange()
					}
				})
			}
		case err, ok := <-watcher.Errors:
			if !ok {
				return
			}
			log.Printf("images watcher error: %v", err)
		}
	}
}
