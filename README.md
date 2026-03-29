# Screening

A digital dashboard for Android TV. Rotates through your calendar, to-do list, photo gallery, and videos on your TV — powered by a lightweight Go server running on your computer.

## How it works

```
[Your Computer]                 [Android TV]
  screening-server ── WebSocket ──► Screening app
  :9900                             Compose for TV
  │
  ├── Calendar (ICS file)
  ├── To-do list (JSON file)
  ├── Photo gallery (image folder)
  ├── Video player (video folder)
  └── Background music (music folder)
```

The server watches your local files and pushes updates to the TV app in real-time over your home network. Drop a photo into the folder — it appears on your TV within seconds.

## Quick Start

### 1. Install the server

Download the latest release for your platform from [Releases](https://github.com/phenomenon0/screening/releases), or build from source:

```bash
cd server
go build -o screening-server .
./screening-server
```

The server creates default directories and starts on port 9900.

### 2. Install the TV app

Download the APK from [Releases](https://github.com/phenomenon0/screening/releases) and sideload:

```bash
adb connect <your-tv-ip>:5555
adb install screening-v1.0.0.apk
```

Or install from the Google Play Store / Amazon Appstore (coming soon).

The app auto-discovers the server on your network via mDNS. No configuration needed.

### 3. Add your content

| Content | Where to put it |
|---------|----------------|
| Photos | `~/Pictures/Dashboard/` |
| Videos | `~/Videos/Dashboard/` |
| Music | `~/Music/Dashboard/` |
| Calendar | Set `ics_path` in `config.json` |
| To-dos | Edit `~/.local/share/screening/todos.json` |

## Configuration

Edit `server/config.json`:

```json
{
  "port": 9900,
  "ics_path": "",
  "images_dir": "~/Pictures/Dashboard",
  "videos_dir": "~/Videos/Dashboard",
  "music_dir": "~/Music/Dashboard",
  "todos_file": "~/.local/share/screening/todos.json"
}
```

## Frame Rotation

The dashboard cycles through:
- **Photos** — 8 minutes (landscape fills screen, portrait on black)
- **To-do** — 2 minutes
- **Calendar** — 1 minute
- **Video** — manual only (navigate with remote)

Use the D-pad left/right on your remote to switch frames manually.

## Run as a service (Linux)

```bash
# Copy binary
cp screening-server ~/.local/bin/

# Copy systemd service
cp screening-server.service ~/.config/systemd/user/

# Enable and start
systemctl --user daemon-reload
systemctl --user enable --now screening-server

# Auto-start before login
loginctl enable-linger $USER
```

## Features

- Auto-discovery via mDNS (no IP configuration)
- WebSocket for real-time updates
- File watching — changes appear instantly
- Background music playback
- Screen sharing from PC (via ffmpeg)
- Keep-screen-on for always-on display
- Reconnects automatically if server restarts

## Building from source

**Server:**
```bash
cd server && go build -o screening-server .
```

**Android TV app:**
```bash
cd app && ./gradlew assembleDebug
```

## License

MIT
