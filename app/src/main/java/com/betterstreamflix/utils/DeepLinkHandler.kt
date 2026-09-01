package com.betterstreamflix.utils

import android.net.Uri
import com.betterstreamflix.providers.Provider

/**
 * Handles deep links for navigating to specific content within the app.
 */
object DeepLinkHandler {

    const val SCHEME = "betterstreamflix"
    const val HOST_MOVIE = "movie"
    const val HOST_TVSHOW = "tvshow"
    const val HOST_EPISODE = "episode"
    const val HOST_SEARCH = "search"
    const val HOST_PROVIDER = "provider"
    const val HOST_FAVORITES = "favorites"
    const val HOST_CONTINUE = "continue"
    const val QUERY_PROVIDER = "provider"

    @Volatile
    var pendingSearchQuery: String? = null

    @Volatile
    var pendingOpenContinueWatching: Boolean = false

    fun parse(uri: Uri): DeepLink? {
        if (uri.scheme != SCHEME) return null
        val providerName = uri.getQueryParameter(QUERY_PROVIDER)
        return when (uri.host) {
            HOST_MOVIE -> uri.lastPathSegment?.let { DeepLink.Movie(it, providerName) }
            HOST_TVSHOW -> uri.lastPathSegment?.let { DeepLink.TvShow(it, providerName) }
            HOST_EPISODE -> uri.lastPathSegment?.let { DeepLink.Episode(it, providerName) }
            HOST_SEARCH -> uri.lastPathSegment?.let { DeepLink.Search(it) }
            HOST_PROVIDER -> uri.lastPathSegment?.let { DeepLink.Provider(it) }
            HOST_FAVORITES -> DeepLink.Favorites
            HOST_CONTINUE -> DeepLink.ContinueWatching
            else -> null
        }
    }

    fun movieUri(id: String, providerName: String? = null): Uri =
        buildUri(HOST_MOVIE, id, providerName)

    fun tvShowUri(id: String, providerName: String? = null): Uri =
        buildUri(HOST_TVSHOW, id, providerName)

    fun episodeUri(id: String, providerName: String? = null): Uri =
        buildUri(HOST_EPISODE, id, providerName)

    fun searchUri(query: String): Uri = Uri.parse("$SCHEME://$HOST_SEARCH/${Uri.encode(query)}")

    fun favoritesUri(): Uri = Uri.parse("$SCHEME://$HOST_FAVORITES/")

    fun continueWatchingUri(): Uri = Uri.parse("$SCHEME://$HOST_CONTINUE/")

    private fun buildUri(host: String, id: String, providerName: String?): Uri {
        val base = Uri.parse("$SCHEME://$host/$id")
        return if (providerName.isNullOrBlank()) {
            base
        } else {
            base.buildUpon()
                .appendQueryParameter(QUERY_PROVIDER, providerName)
                .build()
        }
    }

    fun applyProviderIfPresent(providerName: String?) {
        val targetName = providerName?.takeIf { it.isNotBlank() } ?: return
        val provider = Provider.providers.keys
            .find { it.name == targetName }
            ?: return
        UserPreferences.currentProvider = provider
    }
}

sealed class DeepLink {
    abstract val providerName: String?

    data class Movie(val id: String, override val providerName: String? = null) : DeepLink()
    data class TvShow(val id: String, override val providerName: String? = null) : DeepLink()
    data class Episode(val id: String, override val providerName: String? = null) : DeepLink()
    data class Search(val query: String) : DeepLink() {
        override val providerName: String? = null
    }
    data class Provider(val name: String) : DeepLink() {
        override val providerName: String? = null
    }
    data object Favorites : DeepLink() {
        override val providerName: String? = null
    }
    data object ContinueWatching : DeepLink() {
        override val providerName: String? = null
    }
}
