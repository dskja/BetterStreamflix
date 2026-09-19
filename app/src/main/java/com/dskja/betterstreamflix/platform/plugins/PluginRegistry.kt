package com.dskja.betterstreamflix.platform.plugins

import com.dskja.betterstreamflix.providers.Provider
import com.dskja.betterstreamflix.utils.UserPreferences
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry for marketplace-ready source plugins.
 * Builtins mirror [Provider.providers]; remote/local plugins can be registered later
 * without changing the Provider companion map until activation.
 */
object PluginRegistry {
    private val plugins = ConcurrentHashMap<String, SourcePlugin>()

    val size: Int get() = plugins.size

    fun bootstrapBuiltins() {
        Provider.providers.forEach { (provider, support) ->
            val selfHosted = provider.name == "Jellyfin" || provider.name == "Plex"
            val plugin = object : SourcePlugin {
                private val builtin = BuiltinProviderPlugin(provider, support)
                override val manifest = builtin.manifest.copy(
                    capabilities = builtin.manifest.capabilities.copy(
                        selfHosted = selfHosted,
                        requiresAuth = selfHosted,
                        movies = support.movies,
                        tvShows = support.tvShows,
                    ),
                )
                override fun createProvider(): Provider = provider
                override fun isEnabled(): Boolean {
                    if (UserPreferences.isPluginDisabled(manifest.id)) return false
                    if (selfHosted) {
                        return when (provider.name) {
                            "Jellyfin" -> com.dskja.betterstreamflix.platform.jellyfin.JellyfinProvider.isConfigured()
                            "Plex" -> com.dskja.betterstreamflix.platform.plex.PlexProvider.isConfigured()
                            else -> true
                        }
                    }
                    return true
                }
            }
            plugins.putIfAbsent(plugin.manifest.id, plugin)
        }
    }

    fun register(plugin: SourcePlugin) {
        plugins[plugin.manifest.id] = plugin
    }

    fun unregister(id: String) {
        plugins.remove(id)
    }

    fun clear() {
        plugins.clear()
    }

    fun get(id: String): SourcePlugin? = plugins[id]

    fun all(): List<SourcePlugin> = plugins.values.sortedBy { it.manifest.name.lowercase() }

    fun enabledProviders(): List<Provider> =
        all().filter { it.isEnabled() }.map { it.createProvider() }

    fun findByProviderName(name: String): SourcePlugin? =
        plugins.values.firstOrNull { it.manifest.name == name }

    fun isProviderVisible(provider: Provider): Boolean {
        val plugin = findByProviderName(provider.name) ?: return true
        return plugin.isEnabled()
    }
}
