package main

import (
	"fmt"
	"log"
	"os"
	"os/exec"
	"sync"
)

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

	// Detect display server
	sessionType := os.Getenv("XDG_SESSION_TYPE")
	listenAddr := fmt.Sprintf("http://0.0.0.0:%d", ss.port)

	var cmd *exec.Cmd
	if sessionType == "wayland" {
		// On Wayland: use PipeWire screencast portal via gst-launch or wl-screenrec
		// Try wl-screenrec first, fall back to GNOME screencast
		if _, err := exec.LookPath("wl-screenrec"); err == nil {
			cmd = exec.Command("wl-screenrec",
				"--codec", "h264",
				"--bitrate", "3000000",
				"-f", fmt.Sprintf("/tmp/screening-stream-%d.ts", ss.port),
			)
		} else {
			// Fall back to portal-based capture with ffmpeg + PipeWire
			// This requires the user to accept a portal dialog on first use
			cmd = exec.Command("bash", "-c", fmt.Sprintf(
				`dbus-send --session --type=method_call --print-reply --dest=org.gnome.Shell /org/gnome/Shell/Screencast org.gnome.Shell.Screencast.Screencast string:"/tmp/screening-screencast.webm" dict:string:string:"framerate","30","pipeline","vp8enc min_quantizer=13 max_quantizer=13 cpu-used=5 deadline=1000000 threads=4 ! queue ! webmmux" && ffmpeg -re -i /tmp/screening-screencast.webm -c:v libx264 -preset ultrafast -tune zerolatency -f mpegts -listen 1 %s`, listenAddr))
		}
	} else {
		// X11: standard approach
		cmd = exec.Command("ffmpeg",
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
	}

	ss.cmd = cmd
	if err := ss.cmd.Start(); err != nil {
		return "", fmt.Errorf("screen capture start: %w", err)
	}

	ss.active = true
	log.Printf("screen share: started (%s mode) on %s", sessionType, listenAddr)

	go func() {
		err := ss.cmd.Wait()
		ss.mu.Lock()
		ss.active = false
		ss.cmd = nil
		ss.mu.Unlock()
		if err != nil {
			log.Printf("screen share: process exited: %v", err)
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
