package com.screening.dashboard

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import com.screening.dashboard.data.MusicService
import com.screening.dashboard.ui.DashboardScreen
import com.screening.dashboard.ui.SetupScreen

class MainActivity : ComponentActivity() {

    private val app by lazy { application as DashboardApp }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Allow intent extras to override (for ADB testing)
        intent.getStringExtra("server_host")?.let { host ->
            val port = intent.getIntExtra("server_port", 9900)
            app.saveAndConnect(host, port)
        }

        app.ensureConnected()

        setContent {
            val setupComplete by app.setupComplete.collectAsState()

            if (!setupComplete) {
                SetupScreen(
                    defaultIp = app.savedHost,
                    onConnect = { host, port -> app.saveAndConnect(host, port) },
                    onScan = { app.retryScan() }
                )
            } else {
                val state by app.repository.state.collectAsState()

                DashboardScreen(
                    state = state.copy(serverBaseUrl = app.serverBaseUrl),
                    imageLoader = app.imageLoader,
                    onToggleTodo = { id -> app.repository.toggleTodo(id) },
                    onToggleHabit = { id -> app.repository.toggleHabit(id) },
                    onAddTodo = { text, priority -> app.repository.addTodo(text, priority) },
                    onScreenShareStop = { app.repository.screenShareStop() },
                    onClearForceFrame = { app.repository.clearForceFrame() },
                    onClearAlarm = { app.repository.clearAlarm() }
                )
            }
        }
    }
}
