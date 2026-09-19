package com.dskja.betterstreamflix.platform.plex

import android.util.Log
import com.dskja.betterstreamflix.utils.NetworkClient
import com.dskja.betterstreamflix.utils.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

/**
 * Reports playback progress to Plex (`/: /timeline`) so On Deck stays accurate.
 */
object PlexPlaybackReporter {
    private const val TAG = "PlexProgress"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lastAt = ConcurrentHashMap<String, Long>()

    fun report(ratingKey: String, positionMs: Long, durationMs: Long, isPlaying: Boolean) {
        if (!PlexApi().configured() || ratingKey.isBlank() || durationMs <= 0L) return
        val now = System.currentTimeMillis()
        val last = lastAt[ratingKey] ?: 0L
        val progress = positionMs.toDouble() / durationMs.toDouble()
        val force = !isPlaying || progress >= 0.95
        val interval = UserPreferences.selfHostProgressIntervalMs.coerceIn(5_000L, 60_000L)
        if (!force && now - last < interval) return
        lastAt[ratingKey] = now
        scope.launch {
            runCatching {
                val state = when {
                    progress >= 0.95 -> "stopped"
                    !isPlaying -> "paused"
                    else -> "playing"
                }
                val base = UserPreferences.plexBaseUrl.trim().trimEnd('/')
                val token = UserPreferences.plexToken.trim()
                val url = "$base/:/timeline?ratingKey=$ratingKey" +
                    "&key=/library/metadata/$ratingKey" +
                    "&state=$state" +
                    "&time=${positionMs.coerceAtLeast(0L)}" +
                    "&duration=$durationMs" +
                    "&X-Plex-Token=$token"
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .header("Accept", "application/json")
                    .header("X-Plex-Token", token)
                    .header("X-Plex-Product", "BetterStreamflix")
                    .header(
                        "X-Plex-Client-Identifier",
                        UserPreferences.plexClientId.ifBlank { "BetterStreamflix" },
                    )
                    .build()
                NetworkClient.default.newCall(request).execute().close()
            }.onFailure { Log.w(TAG, "report failed: ${it.message}") }
        }
    }
}
