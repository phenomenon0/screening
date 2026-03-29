package com.screening.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.screening.mobile.ui.MobileUI
import com.screening.mobile.ui.SetupScreen

class MainActivity : ComponentActivity() {

    private val app by lazy { application as MobileApp }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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
                MobileUI(
                    state = state.copy(serverBaseUrl = app.serverBaseUrl),
                    repo = app.repository,
                    imageLoader = app.imageLoader,
                    serverBaseUrl = app.serverBaseUrl
                )
            }
        }
    }
}
