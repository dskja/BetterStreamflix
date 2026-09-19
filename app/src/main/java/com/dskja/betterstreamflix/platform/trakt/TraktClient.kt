package com.dskja.betterstreamflix.platform.trakt

import android.content.Context
import android.util.Log
import com.dskja.betterstreamflix.utils.NetworkClient
import com.dskja.betterstreamflix.utils.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

object TraktClient {
    private const val TAG = "TraktClient"
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    fun warm(context: Context) {
        if (!TraktConfig.configured()) {
            Log.d(TAG, "Trakt not configured")
            return
        }
        Log.d(TAG, "Trakt ready")
    }

    suspend fun scrobbleMovieStart(ids: TraktIds, progress: Double): Boolean =
        scrobbleMovie("start", ids, progress)

    suspend fun scrobbleMoviePause(ids: TraktIds, progress: Double): Boolean =
        scrobbleMovie("pause", ids, progress)

    suspend fun scrobbleMovieStop(ids: TraktIds, progress: Double): Boolean =
        scrobbleMovie("stop", ids, progress)

    suspend fun scrobbleEpisodeStart(ref: TraktEpisodeRef, progress: Double): Boolean =
        scrobbleEpisode("start", ref, progress)

    suspend fun scrobbleEpisodePause(ref: TraktEpisodeRef, progress: Double): Boolean =
        scrobbleEpisode("pause", ref, progress)

    suspend fun scrobbleEpisodeStop(ref: TraktEpisodeRef, progress: Double): Boolean =
        scrobbleEpisode("stop", ref, progress)

    /** Backward-compatible movie scrobble by IMDb id. */
    suspend fun scrobbleStart(imdbId: String?, progress: Double): Boolean =
        scrobbleMovieStart(TraktIds(imdb = imdbId), progress)

    suspend fun scrobblePause(imdbId: String?, progress: Double): Boolean =
        scrobbleMoviePause(TraktIds(imdb = imdbId), progress)

    suspend fun scrobbleStop(imdbId: String?, progress: Double): Boolean =
        scrobbleMovieStop(TraktIds(imdb = imdbId), progress)

    suspend fun addToWatchlist(imdbId: String?, mediaType: String): Boolean =
        withContext(Dispatchers.IO) {
            if (!TraktConfig.configured() || imdbId.isNullOrBlank()) return@withContext false
            val body = JSONObject()
                .put(
                    if (mediaType == "show") "shows" else "movies",
                    JSONArray().put(JSONObject().put("ids", JSONObject().put("imdb", imdbId))),
                )
                .toString()
            post("/sync/watchlist", body)
        }

    suspend fun fetchPlayback(): JSONArray = withContext(Dispatchers.IO) {
        if (!TraktConfig.configured()) return@withContext JSONArray()
        getJson("/sync/playback?extended=full").optJSONArray("items")
            ?: runCatching {
                // /sync/playback returns a bare array
                val raw = getRaw("/sync/playback")
                if (raw.trimStart().startsWith("[")) JSONArray(raw) else JSONArray()
            }.getOrDefault(JSONArray())
    }

    data class DeviceCode(
        val deviceCode: String,
        val userCode: String,
        val verificationUrl: String,
        val intervalSeconds: Int,
        val expiresInSeconds: Int,
    )

    suspend fun requestDeviceCode(): DeviceCode? = withContext(Dispatchers.IO) {
        val clientId = TraktConfig.clientId()
        if (clientId.isBlank()) return@withContext null
        val body = JSONObject().put("client_id", clientId).toString()
        runCatching {
            val request = Request.Builder()
                .url("${TraktConfig.API_BASE}/oauth/device/code")
                .post(body.toRequestBody(jsonMedia))
                .header("Content-Type", "application/json")
                .build()
            NetworkClient.default.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) return@use null
                val json = JSONObject(raw)
                DeviceCode(
                    deviceCode = json.optString("device_code"),
                    userCode = json.optString("user_code"),
                    verificationUrl = json.optString("verification_url")
                        .ifBlank { "https://trakt.tv/activate" },
                    intervalSeconds = json.optInt("interval", 5),
                    expiresInSeconds = json.optInt("expires_in", 600),
                )
            }
        }.getOrNull()
    }

    /**
     * Polls until the user authorizes the device code, then stores tokens.
     * Returns true on success.
     */
    suspend fun pollDeviceToken(deviceCode: String, intervalSeconds: Int, expiresInSeconds: Int): Boolean =
        withContext(Dispatchers.IO) {
            val clientId = TraktConfig.clientId()
            val clientSecret = UserPreferences.traktClientSecret.trim()
            if (clientId.isBlank()) return@withContext false
            val deadline = System.currentTimeMillis() + expiresInSeconds * 1000L
            val interval = intervalSeconds.coerceAtLeast(1) * 1000L
            while (System.currentTimeMillis() < deadline) {
                delay(interval)
                val body = JSONObject()
                    .put("code", deviceCode)
                    .put("client_id", clientId)
                    .put("client_secret", clientSecret)
                    .toString()
                val request = Request.Builder()
                    .url("${TraktConfig.API_BASE}/oauth/device/token")
                    .post(body.toRequestBody(jsonMedia))
                    .header("Content-Type", "application/json")
                    .build()
                val result = runCatching {
                    NetworkClient.default.newCall(request).execute().use { response ->
                        val raw = response.body?.string().orEmpty()
                        when (response.code) {
                            200 -> {
                                val json = JSONObject(raw)
                                UserPreferences.traktAccessToken = json.optString("access_token")
                                UserPreferences.traktRefreshToken = json.optString("refresh_token")
                                UserPreferences.traktEnabled = true
                                true
                            }
                            400 -> {
                                // pending / slow_down — keep polling
                                false
                            }
                            else -> {
                                Log.w(TAG, "device token HTTP ${response.code}: ${raw.take(80)}")
                                null
                            }
                        }
                    }
                }.getOrNull()
                when (result) {
                    true -> return@withContext true
                    null -> return@withContext false
                    false -> Unit
                }
            }
            false
        }

    private suspend fun scrobbleMovie(action: String, ids: TraktIds, progress: Double): Boolean =
        withContext(Dispatchers.IO) {
            if (!TraktConfig.configured() || ids.isEmpty()) return@withContext false
            val body = JSONObject()
                .put("movie", JSONObject().put("ids", ids.toJson()))
                .put("progress", progress.coerceIn(0.0, 100.0))
                .toString()
            post("/scrobble/$action", body)
        }

    private suspend fun scrobbleEpisode(action: String, ref: TraktEpisodeRef, progress: Double): Boolean =
        withContext(Dispatchers.IO) {
            if (!TraktConfig.configured()) return@withContext false
            if (ref.showIds.isEmpty() && ref.episodeIds.isEmpty()) return@withContext false
            val episode = JSONObject()
                .put("season", ref.season)
                .put("number", ref.number)
            if (!ref.episodeIds.isEmpty()) {
                episode.put("ids", ref.episodeIds.toJson())
            }
            val body = JSONObject()
                .put("show", JSONObject().put("ids", ref.showIds.toJson()))
                .put("episode", episode)
                .put("progress", progress.coerceIn(0.0, 100.0))
                .toString()
            post("/scrobble/$action", body)
        }

    private fun post(path: String, jsonBody: String): Boolean {
        return runCatching {
            val request = Request.Builder()
                .url(TraktConfig.API_BASE + path)
                .post(jsonBody.toRequestBody(jsonMedia))
                .apply { TraktConfig.authHeaders().forEach { (k, v) -> header(k, v) } }
                .build()
            NetworkClient.default.newCall(request).execute().use { response ->
                val ok = response.isSuccessful
                if (!ok) Log.w(TAG, "Trakt $path → ${response.code}")
                ok
            }
        }.getOrElse {
            Log.w(TAG, "Trakt $path failed: ${it.message}")
            false
        }
    }

    private fun getRaw(path: String): String {
        val request = Request.Builder()
            .url(TraktConfig.API_BASE + path)
            .get()
            .apply { TraktConfig.authHeaders().forEach { (k, v) -> header(k, v) } }
            .build()
        return NetworkClient.default.newCall(request).execute().use { it.body?.string().orEmpty() }
    }

    private fun getJson(path: String): JSONObject {
        val raw = getRaw(path)
        return if (raw.trimStart().startsWith("[")) {
            JSONObject().put("items", JSONArray(raw))
        } else {
            runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
        }
    }
}
