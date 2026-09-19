package com.dskja.betterstreamflix.platform.simkl

import android.util.Log
import com.dskja.betterstreamflix.utils.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Throttled Simkl scrobble fan-out from the player.
 */
object SimklSyncHooks {
    private const val TAG = "SimklSync"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = ConcurrentHashMap.newKeySet<String>()
    private val lastProgress = ConcurrentHashMap<String, Double>()
    private val lastAt = ConcurrentHashMap<String, Long>()

    fun onPlaybackProgress(
        imdbId: String?,
        positionMs: Long,
        durationMs: Long,
        isPlaying: Boolean,
        mediaKey: String,
        season: Int? = null,
        episode: Int? = null,
        tmdbId: String? = null,
    ) {
        if (!SimklConfig.configured() || durationMs <= 0L) return
        if (imdbId.isNullOrBlank() && tmdbId.isNullOrBlank()) return
        val progress = (positionMs.toDouble() / durationMs.toDouble()) * 100.0
        val prev = lastProgress[mediaKey] ?: 0.0
        val already = started.contains(mediaKey)
        val action = SimklScrobbler.decide(prev, progress, isPlaying, already)
        lastProgress[mediaKey] = progress
        if (action == SimklScrobbler.Action.NONE) return
        val now = System.currentTimeMillis()
        val last = lastAt[mediaKey] ?: 0L
        if (action != SimklScrobbler.Action.STOP &&
            action != SimklScrobbler.Action.PAUSE &&
            now - last < 10_000L
        ) return
        lastAt[mediaKey] = now
        when (action) {
            SimklScrobbler.Action.START -> started.add(mediaKey)
            SimklScrobbler.Action.STOP -> started.remove(mediaKey)
            else -> Unit
        }
        scope.launch {
            runCatching {
                SimklClient.scrobble(
                    action = action,
                    progress = progress,
                    imdbId = imdbId,
                    tmdbId = tmdbId,
                    season = season,
                    episode = episode,
                )
            }.onFailure { Log.w(TAG, "scrobble failed: ${it.message}") }
        }
    }

    fun resetSession(mediaKey: String? = null) {
        if (mediaKey == null) {
            started.clear()
            lastProgress.clear()
            lastAt.clear()
        } else {
            started.remove(mediaKey)
            lastProgress.remove(mediaKey)
            lastAt.remove(mediaKey)
        }
    }
}
