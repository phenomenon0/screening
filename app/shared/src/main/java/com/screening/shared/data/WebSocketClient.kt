package com.screening.shared.data

import android.util.Log
import com.screening.shared.model.ClientMessage
import com.screening.shared.model.ServerMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.*
import java.util.concurrent.atomic.AtomicBoolean

class WebSocketClient(private val serverUrl: String) {

    private val TAG = "WebSocketClient"
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var webSocket: WebSocket? = null
    private var backoffMs = 1000L
    private val maxBackoffMs = 30_000L
    private val running = AtomicBoolean(false)

    private val _messages = MutableSharedFlow<ServerMessage>(replay = 16, extraBufferCapacity = 64)
    val messages: SharedFlow<ServerMessage> = _messages

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    fun connect() {
        if (running.compareAndSet(false, true)) {
            scope.launch { doConnect() }
        }
    }

    private fun doConnect() {
        if (!running.get()) return
        val request = Request.Builder().url(serverUrl).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                _connected.value = true
                backoffMs = 1000L
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val msg = json.decodeFromString<ServerMessage>(text)
                    _messages.tryEmit(msg)
                } catch (_: Exception) {}
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _connected.value = false
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _connected.value = false
                scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        if (!running.get()) return
        scope.launch {
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(maxBackoffMs)
            doConnect()
        }
    }

    fun send(msg: ClientMessage) {
        try {
            val text = json.encodeToString(msg)
            webSocket?.send(text)
        } catch (_: Exception) {}
    }

    fun sendRaw(text: String) {
        webSocket?.send(text)
    }

    fun disconnect() {
        running.set(false)
        webSocket?.close(1000, "App closing")
    }
}
