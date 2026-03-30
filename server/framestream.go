package main

import (
	"fmt"
	"net/http"
	"sync"
)

// FrameStream receives JPEG frames from the renderer and serves them as MJPEG to viewers.
type FrameStream struct {
	mu       sync.RWMutex
	frame    []byte
	viewers  map[chan []byte]bool
	hasFrame bool
}

func NewFrameStream() *FrameStream {
	return &FrameStream{
		viewers: make(map[chan []byte]bool),
	}
}

// LatestFrame returns the most recent frame as a single JPEG.
func (fs *FrameStream) LatestFrame(w http.ResponseWriter, r *http.Request) {
	fs.mu.RLock()
	frame := fs.frame
	has := fs.hasFrame
	fs.mu.RUnlock()
	if !has || len(frame) == 0 {
		http.Error(w, "no frame", http.StatusServiceUnavailable)
		return
	}
	w.Header().Set("Content-Type", "image/jpeg")
	w.Header().Set("Cache-Control", "no-cache")
	w.Write(frame)
}

// PushFrame stores a new frame and notifies all viewers.
func (fs *FrameStream) PushFrame(jpeg []byte) {
	fs.mu.Lock()
	fs.frame = jpeg
	fs.hasFrame = true
	// Non-blocking send to all viewers
	for ch := range fs.viewers {
		select {
		case ch <- jpeg:
		default:
			// Viewer is slow, skip this frame
		}
	}
	fs.mu.Unlock()
}

// ServeHTTP serves an MJPEG stream.
func (fs *FrameStream) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	flusher, ok := w.(http.Flusher)
	if !ok {
		http.Error(w, "streaming not supported", http.StatusInternalServerError)
		return
	}

	ch := make(chan []byte, 2)
	fs.mu.Lock()
	fs.viewers[ch] = true
	fs.mu.Unlock()

	defer func() {
		fs.mu.Lock()
		delete(fs.viewers, ch)
		fs.mu.Unlock()
	}()

	w.Header().Set("Content-Type", "multipart/x-mixed-replace; boundary=frame")
	w.Header().Set("Cache-Control", "no-cache")
	w.Header().Set("Connection", "keep-alive")

	for {
		select {
		case frame := <-ch:
			_, err := fmt.Fprintf(w, "--frame\r\nContent-Type: image/jpeg\r\nContent-Length: %d\r\n\r\n", len(frame))
			if err != nil {
				return
			}
			_, err = w.Write(frame)
			if err != nil {
				return
			}
			_, err = fmt.Fprint(w, "\r\n")
			if err != nil {
				return
			}
			flusher.Flush()
		case <-r.Context().Done():
			return
		}
	}
}
