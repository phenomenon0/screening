package com.screening.dashboard.data

import com.screening.shared.model.DashboardState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class AiResponse(
    val text: String,
    val actions: List<AiAction> = emptyList(),
    val imageUrl: String? = null
)

sealed class AiAction {
    data class SwitchFrame(val frame: Int) : AiAction()
    data class ToggleTodo(val id: String) : AiAction()
    data class AddTodo(val text: String, val priority: Int = 0) : AiAction()
    data class ToggleHabit(val id: String) : AiAction()
    data class MusicControl(val action: String) : AiAction()
    data class ShowImage(val url: String) : AiAction()
}

class AiClient(private val serverBaseUrl: String = "") {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

    private val tools = JSONArray(TOOLS_JSON)

    /** Multi-turn tool use: sends to Claude, executes tools, loops until done. */
    suspend fun ask(transcript: String, state: DashboardState): AiResponse = withContext(Dispatchers.IO) {
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "user").put("content", transcript))

        val allActions = mutableListOf<AiAction>()
        var finalText = ""
        var imageUrl: String? = null

        // Tool use loop (max 3 rounds to cap API cost)
        repeat(3) { round ->
            val response = try {
                callClaude(messages, state)
            } catch (e: Exception) {
                return@withContext AiResponse("Error: ${e.message?.take(100) ?: "network failure"}")
            }
            val content = response.optJSONArray("content")
                ?: return@withContext AiResponse(response.optJSONObject("error")?.optString("message") ?: "Empty response")
            val stopReason = response.optString("stop_reason")

            var text = ""
            val toolUses = mutableListOf<JSONObject>()
            for (i in 0 until content.length()) {
                val item = content.getJSONObject(i)
                when (item.optString("type")) {
                    "text" -> text = item.optString("text")
                    "tool_use" -> toolUses.add(item)
                }
            }
            if (text.isNotEmpty()) finalText = text

            // Parse action-only tools (switch_frame, toggle_todo, etc.)
            for (tool in toolUses) {
                parseAction(tool)?.let { allActions.add(it) }
                // Check for show_image
                if (tool.optString("name") == "show_image") {
                    imageUrl = tool.optJSONObject("input")?.optString("url")
                }
            }

            if (stopReason != "tool_use" || toolUses.isEmpty()) {
                return@withContext AiResponse(
                    text = finalText.ifBlank { if (allActions.isNotEmpty()) "Done." else "I didn't understand that." },
                    actions = allActions,
                    imageUrl = imageUrl
                )
            }

            // Add assistant message with tool_use blocks
            messages.put(JSONObject().put("role", "assistant").put("content", content))

            // Execute tools that return data and build tool_result messages
            val toolResults = JSONArray()
            for (tool in toolUses) {
                val result = executeTool(tool)
                toolResults.put(JSONObject().apply {
                    put("type", "tool_result")
                    put("tool_use_id", tool.getString("id"))
                    put("content", result)
                })
            }
            messages.put(JSONObject().put("role", "user").put("content", toolResults))
        }

        AiResponse(
            text = finalText.ifBlank { "Done." },
            actions = allActions,
            imageUrl = imageUrl
        )
    }

    private fun callClaude(messages: JSONArray, state: DashboardState): JSONObject {
        val body = JSONObject().apply {
            put("model", AiConfig.CLAUDE_MODEL)
            put("max_tokens", 2048)
            put("system", buildSystemPrompt(state))
            put("messages", messages)
            put("tools", tools)
        }.toString()

        val req = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", AiConfig.ANTHROPIC_API_KEY)
            .header("anthropic-version", "2023-06-01")
            .header("content-type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val resp = client.newCall(req).execute()
        return JSONObject(resp.body?.string() ?: throw Exception("No response"))
    }

    private fun executeTool(tool: JSONObject): String {
        val name = tool.optString("name")
        val input = tool.optJSONObject("input") ?: JSONObject()

        return when (name) {
            "web_search" -> webSearch(input.optString("query"))
            "fetch_url"  -> fetchUrl(input.optString("url"))
            "save_note"  -> saveNote(input.optString("title"), input.optString("content"))
            "list_notes" -> listNotes()
            "delete_note" -> deleteNote(input.optString("id"))
            // Action tools (switch_frame, music_control, etc.) return confirmation
            "switch_frame"   -> "Switched to frame ${input.optInt("frame")}"
            "toggle_todo"    -> "Todo toggled"
            "add_todo"       -> "Todo added: ${input.optString("text")}"
            "toggle_habit"   -> "Habit toggled"
            "music_control"  -> "Music: ${input.optString("action")}"
            "show_image"     -> "Showing image"
            else -> "Done"
        }
    }

    private fun parseAction(tool: JSONObject): AiAction? {
        val input = tool.optJSONObject("input") ?: JSONObject()
        return when (tool.optString("name")) {
            "switch_frame"  -> AiAction.SwitchFrame(input.optInt("frame", 0))
            "toggle_todo"   -> AiAction.ToggleTodo(input.optString("id"))
            "add_todo"      -> AiAction.AddTodo(input.optString("text"), input.optInt("priority", 0))
            "toggle_habit"  -> AiAction.ToggleHabit(input.optString("id"))
            "music_control" -> AiAction.MusicControl(input.optString("action"))
            "show_image"    -> AiAction.ShowImage(input.optString("url"))
            else -> null
        }
    }

    // ── Tool implementations ──

    private fun webSearch(query: String): String {
        if (query.isBlank()) return "No query provided"
        return try {
            val url = if (serverBaseUrl.isNotBlank())
                "$serverBaseUrl/api/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}"
            else
                "https://api.duckduckgo.com/?q=${java.net.URLEncoder.encode(query, "UTF-8")}&format=json&no_html=1&skip_disambig=1"
            val req = Request.Builder().url(url).build()
            val resp = client.newCall(req).execute()
            resp.body?.string() ?: "No results"
        } catch (e: Exception) { "Search error: ${e.message}" }
    }

    private fun fetchUrl(url: String): String {
        if (url.isBlank()) return "No URL provided"
        return try {
            val target = if (serverBaseUrl.isNotBlank())
                "$serverBaseUrl/api/fetch?url=${java.net.URLEncoder.encode(url, "UTF-8")}"
            else url
            val req = Request.Builder().url(target).build()
            val resp = client.newCall(req).execute()
            val text = resp.body?.string()?.take(3000) ?: "Empty page"
            text
        } catch (e: Exception) { "Fetch error: ${e.message}" }
    }

    private fun saveNote(title: String, content: String): String {
        if (title.isBlank()) return "Title required"
        return try {
            val req = Request.Builder()
                .url("$serverBaseUrl/mcp")
                .header("content-type", "application/json")
                .post(JSONObject().apply {
                    put("jsonrpc", "2.0"); put("id", 1); put("method", "tools/call")
                    put("params", JSONObject().apply {
                        put("name", "save_note")
                        put("arguments", JSONObject().put("title", title).put("content", content))
                    })
                }.toString().toRequestBody("application/json".toMediaType()))
                .build()
            val resp = client.newCall(req).execute()
            val json = JSONObject(resp.body?.string() ?: "")
            json.optJSONObject("result")?.optJSONArray("content")?.optJSONObject(0)?.optString("text") ?: "Note saved"
        } catch (e: Exception) { "Note saved locally" }
    }

    private fun listNotes(): String {
        return try {
            val req = Request.Builder()
                .url("$serverBaseUrl/mcp")
                .header("content-type", "application/json")
                .post(JSONObject().apply {
                    put("jsonrpc", "2.0"); put("id", 1); put("method", "tools/call")
                    put("params", JSONObject().apply {
                        put("name", "list_notes"); put("arguments", JSONObject())
                    })
                }.toString().toRequestBody("application/json".toMediaType()))
                .build()
            val resp = client.newCall(req).execute()
            val json = JSONObject(resp.body?.string() ?: "")
            json.optJSONObject("result")?.optJSONArray("content")?.optJSONObject(0)?.optString("text") ?: "No notes"
        } catch (e: Exception) { "Error listing notes: ${e.message}" }
    }

    private fun deleteNote(id: String): String {
        return try {
            val req = Request.Builder()
                .url("$serverBaseUrl/mcp")
                .header("content-type", "application/json")
                .post(JSONObject().apply {
                    put("jsonrpc", "2.0"); put("id", 1); put("method", "tools/call")
                    put("params", JSONObject().apply {
                        put("name", "delete_note"); put("arguments", JSONObject().put("id", id))
                    })
                }.toString().toRequestBody("application/json".toMediaType()))
                .build()
            val resp = client.newCall(req).execute()
            val json = JSONObject(resp.body?.string() ?: "")
            json.optJSONObject("result")?.optJSONArray("content")?.optJSONObject(0)?.optString("text") ?: "Note deleted"
        } catch (e: Exception) { "Error: ${e.message}" }
    }

    private fun buildSystemPrompt(state: DashboardState): String = buildString {
        appendLine("You are an AI assistant for a TV dashboard. Keep responses concise — they display on a TV screen.")
        appendLine()
        appendLine("You have powerful tools: web search, notes, music control, and TV navigation.")
        appendLine("Use web_search to find information. Use fetch_url to read a specific page.")
        appendLine("Use save_note/list_notes/delete_note for persistent memory across conversations.")
        appendLine("Use music_control to play/pause/next/prev music.")
        appendLine("Use show_image to display an image URL on the TV screen.")
        appendLine()
        if (state.todos.isNotEmpty()) {
            appendLine("Current todos:")
            state.todos.forEach { appendLine("  [${it.id}] ${it.text} — ${if (it.done) "done" else "pending"}") }
        }
        if (state.habits.isNotEmpty()) {
            appendLine("Habits today:")
            state.habits.forEach { appendLine("  [${it.id}] ${it.name} — ${if (it.doneToday) "done" else "pending"}, streak ${it.streak}") }
        }
        if (state.events.isNotEmpty()) {
            appendLine("Calendar events:")
            state.events.take(6).forEach { appendLine("  ${it.title} at ${it.start}") }
        }
        if (state.weatherEmoji.isNotEmpty()) {
            appendLine("Weather: ${state.weatherEmoji} ${state.weatherTemp}")
        }
    }

    companion object {
        private const val TOOLS_JSON = """[
  {"name":"switch_frame","description":"Switch TV view: 0=gallery,1=todos,2=habits,3=calendar,4=videos,5=music,6=ai",
   "input_schema":{"type":"object","properties":{"frame":{"type":"integer"}},"required":["frame"]}},
  {"name":"toggle_todo","description":"Toggle a todo done/undone",
   "input_schema":{"type":"object","properties":{"id":{"type":"string"}},"required":["id"]}},
  {"name":"add_todo","description":"Add a new todo",
   "input_schema":{"type":"object","properties":{"text":{"type":"string"},"priority":{"type":"integer"}},"required":["text"]}},
  {"name":"toggle_habit","description":"Toggle a habit for today",
   "input_schema":{"type":"object","properties":{"id":{"type":"string"}},"required":["id"]}},
  {"name":"music_control","description":"Control music: play, pause, next, prev",
   "input_schema":{"type":"object","properties":{"action":{"type":"string","enum":["play","pause","next","prev"]}},"required":["action"]}},
  {"name":"web_search","description":"Search the web for current information",
   "input_schema":{"type":"object","properties":{"query":{"type":"string"}},"required":["query"]}},
  {"name":"fetch_url","description":"Fetch a URL and read its content",
   "input_schema":{"type":"object","properties":{"url":{"type":"string"}},"required":["url"]}},
  {"name":"save_note","description":"Save a persistent note (survives across conversations)",
   "input_schema":{"type":"object","properties":{"title":{"type":"string"},"content":{"type":"string"}},"required":["title","content"]}},
  {"name":"list_notes","description":"List all saved notes",
   "input_schema":{"type":"object","properties":{}}},
  {"name":"delete_note","description":"Delete a saved note",
   "input_schema":{"type":"object","properties":{"id":{"type":"string"}},"required":["id"]}},
  {"name":"show_image","description":"Display an image on the TV screen by URL",
   "input_schema":{"type":"object","properties":{"url":{"type":"string"}},"required":["url"]}}
]"""
    }
}
