package main

import (
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"
)

var searchClient = &http.Client{Timeout: 10 * time.Second}

type SearchResult struct {
	Title   string `json:"title"`
	URL     string `json:"url"`
	Snippet string `json:"snippet"`
}

// WebSearch queries DuckDuckGo and returns structured results.
func WebSearch(query string) []SearchResult {
	// DuckDuckGo instant answer API
	u := fmt.Sprintf("https://api.duckduckgo.com/?q=%s&format=json&no_html=1&skip_disambig=1", url.QueryEscape(query))
	resp, err := searchClient.Get(u)
	if err != nil {
		return nil
	}
	defer resp.Body.Close()
	body, _ := io.ReadAll(resp.Body)

	var ddg struct {
		Abstract       string `json:"Abstract"`
		AbstractText   string `json:"AbstractText"`
		AbstractSource string `json:"AbstractSource"`
		AbstractURL    string `json:"AbstractURL"`
		RelatedTopics  []struct {
			Text     string `json:"Text"`
			FirstURL string `json:"FirstURL"`
		} `json:"RelatedTopics"`
	}
	json.Unmarshal(body, &ddg)

	var results []SearchResult
	if ddg.AbstractText != "" {
		results = append(results, SearchResult{
			Title:   ddg.AbstractSource,
			URL:     ddg.AbstractURL,
			Snippet: ddg.AbstractText,
		})
	}
	for _, rt := range ddg.RelatedTopics {
		if rt.Text != "" && len(results) < 5 {
			results = append(results, SearchResult{
				Title:   truncate(rt.Text, 80),
				URL:     rt.FirstURL,
				Snippet: rt.Text,
			})
		}
	}
	return results
}

// FetchURL fetches a URL and returns plain text (HTML stripped).
func FetchURL(rawURL string) string {
	resp, err := searchClient.Get(rawURL)
	if err != nil {
		return fmt.Sprintf("Error fetching URL: %v", err)
	}
	defer resp.Body.Close()
	body, _ := io.ReadAll(io.LimitReader(resp.Body, 50_000))
	text := stripHTML(string(body))
	return truncate(text, 3000)
}

func handleSearch(w http.ResponseWriter, r *http.Request) {
	q := r.URL.Query().Get("q")
	if q == "" {
		http.Error(w, "missing ?q=", http.StatusBadRequest)
		return
	}
	results := WebSearch(q)
	w.Header().Set("Content-Type", "application/json")
	json.NewEncoder(w).Encode(results)
}

func handleFetchURL(w http.ResponseWriter, r *http.Request) {
	u := r.URL.Query().Get("url")
	if u == "" {
		http.Error(w, "missing ?url=", http.StatusBadRequest)
		return
	}
	text := FetchURL(u)
	w.Header().Set("Content-Type", "text/plain")
	w.Write([]byte(text))
}

func stripHTML(s string) string {
	var out strings.Builder
	inTag := false
	for _, c := range s {
		switch {
		case c == '<':
			inTag = true
		case c == '>':
			inTag = false
			out.WriteRune(' ')
		case !inTag:
			out.WriteRune(c)
		}
	}
	// Collapse whitespace
	result := out.String()
	for strings.Contains(result, "  ") {
		result = strings.ReplaceAll(result, "  ", " ")
	}
	return strings.TrimSpace(result)
}

func truncate(s string, maxLen int) string {
	if len(s) <= maxLen {
		return s
	}
	return s[:maxLen] + "..."
}
