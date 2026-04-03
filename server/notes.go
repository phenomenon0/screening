package main

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"sync"
	"time"
)

type Note struct {
	ID      string `json:"id"`
	Title   string `json:"title"`
	Content string `json:"content"`
	Created string `json:"created"`
}

type NoteStore struct {
	mu       sync.RWMutex
	notes    []Note
	filePath string
	nextID   int
}

func NewNoteStore(dataDir string) *NoteStore {
	ns := &NoteStore{filePath: filepath.Join(dataDir, "notes.json")}
	ns.load()
	return ns
}

func (ns *NoteStore) load() {
	data, err := os.ReadFile(ns.filePath)
	if err != nil {
		return
	}
	json.Unmarshal(data, &ns.notes)
	for _, n := range ns.notes {
		var id int
		fmt.Sscanf(n.ID, "n%d", &id)
		if id >= ns.nextID {
			ns.nextID = id + 1
		}
	}
}

func (ns *NoteStore) save() {
	data, _ := json.MarshalIndent(ns.notes, "", "  ")
	tmp := ns.filePath + ".tmp"
	if err := os.WriteFile(tmp, data, 0644); err == nil {
		os.Rename(tmp, ns.filePath)
	}
}

func (ns *NoteStore) Add(title, content string) Note {
	ns.mu.Lock()
	defer ns.mu.Unlock()
	ns.nextID++
	n := Note{
		ID:      fmt.Sprintf("n%d", ns.nextID),
		Title:   title,
		Content: content,
		Created: time.Now().Format("2006-01-02 15:04"),
	}
	ns.notes = append(ns.notes, n)
	ns.save()
	return n
}

func (ns *NoteStore) List() []Note {
	ns.mu.RLock()
	defer ns.mu.RUnlock()
	out := make([]Note, len(ns.notes))
	copy(out, ns.notes)
	return out
}

func (ns *NoteStore) Delete(id string) bool {
	ns.mu.Lock()
	defer ns.mu.Unlock()
	for i, n := range ns.notes {
		if n.ID == id {
			ns.notes = append(ns.notes[:i], ns.notes[i+1:]...)
			ns.save()
			return true
		}
	}
	return false
}
