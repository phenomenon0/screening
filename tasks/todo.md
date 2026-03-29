# Screening v2.0 Feature Sprint

## Features (ordered by dependency — web companion first, everything else builds on it)

### Phase 1: Web Companion + Photo Drops (foundation for everything)
- [ ] Server: embed a web UI on `:9900/` (Go `embed` + vanilla HTML/JS)
- [ ] Web UI: manage todos (add/edit/delete/reorder)
- [ ] Web UI: upload images (drag & drop → saves to images dir)
- [ ] Web UI: upload videos, music
- [ ] Web UI: configure settings (frame durations, server paths)
- [ ] Shared photo drops: same upload UI, no auth needed on LAN
- [ ] QR code on TV status bar linking to `http://<server-ip>:9900/`

### Phase 2: Clock Mode + Pomodoro
- [ ] ClockFrame.kt: full-screen digital clock (large, clean)
- [ ] ClockFrame.kt: word clock mode ("it is twenty past three")
- [ ] Add clock to auto-rotation (or as always-visible overlay option)
- [ ] Pomodoro: server-side timer state (start/pause/reset via web UI)
- [ ] Pomodoro: TV shows big countdown, color shifts as time runs out
- [ ] Pomodoro: sound/visual alert on completion

### Phase 3: Habit Tracker
- [ ] Server: habits.json persistence (name, frequency, streak, history)
- [ ] Server: habit_sync WebSocket message
- [ ] Web UI: manage habits (add/complete/delete)
- [ ] HabitFrame.kt: show habits with streak counts, flame icons
- [ ] Add to rotation or overlay on other frames

### Phase 4: Phone Remote
- [ ] Web UI: responsive mobile layout (already have web companion)
- [ ] Swipe left/right → send frame_change to server → broadcast to TVs
- [ ] Play/pause music controls
- [ ] Quick-add todo from phone
- [ ] This is essentially the web companion with mobile-first CSS

### Phase 5: Multi-TV Sync
- [ ] Server: track multiple TV clients by ID (device name)
- [ ] Server: per-TV frame assignment (TV1=images, TV2=calendar)
- [ ] Web UI: TV management page (see connected TVs, assign content)
- [ ] App: send device identifier on connect
- [ ] Server: broadcast frame_change commands to specific TVs

### Phase 6: Ambient Light Adaptation
- [ ] Server: time-based brightness schedule in config
- [ ] Server: brightness_sync message (0.0-1.0 dimming factor)
- [ ] App: apply dimming via Window brightness + color overlay
- [ ] Darker image selection during night hours (tag images as dark/light)

### Phase 7: Digital Art Frame
- [ ] Research: free art APIs (Unsplash, Pexels, WikiArt, ArtStation)
- [ ] Server: art fetcher that downloads curated images periodically
- [ ] Server: art_sync message with attributed images
- [ ] App: show art with artist attribution overlay
- [ ] Web UI: configure art categories/preferences

## Priority Order
1. Web Companion + Photo Drops (biggest unlock — everything uses the web UI)
2. Clock + Pomodoro (high daily utility)
3. Habit Tracker (quick win, uses existing patterns)
4. Phone Remote (free if web companion is responsive)
5. Multi-TV Sync (server-side routing)
6. Ambient Light (polish)
7. Digital Art (content expansion)
