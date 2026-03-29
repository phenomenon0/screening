package com.screening.shared.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ServerDiscovery(context: Context) {

    private val TAG = "ServerDiscovery"
    private val SERVICE_TYPE = "_screening._tcp."
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    private val _serverAddress = MutableStateFlow<Pair<String, Int>?>(null)
    val serverAddress: StateFlow<Pair<String, Int>?> = _serverAddress

    private var discovering = false

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        override fun onDiscoveryStarted(serviceType: String) {}
        override fun onDiscoveryStopped(serviceType: String) {}

        override fun onServiceFound(service: NsdServiceInfo) {
            if (service.serviceName == "Screening") {
                nsdManager.resolveService(service, resolveListener)
            }
        }
        override fun onServiceLost(service: NsdServiceInfo) {}
    }

    private val resolveListener = object : NsdManager.ResolveListener {
        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
            val host = serviceInfo.host.hostAddress
            val port = serviceInfo.port
            if (host != null) _serverAddress.value = Pair(host, port)
        }
    }

    fun startDiscovery() {
        if (discovering) return
        discovering = true
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    fun stopDiscovery() {
        if (!discovering) return
        discovering = false
        try { nsdManager.stopServiceDiscovery(discoveryListener) } catch (_: Exception) {}
    }
}
