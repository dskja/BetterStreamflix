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

/**
 * Fire-and-forget Trakt hooks mirroring CloudSyncHooks call sites.
 */
object TraktSyncHooks {
    private const val TAG = "TraktSyncHooks"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var scrobbleStarted = false

    fun movieWatched(context: Context, movie: Movie) {
        if (!TraktConfig.configured()) return
        val imdb = movie.imdbId ?: return
        val progress = progressOf(movie)
        scope.launch {
            val action = TraktScrobbler.decide(
                previousProgress = 0.0,
                currentProgress = progress,
                isPlaying = false,
                alreadyStarted = scrobbleStarted,
            )
            when (action) {
                TraktScrobbler.Action.STOP -> {
                    TraktClient.scrobbleStop(imdb, progress)
                    scrobbleStarted = false
                }
                TraktScrobbler.Action.START -> {
                    TraktClient.scrobbleStart(imdb, progress)
                    scrobbleStarted = true
                }
                TraktScrobbler.Action.PAUSE -> TraktClient.scrobblePause(imdb, progress)
                TraktScrobbler.Action.NONE -> Unit
            }
            Log.d(TAG, "movie ${movie.id} action=$action progress=$progress")
        }
    }

    fun episodeWatched(context: Context, episode: Episode) {
        if (!TraktConfig.configured()) return
        // Episodes often lack imdb on the leaf; stop/start still best-effort via movie path later.
        val progress = progressOf(episode)
        scope.launch {
            if (progress >= 80.0) {
                Log.d(TAG, "episode ${episode.id} near-complete ($progress%) — stop queued")
            }
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
    ) {
        if (!TraktConfig.configured() || imdbId.isNullOrBlank() || durationMs <= 0L) return
        val progress = (positionMs.toDouble() / durationMs.toDouble()) * 100.0
        scope.launch {
            when (
                TraktScrobbler.decide(
                    previousProgress = 0.0,
                    currentProgress = progress,
                    isPlaying = isPlaying,
                    alreadyStarted = scrobbleStarted,
                )
            ) {
                TraktScrobbler.Action.START -> {
                    TraktClient.scrobbleStart(imdbId, progress)
                    scrobbleStarted = true
                }
                TraktScrobbler.Action.PAUSE -> TraktClient.scrobblePause(imdbId, progress)
                TraktScrobbler.Action.STOP -> {
                    TraktClient.scrobbleStop(imdbId, progress)
                    scrobbleStarted = false
                }
                TraktScrobbler.Action.NONE -> Unit
            }
        }
    }

    fun resetSession() {
        scrobbleStarted = false
    }

    private fun progressOf(item: WatchItem): Double {
        val history = item.watchHistory ?: return if (item.isWatched) 100.0 else 0.0
        if (history.durationMillis <= 0L) return if (item.isWatched) 100.0 else 0.0
        return (history.lastPlaybackPositionMillis.toDouble() / history.durationMillis.toDouble()) * 100.0
    }
}
