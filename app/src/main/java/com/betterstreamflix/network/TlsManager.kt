package com.betterstreamflix.network

import java.util.concurrent.ConcurrentHashMap

/**
 * SSL/TLS manager — manages certificate pinning, TLS version
 * preferences, and SSL configuration.
 */
object TlsManager {

    private val pinnedHosts = ConcurrentHashMap<String, List<String>>()

    /**
     * Pin certificates for a host.
     */
    fun pinHost(host: String, pins: List<String>) {
        pinnedHosts[host] = pins
    }

    /**
     * Remove pins for a host.
     */
    fun unpinHost(host: String) {
        pinnedHosts.remove(host)
    }

    /**
     * Get pins for a host.
     */
    fun getPins(host: String): List<String>? = pinnedHosts[host]

    /**
     * Get all pinned hosts.
     */
    fun getAllPinnedHosts(): Map<String, List<String>> = pinnedHosts.toMap()

    /**
     * Check if a host is pinned.
     */
    fun isHostPinned(host: String): Boolean = pinnedHosts.containsKey(host)

    /**
     * Clear all pins.
     */
    fun clearAllPins() {
        pinnedHosts.clear()
    }

    /**
     * Get the recommended TLS version for the current Android version.
     */
    fun getRecommendedTlsVersion(): String {
        return when {
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q -> "TLSv1.3"
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN -> "TLSv1.2"
            else -> "TLSv1.1"
        }
    }

    /**
     * Check if TLS 1.3 is supported.
     */
    fun isTls13Supported(): Boolean {
        return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q
    }
}
