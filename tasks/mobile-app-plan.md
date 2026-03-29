# Mobile App Plan

## Architecture: Network-Aware Multi-Device System

```
                    ┌──────────────────┐
                    │   Go Server      │
                    │   :9900          │
                    │                  │
                    │ WebSocket Hub    │
                    │ ├── TV clients   │
                    │ ├── Phone clients│
                    │ └── Web clients  │
                    │                  │
                    │ Device Registry  │
                    │ ├── TV-1 (Fire)  │
                    │ ├── Phone-1      │
                    │ └── Web-1        │
                    └────────┬─────────┘
                             │ WebSocket
              ┌──────────────┼──────────────┐
              │              │              │
         ┌────┴────┐   ┌────┴────┐   ┌────┴────┐
         │   TV    │   │  Phone  │   │   Web   │
         │  (view) │   │(control)│   │(control)│
         └─────────┘   └─────────┘   └─────────┘
```

## Key Insight: Phones are controllers, TVs are displays

- TV app = passive display (shows frames, responds to commands)
- Phone app = active controller (swipe frames, manage content, remote control)
- Both connect to same server, same WebSocket, same state
- Server tracks device type and routes commands accordingly

## Project Structure

```
Screening/
├── app/                    # TV app (existing)
│   └── app/
├── mobile/                 # Phone app (new)
│   ├── build.gradle.kts
│   └── app/
│       └── src/main/java/com/screening/mobile/
│           ├── MobileApp.kt
│           ├── MainActivity.kt
│           ├── ui/
│           │   ├── HomeScreen.kt        # Main hub: swipe between tabs
│           │   ├── RemoteScreen.kt      # TV remote control
│           │   ├── GalleryScreen.kt     # Browse + upload images
│           │   ├── TodoScreen.kt        # Manage todos
│           │   ├── HabitScreen.kt       # Track habits with haptics
│           │   ├── PomodoroScreen.kt    # Timer with phone vibration
│           │   ├── MusicScreen.kt       # Control music playback
│           │   └── SetupScreen.kt       # QR scan or manual IP
│           ├── data/                    # SHARED with TV app
│           │   ├── WebSocketClient.kt   # Same file
│           │   ├── DashboardRepository.kt
│           │   └── ServerDiscovery.kt
│           └── model/                   # SHARED with TV app
│               └── ServerMessage.kt
├── shared/                 # Extract shared code here
│   └── src/main/java/com/screening/shared/
│       ├── WebSocketClient.kt
│       ├── DashboardRepository.kt
│       ├── ServerDiscovery.kt
│       └── model/ServerMessage.kt
└── server/
```

## Server Changes

### Device Registration
When a client connects, it sends:
```json
{"type": "register", "device_type": "phone", "device_name": "Femi's iPhone"}
```

Server tracks connected devices and broadcasts device list:
```json
{"type": "devices_sync", "devices": [
  {"id": "abc", "type": "tv", "name": "Living Room TV", "connected": true},
  {"id": "def", "type": "phone", "name": "Femi's Phone", "connected": true}
]}
```

### Targeted Commands
Phone sends commands targeting a specific TV:
```json
{"type": "frame_change", "target": "abc", "frame": 2}
```

Server routes to that specific TV instead of broadcasting to all.

## Phone App Features

### 1. Setup (first launch)
- Camera QR scanner → reads server URL from TV's QR code
- Falls back to mDNS discovery
- Falls back to manual IP entry

### 2. Remote Control (main screen)
- Large swipe area: swipe left/right to change TV frame
- Play/pause button for video/music
- Volume control (if TV supports it via CEC)

### 3. Quick Actions
- Add todo with phone keyboard (much easier than TV)
- Complete habit with one tap + haptic feedback
- Start/stop pomodoro
- Upload photo directly from phone camera

### 4. Gallery Browser
- Browse all images on server
- Upload from phone gallery or camera
- Delete images

### 5. Now Playing
- Shows current music track
- Skip/previous/shuffle controls

## Shared Module (`shared/`)
Extract from both TV and mobile:
- `WebSocketClient.kt`
- `DashboardRepository.kt`
- `ServerDiscovery.kt`
- All model classes (`ServerMessage.kt`)
- Theme colors (Kotlin constants, not Compose-specific)

Both `app/` and `mobile/` depend on `shared/`.

## Implementation Order
1. Create `shared/` module, move common code
2. Create `mobile/` module with basic Compose scaffold
3. QR scanner for setup (CameraX + ML Kit barcode)
4. Remote control screen (swipe + buttons)
5. Todo management
6. Habit tracking with haptics
7. Gallery browser + upload
8. Music controls
9. Server device registration + targeted commands
