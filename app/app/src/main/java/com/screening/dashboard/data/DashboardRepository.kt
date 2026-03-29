package com.screening.dashboard.data

import com.screening.dashboard.model.ClientMessage
import com.screening.dashboard.model.DashboardState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
                        "screen_share_active" -> current.copy(screenShareUrl = msg.url)
                        "screen_share_stop" -> current.copy(screenShareUrl = null)
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

    fun toggleTodo(id: String) {
        wsClient.send(ClientMessage(type = "todo_toggle", id = id))
    }

    fun sendScreenShareStop() {
        wsClient.send(ClientMessage(type = "screen_share_stop"))
    }
}
