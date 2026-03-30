package main

import (
	"sync"
	"time"
)

type PomodoroState struct {
	Remaining int  `json:"remaining"`
	Duration  int  `json:"duration"`
	Running   bool `json:"running"`
}

type Pomodoro struct {
	mu       sync.RWMutex
	state    PomodoroState
	ticker   *time.Ticker
	done     chan struct{}
	onChange func()
}

func NewPomodoro(onChange func()) *Pomodoro {
	return &Pomodoro{
		state:    PomodoroState{Remaining: 25 * 60, Duration: 25 * 60},
		onChange: onChange,
	}
}

func (p *Pomodoro) State() PomodoroState {
	p.mu.RLock()
	defer p.mu.RUnlock()
	return p.state
}

func (p *Pomodoro) Start() {
	p.mu.Lock()
	defer p.mu.Unlock()
	if p.state.Running || p.state.Remaining <= 0 {
		return
	}
	p.state.Running = true
	p.ticker = time.NewTicker(1 * time.Second)
	p.done = make(chan struct{})
	go p.tick()
}

func (p *Pomodoro) tick() {
	for {
		select {
		case <-p.done:
			return
		case <-p.ticker.C:
			p.mu.Lock()
			if !p.state.Running {
				p.mu.Unlock()
				return
			}
			p.state.Remaining--
			if p.state.Remaining <= 0 {
				p.state.Remaining = 0
				p.stop()
			}
			p.mu.Unlock()
			if p.onChange != nil {
				p.onChange()
			}
		}
	}
}

// stop halts the ticker. Must be called with mu held.
func (p *Pomodoro) stop() {
	p.state.Running = false
	if p.ticker != nil {
		p.ticker.Stop()
	}
	if p.done != nil {
		close(p.done)
		p.done = nil
	}
}

func (p *Pomodoro) Pause() {
	p.mu.Lock()
	defer p.mu.Unlock()
	if !p.state.Running {
		return
	}
	p.stop()
}

func (p *Pomodoro) Reset() {
	p.mu.Lock()
	defer p.mu.Unlock()
	p.stop()
	p.state.Remaining = p.state.Duration
}

func (p *Pomodoro) Set(minutes int) {
	p.mu.Lock()
	defer p.mu.Unlock()
	p.stop()
	p.state.Duration = minutes * 60
	p.state.Remaining = minutes * 60
}
