package com.betterstreamflix.utils

import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import com.betterstreamflix.models.Video

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

    @Volatile
    var pendingSearchQuery: String? = null

    @Volatile
    var pendingOpenContinueWatching: Boolean = false

    fun parse(uri: Uri): DeepLink? {
        if (uri.scheme != SCHEME) return null
        return when (uri.host) {
            HOST_MOVIE -> uri.lastPathSegment?.let { DeepLink.Movie(it) }
            HOST_TVSHOW -> uri.lastPathSegment?.let { DeepLink.TvShow(it) }
            HOST_EPISODE -> uri.lastPathSegment?.let { DeepLink.Episode(it) }
            HOST_SEARCH -> uri.lastPathSegment?.let { DeepLink.Search(it) }
            HOST_PROVIDER -> uri.lastPathSegment?.let { DeepLink.Provider(it) }
            HOST_FAVORITES -> DeepLink.Favorites
            HOST_CONTINUE -> DeepLink.ContinueWatching
            else -> null
        }
    }

    fun movieUri(id: String): Uri = Uri.parse("$SCHEME://$HOST_MOVIE/$id")
    fun tvShowUri(id: String): Uri = Uri.parse("$SCHEME://$HOST_TVSHOW/$id")
    fun searchUri(query: String): Uri = Uri.parse("$SCHEME://$HOST_SEARCH/${Uri.encode(query)}")
    fun favoritesUri(): Uri = Uri.parse("$SCHEME://$HOST_FAVORITES/")
    fun continueWatchingUri(): Uri = Uri.parse("$SCHEME://$HOST_CONTINUE/")
}

sealed class DeepLink {
    data class Movie(val id: String) : DeepLink()
    data class TvShow(val id: String) : DeepLink()
    data class Episode(val id: String) : DeepLink()
    data class Search(val query: String) : DeepLink()
    data class Provider(val name: String) : DeepLink()
    data object Favorites : DeepLink()
    data object ContinueWatching : DeepLink()
}
