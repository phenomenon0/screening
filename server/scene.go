package main

import (
	"encoding/json"
	"log"
	"os"
	"path/filepath"
	"strings"
	"sync"
)

type SceneInfo struct {
	ID   string `json:"id"`
	Name string `json:"name"`
}

type SceneManager struct {
	mu  sync.RWMutex
	dir string
}

func NewSceneManager(dir string) *SceneManager {
	if dir != "" {
		os.MkdirAll(dir, 0755)
	}
	return &SceneManager{dir: dir}
}

func (sm *SceneManager) List() []SceneInfo {
	sm.mu.RLock()
	defer sm.mu.RUnlock()
	if sm.dir == "" {
		return nil
	}

	entries, err := os.ReadDir(sm.dir)
	if err != nil {
		return nil
	}

	var scenes []SceneInfo
	for _, e := range entries {
		if e.IsDir() || !strings.HasSuffix(e.Name(), ".json") {
			continue
		}
		data, err := os.ReadFile(filepath.Join(sm.dir, e.Name()))
		if err != nil {
			continue
		}
		var s struct {
			ID   string `json:"id"`
			Name string `json:"name"`
		}
		if json.Unmarshal(data, &s) == nil && s.ID != "" {
			scenes = append(scenes, SceneInfo{ID: s.ID, Name: s.Name})
		}
	}
	return scenes
}

func (sm *SceneManager) Get(id string) (json.RawMessage, error) {
	sm.mu.RLock()
	defer sm.mu.RUnlock()
	path := filepath.Join(sm.dir, id+".json")
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}
	return json.RawMessage(data), nil
}

func (sm *SceneManager) Save(id string, data json.RawMessage) error {
	sm.mu.Lock()
	defer sm.mu.Unlock()
	path := filepath.Join(sm.dir, id+".json")
	return os.WriteFile(path, data, 0644)
}

func (sm *SceneManager) CreateDefault() {
	defaultScene := `{
  "id": "demo",
  "name": "Demo Scene",
  "environment": {
    "background": "#1a1a2e",
    "ambientLight": {"color": "#ffffff", "intensity": 0.4}
  },
  "camera": {
    "type": "orbit",
    "position": [3, 2, 5],
    "target": [0, 0.5, 0],
    "fov": 60
  },
  "lights": [
    {"id": "sun", "type": "directional", "color": "#ffffff", "intensity": 1.0, "position": [5, 10, 5], "castShadow": true},
    {"id": "fill", "type": "point", "color": "#00D2FF", "intensity": 0.5, "position": [-3, 3, -3]}
  ],
  "objects": [],
  "ground": {"enabled": true, "size": 20, "color": "#2a2a3e", "grid": true}
}`

	path := filepath.Join(sm.dir, "demo.json")
	if _, err := os.Stat(path); os.IsNotExist(err) {
		os.WriteFile(path, []byte(defaultScene), 0644)
		log.Printf("scenes: created default demo scene")
	}
}
