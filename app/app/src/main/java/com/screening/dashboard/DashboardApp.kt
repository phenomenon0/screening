package com.screening.dashboard

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.screening.shared.data.DashboardRepository
import com.screening.dashboard.data.DashboardService
import com.screening.shared.data.ServerDiscovery
import com.screening.shared.data.WebSocketClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

class DashboardApp : Application() {

    companion object {
        private const val PREFS_NAME = "screening_prefs"
        private const val KEY_SERVER_HOST = "server_host"
        private const val KEY_SERVER_PORT = "server_port"
        private const val FALLBACK_HOST = "10.0.0.101"
        private const val FALLBACK_PORT = 9900
    }

    lateinit var wsClient: WebSocketClient
        private set
    lateinit var repository: DashboardRepository
        private set
    lateinit var imageLoader: ImageLoader
        private set
    lateinit var discovery: ServerDiscovery
        private set

    var serverBaseUrl: String = ""
        private set

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _setupComplete = MutableStateFlow(false)
    val setupComplete: StateFlow<Boolean> = _setupComplete

    val savedHost: String
        get() = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SERVER_HOST, "") ?: ""

    override fun onCreate() {
        super.onCreate()

        imageLoader = ImageLoader.Builder(this)
            .components { add(OkHttpNetworkFetcherFactory()) }
            .build()

        discovery = ServerDiscovery(this)

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedHost = prefs.getString(KEY_SERVER_HOST, null)
        val savedPort = prefs.getInt(KEY_SERVER_PORT, FALLBACK_PORT)

        if (savedHost != null) {
            // Have a saved server — connect immediately
            connectTo(savedHost, savedPort)
            _setupComplete.value = true
        } else {
            // No saved server — try mDNS discovery, fall back to setup screen
            initWithDiscovery()
        }

        // Start foreground service to prevent being killed
        DashboardService.start(this)
    }

    private fun initWithDiscovery() {
        // Initialize with a dummy connection that will be replaced
        connectTo(FALLBACK_HOST, FALLBACK_PORT)

        scope.launch {
            discovery.startDiscovery()
            try {
                val (host, port) = withTimeout(5000) {
                    discovery.serverAddress.filterNotNull().first()
                }
                saveServerAddress(host, port)
                connectTo(host, port)
                _setupComplete.value = true
            } catch (_: TimeoutCancellationException) {
                // mDNS failed — check if fallback works
                delay(2000)
                if (wsClient.connected.value) {
                    // Fallback IP works
                    saveServerAddress(FALLBACK_HOST, FALLBACK_PORT)
                    _setupComplete.value = true
                }
                // Otherwise setupComplete stays false → SetupScreen shown
            }
            discovery.stopDiscovery()
        }
    }

    fun connectTo(host: String, port: Int) {
        if (::wsClient.isInitialized) {
            wsClient.disconnect()
        }
        serverBaseUrl = "http://$host:$port"
        wsClient = WebSocketClient("ws://$host:$port/ws")
        repository = DashboardRepository(wsClient)
        wsClient.connect()
    }

    fun saveAndConnect(host: String, port: Int = FALLBACK_PORT) {
        saveServerAddress(host, port)
        connectTo(host, port)
        _setupComplete.value = true
    }

    private fun saveServerAddress(host: String, port: Int) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SERVER_HOST, host)
            .putInt(KEY_SERVER_PORT, port)
            .apply()
    }

    fun ensureConnected() {
        if (::wsClient.isInitialized) {
            wsClient.connect()
        }
    }

    fun retryScan() {
        scope.launch {
            discovery.startDiscovery()
            try {
                val (host, port) = withTimeout(8000) {
                    discovery.serverAddress.filterNotNull().first()
                }
                saveAndConnect(host, port)
            } catch (_: TimeoutCancellationException) {
                // Still no server found
            }
            discovery.stopDiscovery()
        }
    }
}
