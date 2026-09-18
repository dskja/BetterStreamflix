package com.dskja.betterstreamflix.platform.plugins

/**
 * Marketplace-ready metadata for a source plugin.
 * Built-in providers are wrapped as manifests with [source] = BUILTIN.
 */
data class PluginManifest(
    val id: String,
    val name: String,
    val version: String,
    val language: String,
    val author: String = "BetterStreamflix",
    val description: String = "",
    val source: Source = Source.BUILTIN,
    val capabilities: Capabilities = Capabilities(),
) {
    enum class Source { BUILTIN, LOCAL, REMOTE }

    data class Capabilities(
        val movies: Boolean = true,
        val tvShows: Boolean = true,
        val live: Boolean = false,
        val selfHosted: Boolean = false,
        val requiresAuth: Boolean = false,
    )
}
