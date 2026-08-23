package com.betterstreamflix.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Network connectivity observer — provides reactive network state monitoring.
 */
object NetworkObserver {

    /**
     * Observe network connectivity changes as a Flow.
     * Emits true when online, false when offline.
     */
    fun observe(context: Context): Flow<Boolean> = callbackFlow {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager

        if (connectivityManager == null) {
            trySend(true)
            awaitClose { }
            return@callbackFlow
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(false)
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, callback)

        // Send initial state
        val currentNetwork = connectivityManager.activeNetwork
        val currentCapabilities = connectivityManager.getNetworkCapabilities(currentNetwork)
        trySend(currentCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ?: false)

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }

    /**
     * Get current connection type.
     */
    fun getConnectionType(context: Context): ConnectionType {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
            as? ConnectivityManager ?: return ConnectionType.None

        val network = connectivityManager.activeNetwork ?: return ConnectionType.None
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return ConnectionType.None

        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectionType.Wifi
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ConnectionType.Cellular
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ConnectionType.Ethernet
            else -> ConnectionType.Other
        }
    }
}

/**
 * Network connection types.
 */
sealed class ConnectionType {
    data object Wifi : ConnectionType()
    data object Cellular : ConnectionType()
    data object Ethernet : ConnectionType()
    data object Other : ConnectionType()
    data object None : ConnectionType()
}
