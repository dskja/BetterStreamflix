package com.betterstreamflix.utils

import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import com.betterstreamflix.models.Video

/**
 * Handles deep links for navigating to specific content within the app.
 * Supports URIs like:
 *   betterstreamflix://movie/{id}
 *   betterstreamflix://tvshow/{id}
 *   betterstreamflix://episode/{id}
 *   betterstreamflix://search/{query}
 *   betterstreamflix://provider/{name}
 */
object DeepLinkHandler {

    const val SCHEME = "betterstreamflix"
    const val HOST_MOVIE = "movie"
    const val HOST_TVSHOW = "tvshow"
    const val HOST_EPISODE = "episode"
    const val HOST_SEARCH = "search"
    const val HOST_PROVIDER = "provider"

    /** Optional query applied when opening Search from a deep link. */
    @Volatile
    var pendingSearchQuery: String? = null

    /**
     * Parse a deep link URI into a DeepLink action.
     */
    fun parse(uri: Uri): DeepLink? {
        if (uri.scheme != SCHEME) return null
        return when (uri.host) {
            HOST_MOVIE -> uri.lastPathSegment?.let { DeepLink.Movie(it) }
            HOST_TVSHOW -> uri.lastPathSegment?.let { DeepLink.TvShow(it) }
            HOST_EPISODE -> uri.lastPathSegment?.let { DeepLink.Episode(it) }
            HOST_SEARCH -> uri.lastPathSegment?.let { DeepLink.Search(it) }
            HOST_PROVIDER -> uri.lastPathSegment?.let { DeepLink.Provider(it) }
            else -> null
        }
    }

    /**
     * Create a deep link URI for a movie.
     */
    fun movieUri(id: String): Uri = Uri.parse("$SCHEME://$HOST_MOVIE/$id")

    /**
     * Create a deep link URI for a TV show.
     */
    fun tvShowUri(id: String): Uri = Uri.parse("$SCHEME://$HOST_TVSHOW/$id")

    /**
     * Create a deep link URI for a search query.
     */
    fun searchUri(query: String): Uri = Uri.parse("$SCHEME://$HOST_SEARCH/${Uri.encode(query)}")
}

/**
 * Sealed class representing parsed deep link destinations.
 */
sealed class DeepLink {
    data class Movie(val id: String) : DeepLink()
    data class TvShow(val id: String) : DeepLink()
    data class Episode(val id: String) : DeepLink()
    data class Search(val query: String) : DeepLink()
    data class Provider(val name: String) : DeepLink()
}
