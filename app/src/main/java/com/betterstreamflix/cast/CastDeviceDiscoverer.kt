package com.betterstreamflix.cast

import android.content.Context
import android.net.wifi.WifiManager
import android.text.format.Formatter

/**
 * Cast device discoverer — discovers casting devices on the local
 * network using mDNS/SSDP.
 */
object CastDeviceDiscoverer {

    private val discoveredDevices = mutableListOf<CastManager.CastDevice>()
    private var isDiscovering = false

    /**
     * Start device discovery.
     */
    fun startDiscovery() {
        if (isDiscovering) return
        isDiscovering = true
        // In real implementation, would start mDNS/SSDP discovery
    }

    /**
     * Stop device discovery.
     */
    fun stopDiscovery() {
        isDiscovering = false
    }

    /**
     * Get discovered devices.
     */
    fun getDiscoveredDevices(): List<CastManager.CastDevice> {
        return discoveredDevices.toList()
    }

    /**
     * Check if currently discovering.
     */
    fun isDiscovering(): Boolean = isDiscovering

    /**
     * Clear discovered devices.
     */
    fun clearDevices() {
        discoveredDevices.clear()
    }

    /**
     * Get the local IP address.
     */
    fun getLocalIpAddress(context: Context): String? {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            ?: return null
        @Suppress("DEPRECATION")
        val ip = wifiManager.connectionInfo.ipAddress
        if (ip == 0) return null
        @Suppress("DEPRECATION")
        return Formatter.formatIpAddress(ip)
    }

    /**
     * Check if on same network as a device.
     */
    fun isOnSameNetwork(context: Context, device: CastManager.CastDevice): Boolean {
        val localIp = getLocalIpAddress(context) ?: return false
        val localPrefix = localIp.substringBeforeLast(".")
        val devicePrefix = device.address.substringBeforeLast(".")
        return localPrefix == devicePrefix
    }
}
