package com.dskja.betterstreamflix.platform.plugins

import android.util.Log
import com.dskja.betterstreamflix.adapters.AppAdapter
import com.dskja.betterstreamflix.models.Category
import com.dskja.betterstreamflix.models.Movie
import com.dskja.betterstreamflix.models.TvShow
import com.dskja.betterstreamflix.models.Video
import com.dskja.betterstreamflix.providers.Provider

/**
 * Narrow host facade plugins may use. Prefer this over reaching into app internals.
 */
interface PluginHost {
    fun log(tag: String, message: String)
    fun isPluginDisabled(id: String): Boolean
    fun httpUserAgent(): String
    fun apiVersion(): Int
}

object DefaultPluginHost : PluginHost {
    override fun log(tag: String, message: String) {
        Log.d("Plugin/$tag", message)
    }

    override fun isPluginDisabled(id: String): Boolean =
        com.dskja.betterstreamflix.utils.UserPreferences.isPluginDisabled(id)

    override fun httpUserAgent(): String =
        com.dskja.betterstreamflix.utils.NetworkClient.USER_AGENT

    override fun apiVersion(): Int = PluginApi.VERSION
}

/** Shared API contract version for LOCAL APKs and built-in addons. */
object PluginApi {
    const val VERSION = PluginVerifier.CURRENT_API_VERSION
}

/**
 * Optional extensions beyond [SourcePlugin] / Provider.
 * Built-ins and LOCAL APKs may implement any subset.
 */
interface PluginExtension {
    val pluginId: String
}

/** Contribute extra home categories (merged after provider home rows). */
interface HomeContributionPlugin : PluginExtension {
    suspend fun homeCategories(provider: Provider): List<Category>
}

/** Append or inject search hits for the active provider query. */
interface SearchContributionPlugin : PluginExtension {
    suspend fun search(provider: Provider, query: String, page: Int = 1): List<AppAdapter.Item>
}

/** Enrich movie/TV detail after the provider fetch (non-destructive merge). */
interface MetadataEnrichmentPlugin : PluginExtension {
    suspend fun enrichMovie(provider: Provider, movie: Movie): Movie = movie
    suspend fun enrichTvShow(provider: Provider, tvShow: TvShow): TvShow = tvShow
}

/** Observe / augment playback lifecycle (scrobble-style; never blocks ExoPlayer). */
interface PlaybackHookPlugin : PluginExtension {
    fun onPlaybackStarted(videoType: Video.Type, serverName: String?) {}
    fun onPlaybackEnded(videoType: Video.Type) {}
    fun onServerResolved(server: Video.Server) {}
    fun onPlaybackProgress(videoType: Video.Type, positionMs: Long, durationMs: Long) {}
}

/** Soft-register extractors when the host allows dynamic extractors. */
interface ExtractorContributionPlugin : PluginExtension {
    /** Host identifiers this plugin claims to handle (for catalog display). */
    fun extractorNames(): List<String>
}

/** Optional subtitle provider hints (language codes / source labels). */
interface SubtitleContributionPlugin : PluginExtension {
    fun subtitleLanguages(): List<String> = emptyList()
    fun subtitleSourceLabel(): String = pluginId
}

/** Ephemeral settings rows for the Sources manage UI. */
interface SettingsContributionPlugin : PluginExtension {
    fun settingsSummary(): String = ""
    fun settingsActions(): List<PluginSettingsAction> = emptyList()
}

data class PluginSettingsAction(
    val id: String,
    val label: String,
    val description: String = "",
)

/**
 * Base plugin entry: every marketplace item is at least a [SourcePlugin].
 * Implement optional interfaces for richer capabilities.
 */
interface BetterStreamflixPlugin : SourcePlugin {
    fun onAttach(host: PluginHost) {}
    fun onDetach() {}
}
