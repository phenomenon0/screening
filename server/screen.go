package main

import (
	"fmt"
	"log"
	"os/exec"
	"sync"
)

// ScreenShare manages ffmpeg screen capture.
type ScreenShare struct {
	mu      sync.Mutex
	cmd     *exec.Cmd
	active  bool
	port    int
	localIP string
}

func NewScreenShare(localIP string) *ScreenShare {
	return &ScreenShare{port: 9901, localIP: localIP}
}

func (ss *ScreenShare) Start() (string, error) {
	ss.mu.Lock()
	defer ss.mu.Unlock()

	if ss.active {
		return ss.streamURL(), nil
	}

	// ffmpeg: capture X11 display, encode with ultrafast x264, stream as mpegts over HTTP
	listenAddr := fmt.Sprintf("http://0.0.0.0:%d", ss.port)
	ss.cmd = exec.Command("ffmpeg",
		"-f", "x11grab",
		"-framerate", "30",
		"-video_size", "1920x1080",
		"-i", ":0",
		"-vcodec", "libx264",
		"-preset", "ultrafast",
		"-tune", "zerolatency",
		"-b:v", "3000k",
		"-maxrate", "3000k",
		"-bufsize", "1500k",
		"-g", "30",
		"-f", "mpegts",
		"-listen", "1",
		listenAddr,
	)

	if err := ss.cmd.Start(); err != nil {
		return "", fmt.Errorf("ffmpeg start: %w", err)
	}

	ss.active = true
	log.Printf("screen share: started on %s", listenAddr)

	// Monitor process in background
	go func() {
		err := ss.cmd.Wait()
		ss.mu.Lock()
		ss.active = false
		ss.cmd = nil
		ss.mu.Unlock()
		if err != nil {
			log.Printf("screen share: ffmpeg exited: %v", err)
		}
	}()

	return ss.streamURL(), nil
}

func (ss *ScreenShare) Stop() {
	ss.mu.Lock()
	defer ss.mu.Unlock()

	if !ss.active || ss.cmd == nil || ss.cmd.Process == nil {
		return
	}

	ss.cmd.Process.Kill()
	ss.active = false
	ss.cmd = nil
	log.Printf("screen share: stopped")
}

func (ss *ScreenShare) IsActive() bool {
	ss.mu.Lock()
	defer ss.mu.Unlock()
	return ss.active
}

func (ss *ScreenShare) streamURL() string {
	return fmt.Sprintf("http://%s:%d", ss.localIP, ss.port)
}
