package com.betterstreamflix.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

/**
 * Network capability checker — provides detailed network capability
 * information for adaptive behavior.
 */
object NetworkCapabilityChecker {

    /**
     * Check if the network has internet capability.
     */
    fun hasInternet(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } else {
            @Suppress("DEPRECATION")
            val info = cm.activeNetworkInfo
            return info != null && info.isConnected
        }
    }

    /**
     * Get the current network type.
     */
    fun getNetworkType(context: Context): NetworkType {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return NetworkType.NONE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return NetworkType.NONE
            val capabilities = cm.getNetworkCapabilities(network) ?: return NetworkType.NONE

            return when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> NetworkType.VPN
                else -> NetworkType.OTHER
            }
        } else {
            @Suppress("DEPRECATION")
            val info = cm.activeNetworkInfo ?: return NetworkType.NONE
            return when (info.type) {
                ConnectivityManager.TYPE_WIFI -> NetworkType.WIFI
                ConnectivityManager.TYPE_MOBILE -> NetworkType.CELLULAR
                ConnectivityManager.TYPE_ETHERNET -> NetworkType.ETHERNET
                ConnectivityManager.TYPE_VPN -> NetworkType.VPN
                else -> NetworkType.OTHER
            }
        }
    }

    /**
     * Get the estimated bandwidth in Kbps.
     */
    fun getEstimatedBandwidthKbps(context: Context): Int {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return 0

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return 0
            val capabilities = cm.getNetworkCapabilities(network) ?: return 0
            return capabilities.linkDownstreamBandwidthKbps
        }
        return 0
    }

    /**
     * Check if on unmetered connection (WiFi/Ethernet).
     */
    fun isUnmetered(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        }
        return getNetworkType(context) == NetworkType.WIFI || getNetworkType(context) == NetworkType.ETHERNET
    }

    enum class NetworkType { WIFI, CELLULAR, ETHERNET, VPN, OTHER, NONE }
}
