package com.screening.dashboard

import android.app.Application
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.screening.dashboard.data.DashboardRepository
import com.screening.dashboard.data.ServerDiscovery
import com.screening.dashboard.data.WebSocketClient
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

class DashboardApp : Application() {

    // Fallback if mDNS discovery fails
    private val fallbackHost = "10.0.0.101"
    private val fallbackPort = 9900

    lateinit var wsClient: WebSocketClient
        private set
    lateinit var repository: DashboardRepository
        private set
    lateinit var imageLoader: ImageLoader
        private set
    lateinit var discovery: ServerDiscovery
        private set

    var serverBaseUrl: String = "http://$fallbackHost:$fallbackPort"
        private set

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()

        imageLoader = ImageLoader.Builder(this)
            .components { add(OkHttpNetworkFetcherFactory()) }
            .build()

        discovery = ServerDiscovery(this)

        // Start with fallback, then upgrade if mDNS finds the server
        connectTo(fallbackHost, fallbackPort)

        scope.launch {
            discovery.startDiscovery()
            try {
                val (host, port) = withTimeout(5000) {
                    discovery.serverAddress.filterNotNull().first()
                }
                // Found via mDNS — reconnect to discovered address
                if (host != fallbackHost || port != fallbackPort) {
                    connectTo(host, port)
                }
            } catch (_: TimeoutCancellationException) {
                // mDNS didn't find anything in 5s, stick with fallback
            }
            discovery.stopDiscovery()
        }
    }

    private fun connectTo(host: String, port: Int) {
        if (::wsClient.isInitialized) {
            wsClient.disconnect()
        }
        serverBaseUrl = "http://$host:$port"
        wsClient = WebSocketClient("ws://$host:$port/ws")
        repository = DashboardRepository(wsClient)
        wsClient.connect()
    }

    fun ensureConnected() {
        wsClient.connect()
    }

    fun updateServerUrl(host: String, port: Int = 9900) {
        connectTo(host, port)
    }
}
