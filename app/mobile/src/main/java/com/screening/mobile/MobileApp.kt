package com.screening.mobile

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.screening.shared.data.DashboardRepository
import com.screening.shared.data.ServerDiscovery
import com.screening.shared.data.WebSocketClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

class MobileApp : Application() {

    companion object {
        private const val PREFS = "screening_mobile"
        private const val KEY_HOST = "server_host"
        private const val KEY_PORT = "server_port"
    }

    lateinit var wsClient: WebSocketClient private set
    lateinit var repository: DashboardRepository private set
    lateinit var imageLoader: ImageLoader private set
    lateinit var discovery: ServerDiscovery private set

    var serverBaseUrl: String = "" ; private set

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val _setupComplete = MutableStateFlow(false)
    val setupComplete: StateFlow<Boolean> = _setupComplete

    val savedHost: String
        get() = getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_HOST, "") ?: ""

    override fun onCreate() {
        super.onCreate()
        imageLoader = ImageLoader.Builder(this)
            .components { add(OkHttpNetworkFetcherFactory()) }
            .build()
        discovery = ServerDiscovery(this)

        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val host = prefs.getString(KEY_HOST, null)
        val port = prefs.getInt(KEY_PORT, 9900)

        if (host != null) {
            connectTo(host, port)
            _setupComplete.value = true
        } else {
            // Try mDNS
            connectTo("127.0.0.1", 9900) // placeholder
            scope.launch {
                discovery.startDiscovery()
                try {
                    val (h, p) = withTimeout(5000) { discovery.serverAddress.filterNotNull().first() }
                    saveAndConnect(h, p)
                } catch (_: TimeoutCancellationException) {}
                discovery.stopDiscovery()
            }
        }
    }

    fun connectTo(host: String, port: Int) {
        if (::wsClient.isInitialized) wsClient.disconnect()
        serverBaseUrl = "http://$host:$port"
        wsClient = WebSocketClient("ws://$host:$port/ws")
        repository = DashboardRepository(wsClient)
        wsClient.connect()
    }

    fun saveAndConnect(host: String, port: Int = 9900) {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_HOST, host).putInt(KEY_PORT, port).apply()
        connectTo(host, port)
        _setupComplete.value = true
    }

    fun retryScan() {
        scope.launch {
            discovery.startDiscovery()
            try {
                val (h, p) = withTimeout(8000) { discovery.serverAddress.filterNotNull().first() }
                saveAndConnect(h, p)
            } catch (_: TimeoutCancellationException) {}
            discovery.stopDiscovery()
        }
    }
}
