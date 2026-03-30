package main

import (
	"fmt"
	"io"
	"log"
	"net/http"
	"os/exec"
	"sync"
)

// SceneStream pipes JPEG frames through ffmpeg to produce a continuous
// MPEGTS stream served over HTTP. ExoPlayer plays it as a progressive stream.
type SceneStream struct {
	mu          sync.Mutex
	ffmpeg      *exec.Cmd
	ffmpegStdin io.WriteCloser
	active      bool
	started     bool
	failCount   int
	localIP     string
	serverPort  int
	frameCount  int

	// Ring buffer of recent encoded output for new viewers
	outMu   sync.RWMutex
	viewers map[chan []byte]bool
}

func NewSceneStream(localIP string, serverPort int) *SceneStream {
	return &SceneStream{
		localIP:    localIP,
		serverPort: serverPort,
		viewers:    make(map[chan []byte]bool),
	}
}

func (ss *SceneStream) Enable() string {
	ss.mu.Lock()
	defer ss.mu.Unlock()
	ss.active = true
	ss.failCount = 0
	log.Printf("scene stream: enabled")
	return ss.StreamURL()
}

func (ss *SceneStream) startFFmpeg() error {
	// ffmpeg: JPEG stdin → H.264 MPEGTS stdout
	ss.ffmpeg = exec.Command("ffmpeg",
		"-y",
		"-f", "image2pipe",
		"-framerate", "30",
		"-vcodec", "mjpeg",
		"-i", "pipe:0",
		"-vf", "scale=1280:720,setsar=1:1",
		"-vcodec", "libx264",
		"-preset", "ultrafast",
		"-tune", "zerolatency",
		"-profile:v", "baseline",
		"-level", "3.1",
		"-b:v", "2000k",
		"-maxrate", "2000k",
		"-bufsize", "500k",
		"-g", "10",
		"-keyint_min", "10",
		"-sc_threshold", "0",
		"-flags", "+low_delay",
		"-fflags", "+nobuffer",
		"-pix_fmt", "yuv420p",
		"-color_range", "tv",
		"-f", "mpegts",
		"-mpegts_flags", "resend_headers",
		"pipe:1",
	)

	var err error
	ss.ffmpegStdin, err = ss.ffmpeg.StdinPipe()
	if err != nil {
		return fmt.Errorf("stdin pipe: %w", err)
	}

	stdout, err := ss.ffmpeg.StdoutPipe()
	if err != nil {
		return fmt.Errorf("stdout pipe: %w", err)
	}

	if err := ss.ffmpeg.Start(); err != nil {
		return fmt.Errorf("ffmpeg start: %w", err)
	}

	ss.started = true
	log.Printf("scene stream: ffmpeg started (pid %d), output to viewers", ss.ffmpeg.Process.Pid)

	// Read ffmpeg output and distribute to viewers
	go func() {
		buf := make([]byte, 65536)
		for {
			n, err := stdout.Read(buf)
			if err != nil {
				break
			}
			if n > 0 {
				chunk := make([]byte, n)
				copy(chunk, buf[:n])

				ss.outMu.RLock()
				for ch := range ss.viewers {
					select {
					case ch <- chunk:
					default: // viewer too slow, skip
					}
				}
				ss.outMu.RUnlock()
			}
		}
	}()

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

func (ss *SceneStream) FeedFrame(jpeg []byte) {
	ss.mu.Lock()
	defer ss.mu.Unlock()

	if !ss.active {
		return
	}

	if !ss.started {
		if ss.failCount >= 3 {
			return
		}
		if err := ss.startFFmpeg(); err != nil {
			ss.failCount++
			log.Printf("scene stream: start attempt %d/3 failed: %v", ss.failCount, err)
			return
		}
		ss.failCount = 0
	}

	if ss.ffmpegStdin == nil {
		return
	}

	_, err := ss.ffmpegStdin.Write(jpeg)
	if err != nil {
		log.Printf("scene stream: write error")
		return
	}

	ss.frameCount++
	if ss.frameCount == 1 {
		log.Printf("scene stream: first frame (%d bytes)", len(jpeg))
	}
	if ss.frameCount%200 == 0 {
		log.Printf("scene stream: %d frames piped", ss.frameCount)
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
}

func (ss *SceneStream) IsActive() bool {
	ss.mu.Lock()
	defer ss.mu.Unlock()
	return ss.active && ss.started
}

func (ss *SceneStream) StreamURL() string {
	return fmt.Sprintf("http://%s:%d/scene/live", ss.localIP, ss.serverPort)
}

// ServeHTTP streams live MPEGTS to the requesting client
func (ss *SceneStream) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	flusher, ok := w.(http.Flusher)
	if !ok {
		http.Error(w, "streaming not supported", http.StatusInternalServerError)
		return
	}

	ch := make(chan []byte, 5) // small buffer for low latency
	ss.outMu.Lock()
	ss.viewers[ch] = true
	ss.outMu.Unlock()

	defer func() {
		ss.outMu.Lock()
		delete(ss.viewers, ch)
		ss.outMu.Unlock()
	}()

	w.Header().Set("Content-Type", "video/mp2t")
	w.Header().Set("Cache-Control", "no-cache")
	w.Header().Set("Connection", "keep-alive")
	w.Header().Set("Transfer-Encoding", "chunked")

	log.Printf("scene stream: viewer connected from %s", r.RemoteAddr)

	for {
		select {
		case chunk := <-ch:
			_, err := w.Write(chunk)
			if err != nil {
				return
			}
			flusher.Flush()
		case <-r.Context().Done():
			return
		}
	}
}
