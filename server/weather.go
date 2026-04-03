package main

import (
	"encoding/json"
	"log"
	"net/http"
	"os"
	"path/filepath"
	"sync"
	"time"
)

type WeatherInfo struct {
	TempF     string `json:"temp_f"`
	TempC     string `json:"temp_c"`
	Condition string `json:"condition"`
	Emoji     string `json:"emoji"`
}

type WeatherSource struct {
	mu        sync.RWMutex
	current   WeatherInfo
	onChange  func()
	cachePath string
}

func NewWeatherSource(onChange func()) *WeatherSource {
	home, _ := os.UserHomeDir()
	ws := &WeatherSource{
		onChange:  onChange,
		cachePath: filepath.Join(home, ".local", "share", "screening", "weather_cache.json"),
	}
	ws.loadCache()
	ws.fetch()
	return ws
}

func (ws *WeatherSource) loadCache() {
	data, err := os.ReadFile(ws.cachePath)
	if err != nil {
		return
	}
	var info WeatherInfo
	if json.Unmarshal(data, &info) == nil && info.TempF != "" {
		ws.mu.Lock()
		ws.current = info
		ws.mu.Unlock()
		log.Printf("weather: loaded cache: %s %s°F (%s)", info.Emoji, info.TempF, info.Condition)
	}
}

func (ws *WeatherSource) saveCache() {
	ws.mu.RLock()
	data, _ := json.Marshal(ws.current)
	ws.mu.RUnlock()
	os.WriteFile(ws.cachePath, data, 0644)
}

func (ws *WeatherSource) Current() WeatherInfo {
	ws.mu.RLock()
	defer ws.mu.RUnlock()
	return ws.current
}

func (ws *WeatherSource) fetch() {
	client := &http.Client{Timeout: 10 * time.Second}
	resp, err := client.Get("https://wttr.in/?format=j1")
	if err != nil {
		log.Printf("weather: fetch error: %v", err)
		return
	}
	defer resp.Body.Close()

	var data struct {
		CurrentCondition []struct {
			TempF       string `json:"temp_F"`
			TempC       string `json:"temp_C"`
			WeatherDesc []struct {
				Value string `json:"value"`
			} `json:"weatherDesc"`
			WeatherCode string `json:"weatherCode"`
		} `json:"current_condition"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&data); err != nil {
		log.Printf("weather: parse error: %v", err)
		return
	}

	if len(data.CurrentCondition) == 0 {
		return
	}

	cc := data.CurrentCondition[0]
	condition := ""
	if len(cc.WeatherDesc) > 0 {
		condition = cc.WeatherDesc[0].Value
	}

	ws.mu.Lock()
	ws.current = WeatherInfo{
		TempF:     cc.TempF,
		TempC:     cc.TempC,
		Condition: condition,
		Emoji:     weatherEmoji(cc.WeatherCode, condition),
	}
	ws.mu.Unlock()
	log.Printf("weather: %s %s°F (%s)", ws.current.Emoji, ws.current.TempF, condition)
	ws.saveCache()
}

func weatherEmoji(code string, condition string) string {
	switch code {
	case "113":
		return "\u2600\uFE0F" // sunny
	case "116":
		return "\u26C5" // partly cloudy
	case "119", "122":
		return "\u2601\uFE0F" // cloudy
	case "143", "248", "260":
		return "\U0001F32B\uFE0F" // fog
	case "176", "263", "266", "293", "296", "299", "302", "305", "308", "311", "314", "317", "353", "356", "359", "362", "365":
		return "\U0001F327\uFE0F" // rain
	case "179", "182", "185", "227", "230", "320", "323", "326", "329", "332", "335", "338", "368", "371", "374", "377":
		return "\u2744\uFE0F" // snow
	case "200", "386", "389", "392", "395":
		return "\u26A1" // thunder
	default:
		return "\u2601\uFE0F" // default cloudy
	}
}

func (ws *WeatherSource) StartPolling(done <-chan struct{}) {
	// Broadcast cached/initial weather immediately
	if ws.onChange != nil && ws.Current().TempF != "" {
		ws.onChange()
	}
	ticker := time.NewTicker(15 * time.Minute)
	defer ticker.Stop()
	for {
		select {
		case <-done:
			return
		case <-ticker.C:
			ws.fetch()
			if ws.onChange != nil {
				ws.onChange()
			}
		}
	}
}
