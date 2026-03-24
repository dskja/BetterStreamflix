package com.streamflixreborn.streamflix.utils

import android.os.Build
import android.util.Log
import java.net.URI
import java.net.NetworkInterface

object BypassWebSocketEndpointHelper {

    private const val TAG = "BypassWSEndpoint"

    fun getAdvertisedWsUrl(port: Int): String? {
        val override = UserPreferences.bypassWsAdvertisedHost.trim()
        if (override.isNotEmpty()) {
            return normalizeOverride(override, port)
        }
        return getLocalIpv4Address()?.let { "ws://$it:$port" }
    }

    fun getLocalIpv4Address(): String? {
        return runCatching {
            NetworkInterface.getNetworkInterfaces()
                ?.toList()
                .orEmpty()
                .asSequence()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList().asSequence() }
                .mapNotNull { address ->
                    address.hostAddress
                        ?.takeIf { '.' in it && !it.startsWith("127.") }
                        ?.substringBefore('%')
                }
                .firstOrNull()
        }.getOrNull()
    }

    fun isProbablyEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT.lowercase()
        val model = Build.MODEL.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        return fingerprint.contains("generic")
            || fingerprint.contains("emulator")
            || model.contains("sdk")
            || model.contains("emulator")
            || manufacturer.contains("genymotion")
    }

    private fun normalizeOverride(rawValue: String, port: Int): String? {
        val value = rawValue.trim()
        val withScheme = if (value.startsWith("ws://") || value.startsWith("wss://")) {
            value
        } else {
            "ws://$value"
        }

        return runCatching {
            val uri = URI(withScheme)
            val host = uri.host ?: return null
            val scheme = uri.scheme ?: "ws"
            val resolvedPort = if (uri.port != -1) uri.port else port
            URI(scheme, null, host, resolvedPort, null, null, null).toString()
        }.onFailure {
            Log.e(TAG, "Invalid bypass advertised host override: $rawValue", it)
        }.getOrNull()
    }
}
