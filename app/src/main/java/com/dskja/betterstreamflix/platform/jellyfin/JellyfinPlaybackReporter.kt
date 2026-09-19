package com.dskja.betterstreamflix.platform.jellyfin

import android.util.Log
import com.dskja.betterstreamflix.utils.NetworkClient
import com.dskja.betterstreamflix.utils.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Reports playback progress back to Jellyfin so On Deck / Resume stay in sync.
 */
object JellyfinPlaybackReporter {
    private const val TAG = "JellyfinProgress"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()
    private val lastAt = ConcurrentHashMap<String, Long>()
    private val playSessions = ConcurrentHashMap<String, String>()

    fun report(itemId: String, positionMs: Long, durationMs: Long, isPlaying: Boolean) {
        if (!JellyfinApi().configured() || itemId.isBlank() || durationMs <= 0L) return
        val now = System.currentTimeMillis()
        val last = lastAt[itemId] ?: 0L
        val progress = positionMs.toDouble() / durationMs.toDouble()
        val force = !isPlaying || progress >= 0.95
        val interval = UserPreferences.selfHostProgressIntervalMs.coerceIn(5_000L, 60_000L)
        if (!force && now - last < interval) return
        lastAt[itemId] = now
        scope.launch {
            runCatching {
                val sessionId = playSessions.getOrPut(itemId) { UUID.randomUUID().toString() }
                val ticks = (positionMs.coerceAtLeast(0L)) * 10_000L
                when {
                    progress >= 0.95 || (!isPlaying && progress >= 0.90) -> {
                        post("/Sessions/Playing/Stopped", sessionBody(itemId, sessionId, ticks, stopped = true))
                        playSessions.remove(itemId)
                    }
                    !isPlaying -> {
                        post("/Sessions/Playing/Progress", sessionBody(itemId, sessionId, ticks, paused = true))
                    }
                    last == 0L || !playSessions.containsKey(itemId) -> {
                        playSessions[itemId] = sessionId
                        post("/Sessions/Playing", sessionBody(itemId, sessionId, ticks))
                    }
                    else -> post("/Sessions/Playing/Progress", sessionBody(itemId, sessionId, ticks))
                }
            }.onFailure { Log.w(TAG, "report failed: ${it.message}") }
        }
    }

    private fun sessionBody(
        itemId: String,
        sessionId: String,
        positionTicks: Long,
        paused: Boolean = false,
        stopped: Boolean = false,
    ): String = JSONObject()
        .put("ItemId", itemId)
        .put("PlaySessionId", sessionId)
        .put("MediaSourceId", itemId)
        .put("PositionTicks", positionTicks)
        .put("IsPaused", paused)
        .put("PlayedToCompletion", stopped)
        .toString()

    private fun post(path: String, body: String) {
        val base = UserPreferences.jellyfinBaseUrl.trim().trimEnd('/')
        val token = UserPreferences.jellyfinAccessToken.trim()
        if (base.isBlank() || token.isBlank()) return
        val deviceId = UserPreferences.jellyfinDeviceId.ifBlank { "BetterStreamflix" }
        val auth = "MediaBrowser Client=\"BetterStreamflix\", Device=\"Android\", " +
            "DeviceId=\"$deviceId\", Version=\"1.0.0\", Token=\"$token\""
        val request = Request.Builder()
            .url(base + path)
            .post(body.toRequestBody(jsonMedia))
            .header("Authorization", auth)
            .header("X-Emby-Token", token)
            .header("Content-Type", "application/json")
            .build()
        NetworkClient.default.newCall(request).execute().close()
    }
}
