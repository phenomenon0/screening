package main

import (
	"context"
	"encoding/json"
	"flag"
	"fmt"
	"log"
	"net"
	"net/http"
	"os"
	"os/signal"
	"path/filepath"
	"strings"
	"syscall"

	"github.com/grandcat/zeroconf"
)

var (
	version   = "dev"
	commit    = "none"
	buildDate = "unknown"
)

func main() {
	configPath := flag.String("config", "config.json", "path to config file")
	showVersion := flag.Bool("version", false, "print version and exit")
	flag.Parse()

	if *showVersion {
		fmt.Printf("screening-server %s (commit %s, built %s)\n", version, commit, buildDate)
		return
	}

	cfg := Config{
		Port:      9900,
		ImagesDir: os.ExpandEnv("$HOME/Pictures/Dashboard"),
		VideosDir: os.ExpandEnv("$HOME/Videos/Dashboard"),
		MusicDir:  os.ExpandEnv("$HOME/Music/Dashboard"),
		TodosFile: os.ExpandEnv("$HOME/.local/share/screening/todos.json"),
	}

	if data, err := os.ReadFile(*configPath); err == nil {
		if err := json.Unmarshal(data, &cfg); err != nil {
			log.Fatalf("config parse error: %v", err)
		}
		log.Printf("loaded config from %s", *configPath)
	} else {
		log.Printf("no config file, using defaults (port %d)", cfg.Port)
	}

	cfg.ICSPath = expandHome(cfg.ICSPath)
	cfg.ImagesDir = expandHome(cfg.ImagesDir)
	cfg.VideosDir = expandHome(cfg.VideosDir)
	cfg.MusicDir = expandHome(cfg.MusicDir)
	cfg.TodosFile = expandHome(cfg.TodosFile)

	if cfg.TodosFile != "" {
		os.MkdirAll(filepath.Dir(cfg.TodosFile), 0755)
	}

	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()
	done := ctx.Done()

	localIP := getLocalIP()
	var hub *Hub

	dataDir := filepath.Dir(cfg.TodosFile)
	scenesDir := filepath.Join(dataDir, "scenes")
	assetsDir := filepath.Join(dataDir, "assets")
	os.MkdirAll(assetsDir, 0755)

	calendar := NewCalendarSource(cfg.ICSPath, func() { hub.BroadcastCalendar() })
	todos := NewTodoSource(cfg.TodosFile, func() { hub.BroadcastTodos() })
	images := NewImageSource(cfg.ImagesDir, func() { hub.BroadcastImages() })
	videos := NewVideoSource(cfg.VideosDir, func() { hub.BroadcastVideos() })
	music := NewMusicSource(cfg.MusicDir, func() { hub.BroadcastMusic() })
	screen := NewScreenShare(localIP)
	pomodoro := NewPomodoro(func() { hub.BroadcastPomodoro() })
	habits := NewHabitStore(dataDir, func() { hub.BroadcastHabits() })

	scenes := NewSceneManager(scenesDir)
	scenes.CreateDefault()

	weather := NewWeatherSource(func() { hub.BroadcastWeather() })

	hub = NewHub(calendar, todos, images, videos, music, screen, pomodoro, habits, weather)

	// Wire alarm callback — broadcast to all clients
	calendar.onAlarm = func(a AlarmInfo) {
		hub.Broadcast(ServerMessage{
			Type:    "alarm",
			URL:     a.EventTitle,
			SceneID: a.EventStart.Format("15:04"),
		})
	}

	go calendar.Watch(done)
	go calendar.StartAlarmChecker(done)
	go weather.StartPolling(done)
	go todos.Watch(done)
	go images.Watch(done)
	go videos.Watch(done)
	go music.Watch(done)
	go hub.StartPing(ctx)

	mux := http.NewServeMux()
	mux.HandleFunc("/health", handleHealth)
	mux.HandleFunc("/qr.png", handleQRCode(localIP, cfg.Port))
	frameStream := NewFrameStream()
	sceneStream := NewSceneStream(localIP, cfg.Port)
	mux.HandleFunc("/ws", handleWebSocket(hub, frameStream, sceneStream))
	mux.Handle("/scene/live", sceneStream)
	mux.Handle("/scene/stream", frameStream)
	mux.HandleFunc("/scene/frame", frameStream.LatestFrame)
	mux.HandleFunc("/upload", handleUpload(cfg.ImagesDir, cfg.VideosDir, cfg.MusicDir, assetsDir))
	mux.HandleFunc("/images/", handleFileServe(cfg.ImagesDir))
	mux.HandleFunc("/videos/", handleFileServe(cfg.VideosDir))
	mux.HandleFunc("/music/", handleFileServe(cfg.MusicDir))
	mux.HandleFunc("/assets/", handleFileServe(assetsDir))
	mux.HandleFunc("/scenes/", handleSceneList(scenes))
	mux.Handle("/scene/", http.StripPrefix("/scene/", handleSceneUI()))
	mux.Handle("/", handleWebUI())

	addr := fmt.Sprintf(":%d", cfg.Port)
	server := &http.Server{Addr: addr, Handler: mux}

	go func() {
		<-ctx.Done()
		screen.Stop()
		log.Println("shutting down server...")
		server.Close()
	}()

	// Register mDNS service so the TV can find us
	mdns, err := zeroconf.Register("Screening", "_screening._tcp", "local.", cfg.Port, nil, nil)
	if err != nil {
		log.Printf("mDNS registration failed: %v (discovery won't work, but direct IP still works)", err)
	} else {
		defer mdns.Shutdown()
		log.Printf("mDNS: registered _screening._tcp on port %d", cfg.Port)
	}

	log.Printf("screening server listening on %s (LAN IP: %s)", addr, localIP)
	log.Printf("  calendar: %s", cfg.ICSPath)
	log.Printf("  todos:    %s", cfg.TodosFile)
	log.Printf("  images:   %s", cfg.ImagesDir)
	log.Printf("  videos:   %s", cfg.VideosDir)
	log.Printf("  music:    %s", cfg.MusicDir)

	if err := server.ListenAndServe(); err != http.ErrServerClosed {
		log.Fatalf("server error: %v", err)
	}
}

func expandHome(path string) string {
	if strings.HasPrefix(path, "~/") {
		home, _ := os.UserHomeDir()
		return filepath.Join(home, path[2:])
	}
	return path
}

func getLocalIP() string {
	addrs, err := net.InterfaceAddrs()
	if err != nil {
		return "127.0.0.1"
	}
	for _, addr := range addrs {
		if ipnet, ok := addr.(*net.IPNet); ok && !ipnet.IP.IsLoopback() && ipnet.IP.To4() != nil {
			return ipnet.IP.String()
		}
	}
	return "127.0.0.1"
}
