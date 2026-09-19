package com.dskja.betterstreamflix.platform.plugins

import com.dskja.betterstreamflix.adapters.AppAdapter
import com.dskja.betterstreamflix.models.Category
import com.dskja.betterstreamflix.models.Movie
import com.dskja.betterstreamflix.models.Video
import com.dskja.betterstreamflix.providers.Provider
import com.dskja.betterstreamflix.utils.UserPreferences

/**
 * Built-in demo addon that ships with the app to showcase the plugin extension surface
 * (home + search + metadata + playback + settings) without requiring a separate APK.
 */
object DemoAddonPlugin :
    BetterStreamflixPlugin,
    HomeContributionPlugin,
    SearchContributionPlugin,
    MetadataEnrichmentPlugin,
    PlaybackHookPlugin,
    SettingsContributionPlugin {

    const val ID = "builtin:demo-addon"

    private var host: PluginHost = DefaultPluginHost
    @Volatile
    private var lastServer: String? = null
    @Volatile
    private var playCount: Int = 0

    override val pluginId: String = ID

    override val manifest = PluginManifest(
        id = ID,
        name = "Demo Addon",
        version = "1.1.0",
        language = "en",
        author = "BetterStreamflix",
        description = "Built-in sample addon: home tip, search hint, metadata tag, " +
            "playback hooks and settings actions. Toggle in Settings → Sources.",
        source = PluginManifest.Source.BUILTIN,
        capabilities = PluginManifest.Capabilities(
            movies = false,
            tvShows = false,
            live = false,
            selfHosted = false,
            requiresAuth = false,
            homeContribution = true,
            searchContribution = true,
            metadataEnrichment = true,
            playbackHooks = true,
            extractors = false,
            subtitles = false,
            settings = true,
        ),
        minApiVersion = PluginApi.VERSION,
    )

    override fun createProvider(): Provider {
        error("Demo Addon does not expose a Provider — it contributes extension hooks only")
    }

    override fun isEnabled(): Boolean =
        runCatching { !UserPreferences.isPluginDisabled(ID) }.getOrDefault(true)

    override fun onAttach(host: PluginHost) {
        this.host = host
        host.log("DemoAddon", "attached api=${host.apiVersion()}")
    }

    override fun onDetach() {
        host.log("DemoAddon", "detached")
    }

    override suspend fun homeCategories(provider: Provider): List<Category> {
        if (!isEnabled()) return emptyList()
        val tip = Movie(
            id = "demo-addon-tip",
            title = "Plugin system ready",
            overview = "Demo Addon is active for ${provider.name}. " +
                "Install LOCAL SourcePlugin APKs under Settings → Sources, or disable this tip there.",
            poster = null,
            banner = null,
        )
        return listOf(
            Category(
                name = "BetterStreamflix Addons",
                list = listOf(tip),
            ),
        )
    }

    override suspend fun search(
        provider: Provider,
        query: String,
        page: Int,
    ): List<AppAdapter.Item> {
        if (!isEnabled() || page > 1 || query.isBlank()) return emptyList()
        if (!query.equals("addon", ignoreCase = true) &&
            !query.contains("plugin", ignoreCase = true)
        ) {
            return emptyList()
        }
        return listOf(
            Movie(
                id = "demo-addon-search",
                title = "Demo Addon · $query",
                overview = "Search contribution from Demo Addon on ${provider.name}.",
                poster = null,
                banner = null,
            ).also { it.providerName = "Demo Addon" },
        )
    }

    override suspend fun enrichMovie(provider: Provider, movie: Movie): Movie {
        if (!isEnabled()) return movie
        if (movie.overview.isNullOrBlank()) {
            movie.overview = "Enriched by Demo Addon on ${provider.name}."
        } else if (movie.overview?.contains("Demo Addon") != true) {
            movie.overview = "${movie.overview}\n\n— Demo Addon"
        }
        return movie
    }

    override fun onPlaybackStarted(videoType: Video.Type, serverName: String?) {
        lastServer = serverName
        playCount += 1
        host.log("DemoAddon", "playback started #$playCount server=$serverName type=$videoType")
    }

    override fun onPlaybackEnded(videoType: Video.Type) {
        host.log("DemoAddon", "playback ended lastServer=$lastServer")
    }

    override fun onServerResolved(server: Video.Server) {
        host.log("DemoAddon", "server resolved name=${server.name}")
    }

    override fun onPlaybackProgress(videoType: Video.Type, positionMs: Long, durationMs: Long) {
        if (durationMs > 0 && positionMs > 0 && positionMs % 60_000L < 1_000L) {
            host.log("DemoAddon", "progress ${positionMs / 1000}s / ${durationMs / 1000}s")
        }
    }

    override fun settingsSummary(): String =
        if (isEnabled()) "Demo Addon · plays=$playCount · last=${lastServer ?: "—"}"
        else "Demo Addon hidden"

    override fun settingsActions(): List<PluginSettingsAction> = listOf(
        PluginSettingsAction(
            id = "reset_play_count",
            label = "Reset play counter",
            description = "Clears Demo Addon playback stats",
        ),
    )

    fun handleSettingsAction(actionId: String): Boolean {
        if (actionId == "reset_play_count") {
            playCount = 0
            lastServer = null
            host.log("DemoAddon", "play counter reset")
            return true
        }
        return false
    }

    fun register() {
        PluginRegistry.register(this)
    }
}
