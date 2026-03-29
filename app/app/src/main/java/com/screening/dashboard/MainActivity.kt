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

class MainActivity : ComponentActivity() {

    private val app by lazy { application as DashboardApp }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        intent.getStringExtra("server_host")?.let { host ->
            val port = intent.getIntExtra("server_port", 9900)
            app.updateServerUrl(host, port)
        }

        app.ensureConnected()

        setContent {
            val state by app.repository.state.collectAsState()

            // Start music when tracks arrive
            LaunchedEffect(state.music) {
                if (state.music.isNotEmpty()) {
                    MusicService.start(
                        this@MainActivity,
                        app.serverBaseUrl,
                        state.music
                    )
                }
            }

            DashboardScreen(
                state = state.copy(serverBaseUrl = app.serverBaseUrl),
                imageLoader = app.imageLoader,
                onToggleTodo = { id -> app.repository.toggleTodo(id) },
                onScreenShareStop = { app.repository.sendScreenShareStop() }
            )
        }
    }
}
