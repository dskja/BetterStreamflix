package com.dskja.betterstreamflix.platform.trakt

import android.util.Log
import com.dskja.betterstreamflix.adapters.AppAdapter
import com.dskja.betterstreamflix.models.Episode
import com.dskja.betterstreamflix.models.Movie
import com.dskja.betterstreamflix.models.TvShow
import com.dskja.betterstreamflix.models.WatchItem
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Maps Trakt `/sync/playback` into Continue Watching items.
 */
object TraktContinueWatching {
    private const val TAG = "TraktCW"
    private val isoFormats = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ssZ",
    )

    suspend fun load(): List<AppAdapter.Item> {
        if (!TraktConfig.configured()) return emptyList()
        return runCatching {
            parsePlayback(TraktClient.fetchPlayback())
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
            val pausedAt = parsePausedAt(row)
            val type = row.optString("type")
            when (type) {
                "movie" -> {
                    val movie = row.optJSONObject("movie") ?: continue
                    val ids = movie.optJSONObject("ids")
                    val imdb = ids?.optString("imdb")?.ifBlank { null }
                    val tmdb = ids?.opt("tmdb")?.toString()
                    val runtimeMin = movie.optInt("runtime", 0).takeIf { it > 0 } ?: 120
                    val duration = runtimeMin * 60_000L
                    val mapped = Movie(
                        id = imdb ?: tmdb.orEmpty(),
                        title = movie.optString("title"),
                        overview = movie.optString("overview").ifBlank { null },
                        released = movie.opt("year")?.toString()?.takeIf { it.isNotBlank() },
                        runtime = runtimeMin,
                        imdbId = imdb,
                        poster = null,
                    ).apply {
                        // Prefer current provider for playback; Trakt is metadata-only overlay.
                        providerName = UserPreferencesSafe.currentProviderName() ?: "Trakt"
                        if (progress > 0) {
                            watchHistory = WatchItem.WatchHistory(
                                lastEngagementTimeUtcMillis = pausedAt,
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
                    val showImdb = showIds?.optString("imdb")?.ifBlank { null }
                    val season = episode.optInt("season")
                    val number = episode.optInt("number")
                    val runtimeMin = episode.optInt("runtime", 0).takeIf { it > 0 }
                        ?: show.optInt("runtime", 0).takeIf { it > 0 }
                        ?: 45
                    val duration = runtimeMin * 60_000L
                    val ep = Episode(
                        id = "${showImdb ?: show.optString("title")}-S${season}E$number",
                        number = number,
                        title = episode.optString("title").ifBlank { null },
                        tvShow = TvShow(
                            id = showImdb ?: show.optString("title"),
                            title = show.optString("title"),
                            imdbId = showImdb,
                            providerName = UserPreferencesSafe.currentProviderName() ?: "Trakt",
                        ),
                    ).apply {
                        if (progress > 0) {
                            watchHistory = WatchItem.WatchHistory(
                                lastEngagementTimeUtcMillis = pausedAt,
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

    fun parsePausedAt(row: JSONObject): Long {
        val asLong = row.optLong("paused_at", 0L)
        if (asLong > 1_000_000_000_000L) return asLong
        if (asLong in 1_000_000_000L..9_999_999_999L) return asLong * 1000L
        return parsePausedAtString(row.optString("paused_at"))
    }

    fun parsePausedAtString(raw: String): Long {
        if (raw.isBlank()) return System.currentTimeMillis()
        raw.toLongOrNull()?.let { n ->
            if (n > 1_000_000_000_000L) return n
            if (n in 1_000_000_000L..9_999_999_999L) return n * 1000L
        }
        for (pattern in isoFormats) {
            val parsed = runCatching {
                SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.parse(raw)?.time
            }.getOrNull()
            if (parsed != null) return parsed
        }
        return System.currentTimeMillis()
    }

    /** Avoids hard UserPreferences dependency failures in unit tests. */
    private object UserPreferencesSafe {
        fun currentProviderName(): String? =
            runCatching {
                com.dskja.betterstreamflix.utils.UserPreferences.currentProvider?.name
            }.getOrNull()
    }
}
