package com.dskja.betterstreamflix.platform.plugins

import com.dskja.betterstreamflix.adapters.AppAdapter
import com.dskja.betterstreamflix.models.Category
import com.dskja.betterstreamflix.models.Movie
import com.dskja.betterstreamflix.models.TvShow
import com.dskja.betterstreamflix.models.Video
import com.dskja.betterstreamflix.providers.Provider
import com.dskja.betterstreamflix.utils.UserPreferences

/**
 * Facade around a DexClassLoader-instantiated [SourcePlugin] that:
 * - pins catalog id/name/version/source
 * - preserves optional extension interfaces via delegation
 * - honors disable toggles
 */
internal class LoadedPluginFacade(
    private val entry: PluginCatalog.Entry,
    private val loaded: SourcePlugin,
) : BetterStreamflixPlugin,
    HomeContributionPlugin,
    SearchContributionPlugin,
    MetadataEnrichmentPlugin,
    PlaybackHookPlugin,
    ExtractorContributionPlugin,
    SubtitleContributionPlugin,
    SettingsContributionPlugin {

    override val pluginId: String = entry.id.ifBlank { loaded.manifest.id }

    override val manifest: PluginManifest = loaded.manifest.copy(
        id = pluginId,
        name = entry.name.ifBlank { loaded.manifest.name },
        version = entry.version.ifBlank { loaded.manifest.version },
        description = entry.description.ifBlank { loaded.manifest.description },
        source = PluginManifest.Source.LOCAL,
        capabilities = mergeCapabilities(entry, loaded.manifest.capabilities),
    )

    override fun createProvider(): Provider = loaded.createProvider()

    override fun isEnabled(): Boolean =
        !UserPreferences.isPluginDisabled(manifest.id) && loaded.isEnabled()

    override fun onAttach(host: PluginHost) {
        (loaded as? BetterStreamflixPlugin)?.onAttach(host)
    }

    override fun onDetach() {
        (loaded as? BetterStreamflixPlugin)?.onDetach()
    }

    override suspend fun homeCategories(provider: Provider): List<Category> =
        (loaded as? HomeContributionPlugin)?.homeCategories(provider).orEmpty()

    override suspend fun search(
        provider: Provider,
        query: String,
        page: Int,
    ): List<AppAdapter.Item> =
        (loaded as? SearchContributionPlugin)?.search(provider, query, page).orEmpty()

    override suspend fun enrichMovie(provider: Provider, movie: Movie): Movie =
        (loaded as? MetadataEnrichmentPlugin)?.enrichMovie(provider, movie) ?: movie

    override suspend fun enrichTvShow(provider: Provider, tvShow: TvShow): TvShow =
        (loaded as? MetadataEnrichmentPlugin)?.enrichTvShow(provider, tvShow) ?: tvShow

    override fun onPlaybackStarted(videoType: Video.Type, serverName: String?) {
        (loaded as? PlaybackHookPlugin)?.onPlaybackStarted(videoType, serverName)
    }

    override fun onPlaybackEnded(videoType: Video.Type) {
        (loaded as? PlaybackHookPlugin)?.onPlaybackEnded(videoType)
    }

    override fun onServerResolved(server: Video.Server) {
        (loaded as? PlaybackHookPlugin)?.onServerResolved(server)
    }

    override fun onPlaybackProgress(videoType: Video.Type, positionMs: Long, durationMs: Long) {
        (loaded as? PlaybackHookPlugin)?.onPlaybackProgress(videoType, positionMs, durationMs)
    }

    override fun extractorNames(): List<String> =
        (loaded as? ExtractorContributionPlugin)?.extractorNames().orEmpty()

    override fun subtitleLanguages(): List<String> =
        (loaded as? SubtitleContributionPlugin)?.subtitleLanguages().orEmpty()

    override fun subtitleSourceLabel(): String =
        (loaded as? SubtitleContributionPlugin)?.subtitleSourceLabel() ?: pluginId

    override fun settingsSummary(): String =
        (loaded as? SettingsContributionPlugin)?.settingsSummary().orEmpty()

    override fun settingsActions(): List<PluginSettingsAction> =
        (loaded as? SettingsContributionPlugin)?.settingsActions().orEmpty()

    fun contributesHome(): Boolean =
        loaded is HomeContributionPlugin || manifest.capabilities.homeContribution

    fun contributesSearch(): Boolean =
        loaded is SearchContributionPlugin || manifest.capabilities.searchContribution

    fun contributesMetadata(): Boolean =
        loaded is MetadataEnrichmentPlugin || manifest.capabilities.metadataEnrichment

    fun contributesPlayback(): Boolean =
        loaded is PlaybackHookPlugin || manifest.capabilities.playbackHooks

    fun contributesExtractors(): Boolean =
        loaded is ExtractorContributionPlugin || manifest.capabilities.extractors

    fun contributesSubtitles(): Boolean =
        loaded is SubtitleContributionPlugin || manifest.capabilities.subtitles

    fun contributesSettings(): Boolean =
        loaded is SettingsContributionPlugin || manifest.capabilities.settings

    private fun mergeCapabilities(
        entry: PluginCatalog.Entry,
        loadedCaps: PluginManifest.Capabilities,
    ): PluginManifest.Capabilities {
        val fromCatalog = entry.capabilities
        if (fromCatalog == null) return loadedCaps
        return loadedCaps.copy(
            movies = loadedCaps.movies || fromCatalog.movies,
            tvShows = loadedCaps.tvShows || fromCatalog.tvShows,
            live = loadedCaps.live || fromCatalog.live,
            selfHosted = loadedCaps.selfHosted || fromCatalog.selfHosted,
            requiresAuth = loadedCaps.requiresAuth || fromCatalog.requiresAuth,
            homeContribution = loadedCaps.homeContribution || fromCatalog.homeContribution,
            searchContribution = loadedCaps.searchContribution || fromCatalog.searchContribution,
            metadataEnrichment = loadedCaps.metadataEnrichment || fromCatalog.metadataEnrichment,
            playbackHooks = loadedCaps.playbackHooks || fromCatalog.playbackHooks,
            extractors = loadedCaps.extractors || fromCatalog.extractors,
            subtitles = loadedCaps.subtitles || fromCatalog.subtitles,
            settings = loadedCaps.settings || fromCatalog.settings,
        )
    }
}
