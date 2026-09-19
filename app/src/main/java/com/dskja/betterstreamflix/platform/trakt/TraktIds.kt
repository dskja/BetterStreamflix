package com.dskja.betterstreamflix.platform.trakt

/**
 * External ids used for Trakt scrobble / sync payloads.
 */
data class TraktIds(
    val imdb: String? = null,
    val tmdb: Int? = null,
    val tvdb: Int? = null,
    val trakt: Int? = null,
) {
    fun isEmpty(): Boolean =
        imdb.isNullOrBlank() && tmdb == null && tvdb == null && trakt == null

    fun toJson(): org.json.JSONObject = org.json.JSONObject().apply {
        if (!imdb.isNullOrBlank()) put("imdb", imdb)
        if (tmdb != null) put("tmdb", tmdb)
        if (tvdb != null) put("tvdb", tvdb)
        if (trakt != null) put("trakt", trakt)
    }
}

data class TraktEpisodeRef(
    val showIds: TraktIds,
    val season: Int,
    val number: Int,
    val episodeIds: TraktIds = TraktIds(),
)
