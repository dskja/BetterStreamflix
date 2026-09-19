package com.dskja.betterstreamflix.platform.plugins

import com.dskja.betterstreamflix.providers.Provider

class BuiltinProviderPlugin(
    private val provider: Provider,
    private val support: Provider.Companion.ProviderSupport,
) : SourcePlugin {
    override val manifest = PluginManifest(
        id = "builtin:${provider.name}",
        name = provider.name,
        version = "1.0.0",
        language = provider.language,
        description = "Built-in provider",
        source = PluginManifest.Source.BUILTIN,
        capabilities = PluginManifest.Capabilities(
            movies = support.movies,
            tvShows = support.tvShows,
        ),
    )

    override fun createProvider(): Provider = provider
}
