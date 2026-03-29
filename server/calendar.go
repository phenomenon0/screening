package main

import (
	"fmt"
	"log"
	"os"
	"sync"
	"time"

	ical "github.com/emersion/go-ical"
	"github.com/fsnotify/fsnotify"
)

// CalendarSource watches an ICS file and provides parsed events.
type CalendarSource struct {
	mu     sync.RWMutex
	events []Event
	path   string
	onChange func()
}

func NewCalendarSource(path string, onChange func()) *CalendarSource {
	cs := &CalendarSource{path: path, onChange: onChange}
	if path != "" {
		cs.reload()
	}
	return cs
}

func (cs *CalendarSource) Events() []Event {
	cs.mu.RLock()
	defer cs.mu.RUnlock()
	out := make([]Event, len(cs.events))
	copy(out, cs.events)
	return out
}

func (cs *CalendarSource) reload() {
	if cs.path == "" {
		return
	}
	f, err := os.Open(cs.path)
	if err != nil {
		log.Printf("calendar: open %s: %v", cs.path, err)
		return
	}
	defer f.Close()

	dec := ical.NewDecoder(f)
	var events []Event

	for {
		cal, err := dec.Decode()
		if err != nil {
			break
		}
		for _, comp := range cal.Children {
			if comp.Name != ical.CompEvent {
				continue
			}
			ev := Event{
				ID:    propVal(comp, ical.PropUID),
				Title: propVal(comp, ical.PropSummary),
			}

			dtStart, err := comp.Props.DateTime(ical.PropDateTimeStart, nil)
			if err == nil {
				ev.Start = dtStart.Format(time.RFC3339)
			}
			dtEnd, err := comp.Props.DateTime(ical.PropDateTimeEnd, nil)
			if err == nil {
				ev.End = dtEnd.Format(time.RFC3339)
			}

			// Check if all-day: VALUE=DATE means all-day
			if startProp := comp.Props.Get(ical.PropDateTimeStart); startProp != nil {
				if startProp.Params.Get("VALUE") == "DATE" {
					ev.AllDay = true
				}
			}

			ev.ID = fmt.Sprintf("%s-%s", ev.ID, ev.Start)
			events = append(events, ev)
		}
	}

	cs.mu.Lock()
	cs.events = events
	cs.mu.Unlock()
	log.Printf("calendar: loaded %d events from %s", len(events), cs.path)
}

func propVal(comp *ical.Component, name string) string {
	p := comp.Props.Get(name)
	if p == nil {
		return ""
	}
	return p.Value
}

// Watch starts watching the ICS file for changes.
func (cs *CalendarSource) Watch(done <-chan struct{}) {
	if cs.path == "" {
		return
	}
	watcher, err := fsnotify.NewWatcher()
	if err != nil {
		log.Printf("calendar watcher error: %v", err)
		return
	}
	defer watcher.Close()

	if err := watcher.Add(cs.path); err != nil {
		log.Printf("calendar watch add error: %v", err)
		return
	}
	log.Printf("calendar: watching %s", cs.path)

	for {
		select {
		case <-done:
			return
		case event, ok := <-watcher.Events:
			if !ok {
				return
			}
			if event.Has(fsnotify.Write) || event.Has(fsnotify.Create) {
				log.Printf("calendar: file changed, reloading")
				cs.reload()
				if cs.onChange != nil {
					cs.onChange()
				}
			}
		case err, ok := <-watcher.Errors:
			if !ok {
				return
			}
			log.Printf("calendar watcher error: %v", err)
		}
	}
}
