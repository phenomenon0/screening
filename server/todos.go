package main

import (
	"encoding/json"
	"fmt"
	"log"
	"os"
	"sync"
	"time"

	"github.com/fsnotify/fsnotify"
)

// TodoSource manages a JSON file of todo items.
type TodoSource struct {
	mu       sync.RWMutex
	items    []TodoItem
	path     string
	onChange func()
}

func NewTodoSource(path string, onChange func()) *TodoSource {
	ts := &TodoSource{path: path, onChange: onChange}
	ts.reload()
	return ts
}

func (ts *TodoSource) Items() []TodoItem {
	ts.mu.RLock()
	defer ts.mu.RUnlock()
	out := make([]TodoItem, len(ts.items))
	copy(out, ts.items)
	return out
}

func (ts *TodoSource) reload() {
	if ts.path == "" {
		return
	}
	data, err := os.ReadFile(ts.path)
	if err != nil {
		if os.IsNotExist(err) {
			// Create default todos file
			ts.items = []TodoItem{
				{ID: "1", Text: "Set up calendar ICS file", Done: false, Priority: 1},
				{ID: "2", Text: "Add images to dashboard folder", Done: false, Priority: 2},
				{ID: "3", Text: "Configure server settings", Done: false, Priority: 3},
			}
			ts.save()
			return
		}
		log.Printf("todos: read %s: %v", ts.path, err)
		return
	}
	var items []TodoItem
	if err := json.Unmarshal(data, &items); err != nil {
		log.Printf("todos: parse %s: %v", ts.path, err)
		return
	}
	ts.mu.Lock()
	ts.items = items
	ts.mu.Unlock()
	log.Printf("todos: loaded %d items from %s", len(items), ts.path)
}

func (ts *TodoSource) save() {
	if ts.path == "" {
		return
	}
	data, err := json.MarshalIndent(ts.items, "", "  ")
	if err != nil {
		log.Printf("todos: marshal error: %v", err)
		return
	}
	if err := os.WriteFile(ts.path, data, 0644); err != nil {
		log.Printf("todos: write %s: %v", ts.path, err)
	}
}

// Add creates a new todo item.
func (ts *TodoSource) Add(text string, priority int) {
	ts.mu.Lock()
	defer ts.mu.Unlock()
	if priority < 1 || priority > 3 {
		priority = 2
	}
	ts.items = append(ts.items, TodoItem{
		ID:       fmt.Sprintf("t%d", time.Now().UnixMilli()),
		Text:     text,
		Done:     false,
		Priority: priority,
	})
	ts.save()
}

// Delete removes a todo by ID.
func (ts *TodoSource) Delete(id string) bool {
	ts.mu.Lock()
	defer ts.mu.Unlock()
	for i := range ts.items {
		if ts.items[i].ID == id {
			ts.items = append(ts.items[:i], ts.items[i+1:]...)
			ts.save()
			return true
		}
	}
	return false
}

// Toggle flips the done state of a todo by ID and persists.
func (ts *TodoSource) Toggle(id string) bool {
	ts.mu.Lock()
	defer ts.mu.Unlock()
	for i := range ts.items {
		if ts.items[i].ID == id {
			ts.items[i].Done = !ts.items[i].Done
			ts.save()
			return true
		}
	}
	return false
}

// Watch watches the todos file for external changes.
func (ts *TodoSource) Watch(done <-chan struct{}) {
	if ts.path == "" {
		return
	}
	watcher, err := fsnotify.NewWatcher()
	if err != nil {
		log.Printf("todos watcher error: %v", err)
		return
	}
	defer watcher.Close()

	if err := watcher.Add(ts.path); err != nil {
		log.Printf("todos watch add error: %v", err)
		return
	}
	log.Printf("todos: watching %s", ts.path)

	for {
		select {
		case <-done:
			return
		case event, ok := <-watcher.Events:
			if !ok {
				return
			}
			if event.Has(fsnotify.Write) || event.Has(fsnotify.Create) {
				ts.reload()
				if ts.onChange != nil {
					ts.onChange()
				}
			}
		case err, ok := <-watcher.Errors:
			if !ok {
				return
			}
			log.Printf("todos watcher error: %v", err)
		}
	}
}
