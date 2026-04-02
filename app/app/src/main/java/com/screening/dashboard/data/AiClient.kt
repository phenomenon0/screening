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
    val actions: List<AiAction> = emptyList()
)

sealed class AiAction {
    data class SwitchFrame(val frame: Int) : AiAction()
    data class ToggleTodo(val id: String) : AiAction()
    data class AddTodo(val text: String, val priority: Int = 0) : AiAction()
    data class ToggleHabit(val id: String) : AiAction()
}

class AiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val tools = JSONArray("""
    [
      {
        "name": "switch_frame",
        "description": "Switch the TV dashboard to a different view",
        "input_schema": {
          "type": "object",
          "properties": {
            "frame": {
              "type": "integer",
              "description": "0=gallery, 1=todos, 2=habits, 3=calendar, 4=videos, 5=music, 6=ai chat"
            }
          },
          "required": ["frame"]
        }
      },
      {
        "name": "toggle_todo",
        "description": "Mark a todo item as done or undone",
        "input_schema": {
          "type": "object",
          "properties": { "id": { "type": "string" } },
          "required": ["id"]
        }
      },
      {
        "name": "add_todo",
        "description": "Add a new todo item to the list",
        "input_schema": {
          "type": "object",
          "properties": {
            "text": { "type": "string" },
            "priority": { "type": "integer", "description": "0=low, 1=medium, 2=high" }
          },
          "required": ["text"]
        }
      },
      {
        "name": "toggle_habit",
        "description": "Check off or uncheck a habit for today",
        "input_schema": {
          "type": "object",
          "properties": { "id": { "type": "string" } },
          "required": ["id"]
        }
      }
    ]
    """.trimIndent())

    suspend fun ask(transcript: String, state: DashboardState): AiResponse = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("model", AiConfig.CLAUDE_MODEL)
            put("max_tokens", 1024)
            put("system", buildSystemPrompt(state))
            put("messages", JSONArray().put(JSONObject().apply {
                put("role", "user")
                put("content", transcript)
            }))
            put("tools", tools)
        }.toString()

        val req = Request.Builder()
            .url("https://api.anthropic.com/v1/messages")
            .header("x-api-key", AiConfig.ANTHROPIC_API_KEY)
            .header("anthropic-version", "2023-06-01")
            .header("content-type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        try {
            val resp = client.newCall(req).execute()
            val json = JSONObject(resp.body?.string() ?: return@withContext AiResponse("No response"))
            parseResponse(json)
        } catch (e: Exception) {
            AiResponse("Error: ${e.message?.take(80)}")
        }
    }

    private fun parseResponse(json: JSONObject): AiResponse {
        val content = json.optJSONArray("content") ?: return AiResponse("Empty response")
        var text = ""
        val actions = mutableListOf<AiAction>()
        for (i in 0 until content.length()) {
            val item = content.getJSONObject(i)
            when (item.optString("type")) {
                "text" -> text = item.optString("text")
                "tool_use" -> {
                    val input = item.optJSONObject("input") ?: JSONObject()
                    when (item.optString("name")) {
                        "switch_frame" -> actions.add(AiAction.SwitchFrame(input.optInt("frame", 0)))
                        "toggle_todo"  -> actions.add(AiAction.ToggleTodo(input.optString("id")))
                        "add_todo"     -> actions.add(AiAction.AddTodo(input.optString("text"), input.optInt("priority", 0)))
                        "toggle_habit" -> actions.add(AiAction.ToggleHabit(input.optString("id")))
                    }
                }
            }
        }
        if (text.isEmpty() && actions.isNotEmpty()) text = "Done."
        return AiResponse(text.ifBlank { "I didn't understand that." }, actions)
    }

    private fun buildSystemPrompt(state: DashboardState): String = buildString {
        appendLine("You are an AI assistant for a TV dashboard. Keep responses concise — they display on a TV screen. Max 2 sentences for pure answers.")
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
        appendLine()
        appendLine("Use tools to perform actions when the user asks. Otherwise just answer.")
    }
}
