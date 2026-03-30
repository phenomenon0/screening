# Screening/Worldcast Hardening Plan

## Priority: Bug fixes first, then architecture, then polish

### Phase 1: Critical Bug Fixes (do now)
- [ ] 1.1 Fix Pomodoro ticker goroutine leak (done channel)
- [ ] 1.2 Fix Hub.Broadcast holding RLock during network I/O (snapshot pattern)
- [ ] 1.3 Fix WebSocket accept ordering (check err before SetReadLimit)
- [ ] 1.4 Fix scene_load not passing scene_id to renderer JS
- [ ] 1.5 Fix TV SetupScreen buttons not functional (wire onConnect/onScan)
- [ ] 1.6 Add device registration on connect (TV as "tv", mobile as "mobile")
- [ ] 1.7 Add fsnotify debounce to all watchers
- [ ] 1.8 Fix SceneStream ffmpeg retry with backoff (max 3 attempts)

### Phase 2: Protocol & Architecture (next)
- [ ] 2.1 Extract handler registry from monolithic switch statement
- [ ] 2.2 Add message envelope: version, source, target fields
- [ ] 2.3 Add pairing token (QR contains host+port+token)
- [ ] 2.4 Add capability registration on device connect
- [ ] 2.5 Add device presence/health (last heartbeat, RTT)

### Phase 3: Transport & Reliability
- [ ] 3.1 Fix Wayland screen share path
- [ ] 3.2 Frame registry replacing raw frame indices
- [ ] 3.3 WebSocket reconnect with jitter
- [ ] 3.4 Queue outbound messages until connected

### Phase 4: Polish
- [ ] 4.1 Fix camera quaternion math in phone controller
- [ ] 4.2 Web UI: stop full DOM rebuild on state change
- [ ] 4.3 Scene schema versioning
