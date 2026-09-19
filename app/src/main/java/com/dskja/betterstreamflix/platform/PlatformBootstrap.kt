package com.dskja.betterstreamflix.platform

import android.content.Context
import android.util.Log
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
        runCatching { PluginRegistry.bootstrapBuiltins() }
            .onFailure { Log.w(TAG, "Plugin registry: ${it.message}") }
        runCatching {
            com.dskja.betterstreamflix.platform.plugins.PluginCatalog.registerLocalStubs(app)
        }.onFailure { Log.w(TAG, "Plugin catalog: ${it.message}") }
        runCatching {
            com.dskja.betterstreamflix.platform.plugins.PluginApkLoader.loadInstalled(app)
        }.onFailure { Log.w(TAG, "Plugin APK load: ${it.message}") }
        runCatching { TraktClient.warm(app) }
            .onFailure { Log.w(TAG, "Trakt warm: ${it.message}") }
        runCatching { com.dskja.betterstreamflix.platform.simkl.SimklClient.warm() }
            .onFailure { Log.w(TAG, "Simkl warm: ${it.message}") }
        Log.i(TAG, "Full Platform modules ready (plugins=${PluginRegistry.size})")
    }
}
