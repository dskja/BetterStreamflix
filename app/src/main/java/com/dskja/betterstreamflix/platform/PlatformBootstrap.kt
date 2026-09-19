package com.dskja.betterstreamflix.platform

import android.content.Context
import android.util.Log
import com.dskja.betterstreamflix.platform.plugins.PluginManager
import com.dskja.betterstreamflix.platform.plugins.PluginRegistry
import com.dskja.betterstreamflix.platform.trakt.TraktClient

/**
 * Cold-start wiring for Full Platform modules (plugins, Trakt, self-host, debrid, backends).
 * Safe to call multiple times; failures never abort app launch.
 */
object PlatformBootstrap {
    private const val TAG = "PlatformBootstrap"

    @Volatile
    private var started = false

    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        runCatching { PluginManager.start(app) }
            .onFailure { Log.w(TAG, "Plugin manager: ${it.message}") }
        runCatching { TraktClient.warm(app) }
            .onFailure { Log.w(TAG, "Trakt warm: ${it.message}") }
        runCatching { com.dskja.betterstreamflix.platform.simkl.SimklClient.warm() }
            .onFailure { Log.w(TAG, "Simkl warm: ${it.message}") }
        runCatching {
            val jf = IntegrationStatus.jellyfin()
            val px = IntegrationStatus.plex()
            val db = IntegrationStatus.debrid()
            val os = IntegrationStatus.openSubtitles()
            Log.i(
                TAG,
                "Integration snapshot trakt=${IntegrationStatus.trakt().level} " +
                    "jellyfin=${jf.level} plex=${px.level} debrid=${db.level} " +
                    "simkl=${IntegrationStatus.simkl().level} opensubs=${os.level} " +
                    "plugins=${PluginRegistry.size}",
            )
        }.onFailure { Log.w(TAG, "Integration snapshot: ${it.message}") }
        Log.i(TAG, "Full Platform modules ready (plugins=${PluginRegistry.size})")
    }
}
