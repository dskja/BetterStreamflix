package com.dskja.betterstreamflix.platform.trakt

import android.util.Log
import com.dskja.betterstreamflix.adapters.AppAdapter
import com.dskja.betterstreamflix.models.Episode
import com.dskja.betterstreamflix.models.Movie
import com.dskja.betterstreamflix.models.TvShow
import com.dskja.betterstreamflix.models.WatchItem
import org.json.JSONArray
import org.json.JSONObject

/**
 * Maps Trakt `/sync/playback` into Continue Watching items.
 */
object TraktContinueWatching {
    private const val TAG = "TraktCW"

    suspend fun load(): List<AppAdapter.Item> {
        if (!TraktConfig.configured()) return emptyList()
        return runCatching {
            val items = TraktClient.fetchPlayback()
            parsePlayback(items)
        }.getOrElse {
            Log.w(TAG, "load failed: ${it.message}")
            emptyList()
        }
    }

    fun parsePlayback(items: JSONArray): List<AppAdapter.Item> {
        val out = mutableListOf<AppAdapter.Item>()
        for (i in 0 until items.length()) {
            val row = items.optJSONObject(i) ?: continue
            val progress = row.optDouble("progress", 0.0)
            val pausedAt = row.optLong("paused_at", 0L)
            val type = row.optString("type")
            when (type) {
                "movie" -> {
                    val movie = row.optJSONObject("movie") ?: continue
                    val ids = movie.optJSONObject("ids")
                    val imdb = ids?.optString("imdb")
                    val mapped = Movie(
                        id = imdb?.takeIf { it.isNotBlank() } ?: ids?.opt("tmdb")?.toString().orEmpty(),
                        title = movie.optString("title"),
                        overview = movie.optString("overview").ifBlank { null },
                        released = movie.optString("year").takeIf { it.isNotBlank() },
                        imdbId = imdb,
                        poster = null,
                    ).apply {
                        providerName = "Trakt"
                        if (progress > 0) {
                            val duration = 7_200_000L
                            watchHistory = WatchItem.WatchHistory(
                                lastEngagementTimeUtcMillis = pausedAt.takeIf { it > 0 }
                                    ?: System.currentTimeMillis(),
                                lastPlaybackPositionMillis = (duration * (progress / 100.0)).toLong(),
                                durationMillis = duration,
                            )
                        }
                    }
                    if (mapped.id.isNotBlank()) out.add(mapped)
                }
                "episode" -> {
                    val episode = row.optJSONObject("episode") ?: continue
                    val show = row.optJSONObject("show") ?: continue
                    val showIds = show.optJSONObject("ids")
                    val showImdb = showIds?.optString("imdb")
                    val season = episode.optInt("season")
                    val number = episode.optInt("number")
                    val ep = Episode(
                        id = "${showImdb ?: show.optString("title")}-S${season}E$number",
                        number = number,
                        title = episode.optString("title").ifBlank { null },
                        tvShow = TvShow(
                            id = showImdb ?: show.optString("title"),
                            title = show.optString("title"),
                            imdbId = showImdb,
                        ).also { it.providerName = "Trakt" },
                    ).apply {
                        if (progress > 0) {
                            val duration = 2_700_000L
                            watchHistory = WatchItem.WatchHistory(
                                lastEngagementTimeUtcMillis = pausedAt.takeIf { it > 0 }
                                    ?: System.currentTimeMillis(),
                                lastPlaybackPositionMillis = (duration * (progress / 100.0)).toLong(),
                                durationMillis = duration,
                            )
                        }
                    }
                    out.add(ep)
                }
            }
        }
        return out
    }
}
