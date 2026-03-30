package main

import (
	"fmt"
	"io"
	"log"
	"os"
	"os/exec"
	"path/filepath"
	"sync"
)

// SceneStream collects JPEG frames from the browser renderer and pipes them
// through ffmpeg to produce HLS segments. ffmpeg starts lazily on first frame.
type SceneStream struct {
	mu          sync.Mutex
	ffmpeg      *exec.Cmd
	ffmpegStdin io.WriteCloser
	active      bool
	started     bool // ffmpeg process started
	hlsDir      string
	localIP     string
	serverPort  int
	frameCount  int
}

func NewSceneStream(localIP string, serverPort int) *SceneStream {
	hlsDir := filepath.Join(os.TempDir(), "screening-hls")
	os.MkdirAll(hlsDir, 0755)
	return &SceneStream{hlsDir: hlsDir, localIP: localIP, serverPort: serverPort}
}

// Enable marks the stream as active (accepting frames). ffmpeg starts on first frame.
func (ss *SceneStream) Enable() string {
	ss.mu.Lock()
	defer ss.mu.Unlock()
	ss.active = true
	log.Printf("scene stream: enabled, waiting for frames")
	return ss.StreamURL()
}

func (ss *SceneStream) startFFmpeg() error {
	// Clean old segments
	files, _ := filepath.Glob(filepath.Join(ss.hlsDir, "*"))
	for _, f := range files {
		os.Remove(f)
	}

	playlistPath := filepath.Join(ss.hlsDir, "stream.m3u8")

	ss.ffmpeg = exec.Command("ffmpeg",
		"-y",
		"-f", "image2pipe",
		"-framerate", "24",
		"-vcodec", "mjpeg",
		"-i", "pipe:0",
		"-vf", "scale=1920:1080:force_original_aspect_ratio=decrease,pad=1920:1080:(ow-iw)/2:(oh-ih)/2",
		"-vcodec", "libx264",
		"-preset", "ultrafast",
		"-tune", "zerolatency",
		"-b:v", "3000k",
		"-maxrate", "3000k",
		"-bufsize", "1500k",
		"-g", "24",
		"-pix_fmt", "yuv420p",
		"-f", "hls",
		"-hls_time", "1",
		"-hls_list_size", "5",
		"-hls_flags", "delete_segments+independent_segments",
		"-hls_segment_filename", filepath.Join(ss.hlsDir, "stream%d.ts"),
		playlistPath,
	)

	var err error
	ss.ffmpegStdin, err = ss.ffmpeg.StdinPipe()
	if err != nil {
		return fmt.Errorf("stdin pipe: %w", err)
	}

	ss.ffmpeg.Stderr = os.Stderr

	if err := ss.ffmpeg.Start(); err != nil {
		return fmt.Errorf("ffmpeg start: %w", err)
	}

	ss.started = true
	log.Printf("scene stream: ffmpeg started (pid %d)", ss.ffmpeg.Process.Pid)

	go func() {
		err := ss.ffmpeg.Wait()
		ss.mu.Lock()
		ss.started = false
		ss.ffmpegStdin = nil
		ss.mu.Unlock()
		if err != nil {
			log.Printf("scene stream: ffmpeg exited: %v", err)
		}
	}()

	return nil
}

// FeedFrame sends a JPEG frame. Starts ffmpeg on the first frame.
func (ss *SceneStream) FeedFrame(jpeg []byte) {
	ss.mu.Lock()
	defer ss.mu.Unlock()

	if !ss.active {
		return
	}

	// Start ffmpeg lazily on first frame
	if !ss.started {
		if err := ss.startFFmpeg(); err != nil {
			log.Printf("scene stream: failed to start ffmpeg: %v", err)
			ss.active = false // don't keep retrying
			return
		}
	}

	if ss.ffmpegStdin == nil {
		return
	}

	_, err := ss.ffmpegStdin.Write(jpeg)
	if err != nil {
		log.Printf("scene stream: write error, ffmpeg likely crashed")
		// Don't restart here — let the Wait() goroutine clean up
		// Next Enable() call will allow restart
		return
	}

	ss.frameCount++
	if ss.frameCount == 1 {
		log.Printf("scene stream: first frame received (%d bytes), pipeline active", len(jpeg))
		// Debug: save first frame to disk
		os.WriteFile("/tmp/screening-frame-0.jpg", jpeg, 0644)
		log.Printf("scene stream: saved debug frame to /tmp/screening-frame-0.jpg")
	}
	if ss.frameCount%100 == 0 {
		log.Printf("scene stream: %d frames piped to ffmpeg", ss.frameCount)
	}
}

func (ss *SceneStream) Stop() {
	ss.mu.Lock()
	defer ss.mu.Unlock()
	ss.active = false
	if ss.ffmpegStdin != nil {
		ss.ffmpegStdin.Close()
	}
	if ss.ffmpeg != nil && ss.ffmpeg.Process != nil {
		ss.ffmpeg.Process.Kill()
	}
	ss.started = false
	ss.ffmpegStdin = nil
	ss.frameCount = 0
	log.Printf("scene stream: stopped")
}

func (ss *SceneStream) IsActive() bool {
	ss.mu.Lock()
	defer ss.mu.Unlock()
	return ss.active && ss.started
}

func (ss *SceneStream) StreamURL() string {
	return fmt.Sprintf("http://%s:%d/scene/hls/stream.m3u8", ss.localIP, ss.serverPort)
}

func (ss *SceneStream) HLSDir() string {
	return ss.hlsDir
}
