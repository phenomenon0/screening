package main

import (
	"encoding/json"
	"fmt"
	"log"
	"net/http"
)

// JSON-RPC 2.0 types
type jsonRPCRequest struct {
	JSONRPC string          `json:"jsonrpc"`
	ID      interface{}     `json:"id"`
	Method  string          `json:"method"`
	Params  json.RawMessage `json:"params,omitempty"`
}

type jsonRPCResponse struct {
	JSONRPC string      `json:"jsonrpc"`
	ID      interface{} `json:"id"`
	Result  interface{} `json:"result,omitempty"`
	Error   *rpcError   `json:"error,omitempty"`
}

type rpcError struct {
	Code    int    `json:"code"`
	Message string `json:"message"`
}

type mcpTool struct {
	Name        string          `json:"name"`
	Description string          `json:"description"`
	InputSchema json.RawMessage `json:"inputSchema"`
}

type mcpContent struct {
	Type string `json:"type"`
	Text string `json:"text"`
}

var mcpTools = []mcpTool{
	{Name: "switch_frame", Description: "Switch TV to a view: 0=gallery,1=todos,2=habits,3=calendar,4=videos,5=music,6=ai",
		InputSchema: raw(`{"type":"object","properties":{"frame":{"type":"integer"}},"required":["frame"]}`)},
	{Name: "toggle_todo", Description: "Toggle a todo item done/undone",
		InputSchema: raw(`{"type":"object","properties":{"id":{"type":"string"}},"required":["id"]}`)},
	{Name: "add_todo", Description: "Add a new todo item",
		InputSchema: raw(`{"type":"object","properties":{"text":{"type":"string"},"priority":{"type":"integer","description":"0=low,1=med,2=high"}},"required":["text"]}`)},
	{Name: "delete_todo", Description: "Delete a todo item",
		InputSchema: raw(`{"type":"object","properties":{"id":{"type":"string"}},"required":["id"]}`)},
	{Name: "toggle_habit", Description: "Toggle a habit for today",
		InputSchema: raw(`{"type":"object","properties":{"id":{"type":"string"}},"required":["id"]}`)},
	{Name: "add_habit", Description: "Add a new habit",
		InputSchema: raw(`{"type":"object","properties":{"name":{"type":"string"}},"required":["name"]}`)},
	{Name: "music_control", Description: "Control music playback: play, pause, next, prev",
		InputSchema: raw(`{"type":"object","properties":{"action":{"type":"string","enum":["play","pause","next","prev"]}},"required":["action"]}`)},
	{Name: "web_search", Description: "Search the web for information",
		InputSchema: raw(`{"type":"object","properties":{"query":{"type":"string"}},"required":["query"]}`)},
	{Name: "fetch_url", Description: "Fetch a URL and return its text content",
		InputSchema: raw(`{"type":"object","properties":{"url":{"type":"string"}},"required":["url"]}`)},
	{Name: "save_note", Description: "Save a persistent note",
		InputSchema: raw(`{"type":"object","properties":{"title":{"type":"string"},"content":{"type":"string"}},"required":["title","content"]}`)},
	{Name: "list_notes", Description: "List all saved notes",
		InputSchema: raw(`{"type":"object","properties":{}}`)},
	{Name: "delete_note", Description: "Delete a note by ID",
		InputSchema: raw(`{"type":"object","properties":{"id":{"type":"string"}},"required":["id"]}`)},
	{Name: "get_state", Description: "Get current dashboard state (todos, habits, calendar, weather)",
		InputSchema: raw(`{"type":"object","properties":{}}`)},
}

func raw(s string) json.RawMessage { return json.RawMessage(s) }

func handleMCP(hub *Hub, notes *NoteStore) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if r.Method == "OPTIONS" {
			w.Header().Set("Access-Control-Allow-Origin", "*")
			w.Header().Set("Access-Control-Allow-Methods", "POST, OPTIONS")
			w.Header().Set("Access-Control-Allow-Headers", "Content-Type")
			w.WriteHeader(http.StatusNoContent)
			return
		}
		if r.Method != "POST" {
			http.Error(w, "POST only", http.StatusMethodNotAllowed)
			return
		}

		var req jsonRPCRequest
		if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
			writeRPC(w, nil, nil, &rpcError{Code: -32700, Message: "Parse error"})
			return
		}

		log.Printf("mcp: %s (id=%v)", req.Method, req.ID)
		w.Header().Set("Access-Control-Allow-Origin", "*")

		switch req.Method {
		case "initialize":
			writeRPC(w, req.ID, map[string]interface{}{
				"protocolVersion": "2024-11-05",
				"capabilities":    map[string]interface{}{"tools": map[string]interface{}{}},
				"serverInfo":      map[string]interface{}{"name": "screening-tv", "version": "1.0.0"},
			}, nil)

		case "notifications/initialized":
			writeRPC(w, req.ID, map[string]interface{}{}, nil)

		case "tools/list":
			writeRPC(w, req.ID, map[string]interface{}{"tools": mcpTools}, nil)

		case "tools/call":
			var params struct {
				Name      string          `json:"name"`
				Arguments json.RawMessage `json:"arguments"`
			}
			json.Unmarshal(req.Params, &params)
			result := executeMCPTool(hub, notes, params.Name, params.Arguments)
			writeRPC(w, req.ID, map[string]interface{}{
				"content": []mcpContent{{Type: "text", Text: result}},
			}, nil)

		case "resources/list":
			writeRPC(w, req.ID, map[string]interface{}{"resources": []interface{}{}}, nil)

		default:
			writeRPC(w, req.ID, nil, &rpcError{Code: -32601, Message: "Method not found"})
		}
	}
}

func executeMCPTool(hub *Hub, notes *NoteStore, name string, argsRaw json.RawMessage) string {
	var args map[string]interface{}
	json.Unmarshal(argsRaw, &args)

	getString := func(key string) string {
		if v, ok := args[key]; ok {
			if s, ok := v.(string); ok {
				return s
			}
		}
		return ""
	}
	getInt := func(key string) int {
		if v, ok := args[key]; ok {
			if f, ok := v.(float64); ok {
				return int(f)
			}
		}
		return 0
	}

	switch name {
	case "switch_frame":
		frame := getInt("frame")
		hub.Broadcast(ServerMessage{Type: "frame_change", Frame: frame})
		return fmt.Sprintf("Switched to frame %d", frame)

	case "toggle_todo":
		if hub.todos.Toggle(getString("id")) {
			hub.BroadcastTodos()
			return "Todo toggled"
		}
		return "Todo not found"

	case "add_todo":
		hub.todos.Add(getString("text"), getInt("priority"))
		hub.BroadcastTodos()
		return "Todo added"

	case "delete_todo":
		hub.todos.Delete(getString("id"))
		hub.BroadcastTodos()
		return "Todo deleted"

	case "toggle_habit":
		hub.habits.Toggle(getString("id"))
		hub.BroadcastHabits()
		return "Habit toggled"

	case "add_habit":
		hub.habits.Add(getString("name"))
		hub.BroadcastHabits()
		return "Habit added"

	case "music_control":
		action := getString("action")
		hub.SendToType("tv", ServerMessage{Type: "music_control", URL: action})
		return "Music: " + action

	case "web_search":
		results := WebSearch(getString("query"))
		data, _ := json.Marshal(results)
		return string(data)

	case "fetch_url":
		return FetchURL(getString("url"))

	case "save_note":
		n := notes.Add(getString("title"), getString("content"))
		return fmt.Sprintf("Note saved: %s (id=%s)", n.Title, n.ID)

	case "list_notes":
		list := notes.List()
		if len(list) == 0 {
			return "No notes saved."
		}
		data, _ := json.MarshalIndent(list, "", "  ")
		return string(data)

	case "delete_note":
		if notes.Delete(getString("id")) {
			return "Note deleted"
		}
		return "Note not found"

	case "get_state":
		state := map[string]interface{}{
			"todos":   hub.todos.Items(),
			"habits":  hub.habits.List(),
			"events":  hub.calendar.Events(),
			"devices": hub.Devices(),
			"notes":   notes.List(),
		}
		if hub.weather != nil {
			state["weather"] = hub.weather.Current()
		}
		data, _ := json.MarshalIndent(state, "", "  ")
		return string(data)

	default:
		return "Unknown tool: " + name
	}
}

func writeRPC(w http.ResponseWriter, id interface{}, result interface{}, err *rpcError) {
	w.Header().Set("Content-Type", "application/json")
	resp := jsonRPCResponse{JSONRPC: "2.0", ID: id, Result: result, Error: err}
	json.NewEncoder(w).Encode(resp)
}
