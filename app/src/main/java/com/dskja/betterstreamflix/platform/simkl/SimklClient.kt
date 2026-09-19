package com.dskja.betterstreamflix.platform.simkl

import android.util.Log
import com.dskja.betterstreamflix.utils.NetworkClient
import com.dskja.betterstreamflix.utils.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Simkl REST client — scrobble + optional history sync.
 * Auth: OAuth/PKCE token stored in UserPreferences (paste or future device flow).
 */
object SimklClient {
    private const val TAG = "SimklClient"
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    fun warm() {
        if (SimklConfig.configured()) {
            Log.d(TAG, "Simkl configured")
        }
    }

    suspend fun scrobble(
        action: SimklScrobbler.Action,
        progress: Double,
        imdbId: String?,
        tmdbId: String? = null,
        season: Int? = null,
        episode: Int? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        if (!SimklConfig.configured()) return@withContext false
        if (action == SimklScrobbler.Action.NONE) return@withContext false
        val path = when (action) {
            SimklScrobbler.Action.START -> "/scrobble/start"
            SimklScrobbler.Action.PAUSE -> "/scrobble/pause"
            SimklScrobbler.Action.STOP -> "/scrobble/stop"
            SimklScrobbler.Action.NONE -> return@withContext false
        }
        val body = JSONObject().put("progress", progress.coerceIn(0.0, 100.0))
        if (season != null && episode != null) {
            val show = JSONObject()
            if (!imdbId.isNullOrBlank()) show.put("ids", JSONObject().put("imdb", imdbId))
            else if (!tmdbId.isNullOrBlank()) show.put("ids", JSONObject().put("tmdb", tmdbId))
            else return@withContext false
            body.put("show", show)
            body.put("episode", JSONObject().put("season", season).put("number", episode))
        } else {
            val movie = JSONObject()
            val ids = JSONObject()
            if (!imdbId.isNullOrBlank()) ids.put("imdb", imdbId)
            if (!tmdbId.isNullOrBlank()) ids.put("tmdb", tmdbId)
            if (ids.length() == 0) return@withContext false
            movie.put("ids", ids)
            body.put("movie", movie)
        }
        post(path, body.toString())
    }

    private fun post(path: String, jsonBody: String): Boolean {
        return runCatching {
            val url = "${SimklConfig.API}$path?${SimklConfig.queryParams()}"
            val request = Request.Builder()
                .url(url)
                .post(jsonBody.toRequestBody(jsonMedia))
                .apply { SimklConfig.authHeaders().forEach { (k, v) -> header(k, v) } }
                .build()
            NetworkClient.default.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "POST $path HTTP ${response.code}")
                    false
                } else true
            }
        }.getOrElse {
            Log.w(TAG, "POST $path failed: ${it.message}")
            false
        }
    }

    fun clearTokens() {
        UserPreferences.simklAccessToken = ""
    }

    /**
     * GET /users/settings — validates client id + access token.
     * Returns account name / id when healthy.
     */
    suspend fun pingUser(): String? = withContext(Dispatchers.IO) {
        if (!SimklConfig.configured()) return@withContext null
        runCatching {
            val url = "${SimklConfig.API}/users/settings?${SimklConfig.queryParams()}"
            val request = Request.Builder()
                .url(url)
                .get()
                .apply { SimklConfig.authHeaders().forEach { (k, v) -> header(k, v) } }
                .build()
            NetworkClient.default.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.w(TAG, "pingUser HTTP ${response.code}")
                    return@use null
                }
                val user = JSONObject(raw).optJSONObject("user") ?: JSONObject(raw)
                user.optString("name")
                    .ifBlank { user.optString("username") }
                    .ifBlank { user.optString("id") }
                    .takeIf { it.isNotBlank() }
            }
        }.getOrElse {
            Log.w(TAG, "pingUser failed: ${it.message}")
            null
        }
    }
}
