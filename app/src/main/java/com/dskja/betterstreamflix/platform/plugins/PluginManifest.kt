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
    val minApiVersion: Int = 1,
) {
    enum class Source { BUILTIN, LOCAL, REMOTE }

    data class Capabilities(
        val movies: Boolean = true,
        val tvShows: Boolean = true,
        val live: Boolean = false,
        val selfHosted: Boolean = false,
        val requiresAuth: Boolean = false,
        val homeContribution: Boolean = false,
        val searchContribution: Boolean = false,
        val metadataEnrichment: Boolean = false,
        val playbackHooks: Boolean = false,
        val extractors: Boolean = false,
        val subtitles: Boolean = false,
        val settings: Boolean = false,
    ) {
        fun labels(): List<String> = buildList {
            if (movies || tvShows) add("source")
            if (homeContribution) add("home")
            if (searchContribution) add("search")
            if (metadataEnrichment) add("metadata")
            if (playbackHooks) add("playback")
            if (extractors) add("extractor")
            if (subtitles) add("subtitles")
            if (settings) add("settings")
            if (live) add("live")
            if (selfHosted) add("self-hosted")
        }
    }

    companion object {
        fun capabilitiesFromLabels(labels: Collection<String>): Capabilities {
            val set = labels.map { it.lowercase().trim() }.toSet()
            return Capabilities(
                movies = "source" in set || "movies" in set || set.isEmpty(),
                tvShows = "source" in set || "tv" in set || "tvshows" in set || set.isEmpty(),
                live = "live" in set,
                selfHosted = "self-hosted" in set || "selfhosted" in set,
                requiresAuth = "auth" in set || "requires-auth" in set,
                homeContribution = "home" in set,
                searchContribution = "search" in set,
                metadataEnrichment = "metadata" in set || "enrich" in set,
                playbackHooks = "playback" in set,
                extractors = "extractor" in set || "extractors" in set,
                subtitles = "subtitles" in set || "subs" in set,
                settings = "settings" in set,
            )
        }
    }
}
