package com.dskja.betterstreamflix.platform.plugins

import com.dskja.betterstreamflix.providers.Provider

/**
 * Marketplace contract: a catalog/source that can expose a [Provider].
 * Dynamic APK plugins will implement this; builtins wrap existing Provider objects.
 */
interface SourcePlugin {
    val manifest: PluginManifest
    fun createProvider(): Provider
    fun isEnabled(): Boolean = true
}
