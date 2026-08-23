package com.betterstreamflix.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.betterstreamflix.utils.NetworkObserver

/**
 * Network availability checker — provides synchronous and async network state.
 */
object NetworkAvailabilityChecker {

    /**
     * Check if the device has an active internet connection.
     */
    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Check if connected via WiFi.
     */
    fun isOnWifi(context: Context): Boolean {
        return NetworkObserver.getConnectionType(context) is com.betterstreamflix.utils.ConnectionType.Wifi
    }

    /**
     * Check if connected via cellular.
     */
    fun isOnCellular(context: Context): Boolean {
        return NetworkObserver.getConnectionType(context) is com.betterstreamflix.utils.ConnectionType.Cellular
    }

    /**
     * Check if on a metered connection.
     */
    fun isMetered(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }
}
