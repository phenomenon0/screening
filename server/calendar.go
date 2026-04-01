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

type AlarmInfo struct {
	EventTitle string    `json:"event_title"`
	EventStart time.Time `json:"event_start"`
	AlarmAt    time.Time `json:"alarm_at"`
	Fired      bool
}

// CalendarSource watches an ICS file and provides parsed events.
type CalendarSource struct {
	mu      sync.RWMutex
	events  []Event
	alarms  []AlarmInfo
	path    string
	onChange func()
	onAlarm func(AlarmInfo)
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
	var alarms []AlarmInfo

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

			// Parse VALARM children
			for _, child := range comp.Children {
				if child.Name == "VALARM" {
					triggerProp := child.Props.Get("TRIGGER")
					if triggerProp != nil && dtStart.After(time.Now().Add(-24*time.Hour)) {
						dur := parseICALDuration(triggerProp.Value)
						alarmTime := dtStart.Add(dur)
						if alarmTime.After(time.Now()) {
							alarms = append(alarms, AlarmInfo{
								EventTitle: ev.Title,
								EventStart: dtStart,
								AlarmAt:    alarmTime,
							})
						}
					}
				}
			}

			ev.ID = fmt.Sprintf("%s-%s", ev.ID, ev.Start)
			events = append(events, ev)
		}
	}

	cs.mu.Lock()
	cs.events = events
	cs.alarms = alarms
	cs.mu.Unlock()
	log.Printf("calendar: loaded %d events, %d alarms from %s", len(events), len(alarms), cs.path)
}

func propVal(comp *ical.Component, name string) string {
	p := comp.Props.Get(name)
	if p == nil {
		return ""
	}
	return p.Value
}

// parseICALDuration parses "-PT5M" style durations
func parseICALDuration(s string) time.Duration {
	neg := false
	if len(s) > 0 && s[0] == '-' {
		neg = true
		s = s[1:]
	}
	if len(s) > 0 && s[0] == 'P' {
		s = s[1:]
	}
	if len(s) > 0 && s[0] == 'T' {
		s = s[1:]
	}
	var d time.Duration
	num := 0
	for _, c := range s {
		if c >= '0' && c <= '9' {
			num = num*10 + int(c-'0')
		} else {
			switch c {
			case 'H':
				d += time.Duration(num) * time.Hour
			case 'M':
				d += time.Duration(num) * time.Minute
			case 'S':
				d += time.Duration(num) * time.Second
			}
			num = 0
		}
	}
	if neg {
		d = -d
	}
	return d
}

// StartAlarmChecker checks every 30 seconds for alarms to fire
func (cs *CalendarSource) StartAlarmChecker(done <-chan struct{}) {
	ticker := time.NewTicker(30 * time.Second)
	defer ticker.Stop()
	for {
		select {
		case <-done:
			return
		case <-ticker.C:
			cs.checkAlarms()
		}
	}
}

func (cs *CalendarSource) checkAlarms() {
	now := time.Now()
	cs.mu.Lock()
	defer cs.mu.Unlock()
	for i := range cs.alarms {
		a := &cs.alarms[i]
		if a.Fired {
			continue
		}
		if now.After(a.AlarmAt) && now.Before(a.AlarmAt.Add(2*time.Minute)) {
			a.Fired = true
			log.Printf("alarm: firing for %q (starts %s)", a.EventTitle, a.EventStart.Format("15:04"))
			if cs.onAlarm != nil {
				cs.onAlarm(*a)
			}
		}
	}
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

	var debounce *time.Timer
	for {
		select {
		case <-done:
			if debounce != nil {
				debounce.Stop()
			}
			return
		case event, ok := <-watcher.Events:
			if !ok {
				return
			}
			if event.Has(fsnotify.Write) || event.Has(fsnotify.Create) {
				if debounce != nil {
					debounce.Stop()
				}
				debounce = time.AfterFunc(300*time.Millisecond, func() {
					log.Printf("calendar: file changed, reloading")
					cs.reload()
					if cs.onChange != nil {
						cs.onChange()
					}
				})
			}
		case err, ok := <-watcher.Errors:
			if !ok {
				return
			}
			log.Printf("calendar watcher error: %v", err)
		}
	}
}
