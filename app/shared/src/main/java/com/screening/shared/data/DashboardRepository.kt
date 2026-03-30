package com.screening.shared.data

import com.screening.shared.model.ClientMessage
import com.screening.shared.model.DashboardState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DashboardRepository(private val wsClient: WebSocketClient) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state

    init {
        scope.launch {
            wsClient.messages.collect { msg ->
                _state.update { current ->
                    when (msg.type) {
                        "calendar_sync" -> current.copy(events = msg.events ?: emptyList())
                        "todo_sync" -> current.copy(todos = msg.items ?: emptyList())
                        "image_sync" -> current.copy(images = msg.images ?: emptyList())
                        "video_sync" -> current.copy(videos = msg.videos ?: emptyList())
                        "music_sync" -> current.copy(music = msg.music ?: emptyList())
                        "habit_sync" -> current.copy(habits = msg.habits ?: emptyList())
                        "pomodoro_sync" -> current.copy(pomodoro = msg.pomodoro)
                        "devices_sync" -> current.copy(devices = msg.devices ?: emptyList())
                        "screen_share_active" -> current.copy(screenShareUrl = msg.url)
                        "screen_share_stop" -> current.copy(screenShareUrl = null)
                        "frame_change" -> current.copy(forceFrame = msg.frame)
                        "ping" -> {
                            wsClient.send(ClientMessage(type = "pong"))
                            current
                        }
                        else -> current
                    }
                }
            }
        }

        scope.launch {
            wsClient.connected.collect { connected ->
                _state.update { current -> current.copy(connected = connected) }
            }
        }
    }

    fun toggleTodo(id: String) = wsClient.send(ClientMessage(type = "todo_toggle", id = id))
    fun addTodo(text: String, priority: Int) = wsClient.send(ClientMessage(type = "todo_add", text = text, priority = priority))
    fun deleteTodo(id: String) = wsClient.send(ClientMessage(type = "todo_delete", id = id))
    fun toggleHabit(id: String) = wsClient.send(ClientMessage(type = "habit_toggle", id = id))
    fun addHabit(name: String) = wsClient.send(ClientMessage(type = "habit_add", name = name))
    fun deleteHabit(id: String) = wsClient.send(ClientMessage(type = "habit_delete", id = id))
    fun pomodoroStart() = wsClient.send(ClientMessage(type = "pomodoro_start"))
    fun pomodoroPause() = wsClient.send(ClientMessage(type = "pomodoro_pause"))
    fun pomodoroReset() = wsClient.send(ClientMessage(type = "pomodoro_reset"))
    fun pomodoroSet(minutes: Int) = wsClient.send(ClientMessage(type = "pomodoro_set", minutes = minutes))
    fun changeFrame(frame: Int, target: String? = null) = wsClient.send(ClientMessage(type = "frame_change", frame = frame, target = target))
    fun screenShareStop() = wsClient.send(ClientMessage(type = "screen_share_stop"))
    fun clearForceFrame() { _state.update { it.copy(forceFrame = null) } }

    fun sendRaw(json: String) {
        try { wsClient.sendRaw(json) } catch (_: Exception) {}
    }

    fun register(deviceType: String, deviceName: String) {
        wsClient.send(ClientMessage(type = "register", deviceType = deviceType, deviceName = deviceName))
    }

    fun close() {
        scope.cancel()
    }
}
