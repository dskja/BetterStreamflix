package com.dskja.betterstreamflix.platform.trakt

import android.content.Context
import android.util.Log
import com.dskja.betterstreamflix.models.Episode
import com.dskja.betterstreamflix.models.Movie
import com.dskja.betterstreamflix.models.WatchItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Fire-and-forget Trakt hooks mirroring CloudSyncHooks call sites.
 * Session state is keyed per media id to avoid cross-title races.
 */
object TraktSyncHooks {
    private const val TAG = "TraktSyncHooks"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val startedKeys = ConcurrentHashMap.newKeySet<String>()
    private val lastProgressAt = ConcurrentHashMap<String, Long>()

    fun movieWatched(context: Context, movie: Movie) {
        if (!TraktConfig.configured()) return
        val ids = TraktIds(imdb = movie.imdbId)
        if (ids.isEmpty()) return
        val progress = progressOf(movie)
        val key = "movie:${movie.imdbId ?: movie.id}"
        scope.launch {
            dispatchMovie(key, ids, progress, isPlaying = false)
        }
    }

    fun episodeWatched(context: Context, episode: Episode) {
        if (!TraktConfig.configured()) return
        val ref = episodeRef(episode) ?: return
        val progress = progressOf(episode)
        val key = "ep:${ref.showIds.imdb ?: episode.id}:S${ref.season}E${ref.number}"
        scope.launch {
            dispatchEpisode(key, ref, progress, isPlaying = false)
        }
    }

    fun movieFavorite(context: Context, movie: Movie) {
        if (!TraktConfig.configured()) return
        val imdb = movie.imdbId ?: return
        scope.launch { TraktClient.addToWatchlist(imdb, "movie") }
    }

    fun onPlaybackProgress(
        imdbId: String?,
        positionMs: Long,
        durationMs: Long,
        isPlaying: Boolean,
        mediaKey: String = imdbId.orEmpty(),
        episodeRef: TraktEpisodeRef? = null,
    ) {
        if (!TraktConfig.configured() || durationMs <= 0L) return
        val progress = (positionMs.toDouble() / durationMs.toDouble()) * 100.0
        val keyHint = mediaKey.ifBlank { imdbId.orEmpty() }
        val now = System.currentTimeMillis()
        // Throttle start heartbeats per title; always allow pause/stop decisions.
        if (isPlaying && progress < 80.0) {
            val last = lastProgressAt[keyHint] ?: 0L
            if (now - last < 25_000L) return
        }
        lastProgressAt[keyHint] = now
        scope.launch {
            if (episodeRef != null) {
                val key = mediaKey.ifBlank {
                    "ep:${episodeRef.showIds.imdb}:S${episodeRef.season}E${episodeRef.number}"
                }
                dispatchEpisode(key, episodeRef, progress, isPlaying)
            } else if (!imdbId.isNullOrBlank()) {
                val key = mediaKey.ifBlank { "movie:$imdbId" }
                dispatchMovie(key, TraktIds(imdb = imdbId), progress, isPlaying)
            }
        }
    }

    fun resetSession(mediaKey: String? = null) {
        if (mediaKey == null) {
            startedKeys.clear()
            lastProgressAt.clear()
        } else {
            startedKeys.remove(mediaKey)
            lastProgressAt.remove(mediaKey)
        }
    }

    private suspend fun dispatchMovie(
        key: String,
        ids: TraktIds,
        progress: Double,
        isPlaying: Boolean,
    ) {
        val already = startedKeys.contains(key)
        when (TraktScrobbler.decide(0.0, progress, isPlaying, already)) {
            TraktScrobbler.Action.START -> {
                TraktClient.scrobbleMovieStart(ids, progress)
                startedKeys.add(key)
            }
            TraktScrobbler.Action.PAUSE -> TraktClient.scrobbleMoviePause(ids, progress)
            TraktScrobbler.Action.STOP -> {
                TraktClient.scrobbleMovieStop(ids, progress)
                startedKeys.remove(key)
            }
            TraktScrobbler.Action.NONE -> Unit
        }
        Log.d(TAG, "movie $key progress=$progress playing=$isPlaying")
    }

    private suspend fun dispatchEpisode(
        key: String,
        ref: TraktEpisodeRef,
        progress: Double,
        isPlaying: Boolean,
    ) {
        val already = startedKeys.contains(key)
        when (TraktScrobbler.decide(0.0, progress, isPlaying, already)) {
            TraktScrobbler.Action.START -> {
                TraktClient.scrobbleEpisodeStart(ref, progress)
                startedKeys.add(key)
            }
            TraktScrobbler.Action.PAUSE -> TraktClient.scrobbleEpisodePause(ref, progress)
            TraktScrobbler.Action.STOP -> {
                TraktClient.scrobbleEpisodeStop(ref, progress)
                startedKeys.remove(key)
            }
            TraktScrobbler.Action.NONE -> Unit
        }
        Log.d(TAG, "episode $key progress=$progress playing=$isPlaying")
    }

    private fun episodeRef(episode: Episode): TraktEpisodeRef? {
        val showImdb = episode.tvShow?.imdbId
        val season = episode.season?.number ?: return null
        val number = episode.number
        if (number <= 0) return null
        // Prefer show IMDb; fall back to nothing usable.
        if (showImdb.isNullOrBlank()) return null
        return TraktEpisodeRef(
            showIds = TraktIds(imdb = showImdb),
            season = season,
            number = number,
        )
    }

    private fun progressOf(item: WatchItem): Double {
        val history = item.watchHistory ?: return if (item.isWatched) 100.0 else 0.0
        if (history.durationMillis <= 0L) return if (item.isWatched) 100.0 else 0.0
        return (history.lastPlaybackPositionMillis.toDouble() / history.durationMillis.toDouble()) * 100.0
    }
}
