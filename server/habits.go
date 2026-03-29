package main

import (
	"encoding/json"
	"fmt"
	"log"
	"os"
	"sort"
	"sync"
	"time"
)

type Habit struct {
	ID         string   `json:"id"`
	Name       string   `json:"name"`
	Streak     int      `json:"streak"`
	BestStreak int      `json:"best_streak"`
	DoneToday  bool     `json:"done_today"`
	LastDone   string   `json:"last_done"`   // YYYY-MM-DD
	History    []string `json:"history"`      // last 90 days of YYYY-MM-DD completions
	CreatedAt  string   `json:"created_at"`
	TotalDone  int      `json:"total_done"`
}

type HabitStore struct {
	mu       sync.RWMutex
	habits   []Habit
	path     string
	onChange func()
}

func NewHabitStore(dir string, onChange func()) *HabitStore {
	path := ""
	if dir != "" {
		os.MkdirAll(dir, 0755)
		path = dir + "/habits.json"
	}
	hs := &HabitStore{path: path, onChange: onChange}
	hs.load()
	return hs
}

func (hs *HabitStore) List() []Habit {
	hs.mu.Lock()
	defer hs.mu.Unlock()
	hs.refreshToday()
	out := make([]Habit, len(hs.habits))
	copy(out, hs.habits)
	return out
}

func (hs *HabitStore) Add(name string) {
	hs.mu.Lock()
	defer hs.mu.Unlock()
	hs.habits = append(hs.habits, Habit{
		ID:        fmt.Sprintf("h%d", time.Now().UnixMilli()),
		Name:      name,
		CreatedAt: time.Now().Format("2006-01-02"),
	})
	hs.save()
}

func (hs *HabitStore) Delete(id string) bool {
	hs.mu.Lock()
	defer hs.mu.Unlock()
	for i := range hs.habits {
		if hs.habits[i].ID == id {
			hs.habits = append(hs.habits[:i], hs.habits[i+1:]...)
			hs.save()
			return true
		}
	}
	return false
}

func (hs *HabitStore) Toggle(id string) {
	hs.mu.Lock()
	defer hs.mu.Unlock()
	today := time.Now().Format("2006-01-02")
	for i := range hs.habits {
		if hs.habits[i].ID == id {
			h := &hs.habits[i]
			if h.DoneToday {
				// Undo today
				h.DoneToday = false
				h.Streak--
				if h.Streak < 0 {
					h.Streak = 0
				}
				h.TotalDone--
				if h.TotalDone < 0 {
					h.TotalDone = 0
				}
				// Remove today from history
				h.History = removeFromHistory(h.History, today)
			} else {
				// Complete today
				h.DoneToday = true
				h.LastDone = today
				h.Streak++
				h.TotalDone++
				if h.Streak > h.BestStreak {
					h.BestStreak = h.Streak
				}
				// Add to history (keep last 90 days)
				h.History = append(h.History, today)
				h.History = trimHistory(h.History, 90)
			}
			break
		}
	}
	hs.save()
}

func (hs *HabitStore) refreshToday() {
	today := time.Now().Format("2006-01-02")
	yesterday := time.Now().AddDate(0, 0, -1).Format("2006-01-02")
	changed := false
	for i := range hs.habits {
		h := &hs.habits[i]
		if h.LastDone != today {
			h.DoneToday = false
			if h.LastDone != yesterday && h.Streak > 0 {
				h.Streak = 0
				changed = true
			}
		}
	}
	if changed {
		hs.save()
	}
}

func removeFromHistory(history []string, date string) []string {
	out := make([]string, 0, len(history))
	for _, d := range history {
		if d != date {
			out = append(out, d)
		}
	}
	return out
}

func trimHistory(history []string, maxDays int) []string {
	if len(history) <= maxDays {
		return history
	}
	sort.Strings(history)
	return history[len(history)-maxDays:]
}

func (hs *HabitStore) load() {
	if hs.path == "" {
		return
	}
	data, err := os.ReadFile(hs.path)
	if err != nil {
		return
	}
	json.Unmarshal(data, &hs.habits)
	log.Printf("habits: loaded %d habits", len(hs.habits))
}

func (hs *HabitStore) save() {
	if hs.path == "" {
		return
	}
	data, _ := json.MarshalIndent(hs.habits, "", "  ")
	os.WriteFile(hs.path, data, 0644)
}
