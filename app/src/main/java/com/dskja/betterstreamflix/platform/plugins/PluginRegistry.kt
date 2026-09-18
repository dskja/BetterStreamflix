package com.dskja.betterstreamflix.platform.plugins

import com.dskja.betterstreamflix.providers.Provider
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
            val plugin = BuiltinProviderPlugin(provider, support)
            plugins.putIfAbsent(plugin.manifest.id, plugin)
        }
    }

    fun register(plugin: SourcePlugin) {
        plugins[plugin.manifest.id] = plugin
    }

    fun unregister(id: String) {
        plugins.remove(id)
    }

    fun get(id: String): SourcePlugin? = plugins[id]

    fun all(): List<SourcePlugin> = plugins.values.sortedBy { it.manifest.name.lowercase() }

    fun enabledProviders(): List<Provider> =
        all().filter { it.isEnabled() }.map { it.createProvider() }

    fun findByProviderName(name: String): SourcePlugin? =
        plugins.values.firstOrNull { it.manifest.name == name }
}
