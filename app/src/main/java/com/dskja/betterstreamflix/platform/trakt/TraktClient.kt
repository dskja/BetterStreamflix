package com.dskja.betterstreamflix.platform.trakt

import android.content.Context
import android.util.Log
import com.dskja.betterstreamflix.utils.NetworkClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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

    suspend fun scrobbleStart(imdbId: String?, progress: Double): Boolean =
        scrobble("start", imdbId, progress)

    suspend fun scrobblePause(imdbId: String?, progress: Double): Boolean =
        scrobble("pause", imdbId, progress)

    suspend fun scrobbleStop(imdbId: String?, progress: Double): Boolean =
        scrobble("stop", imdbId, progress)

    suspend fun addToWatchlist(imdbId: String?, mediaType: String): Boolean =
        withContext(Dispatchers.IO) {
            if (!TraktConfig.configured() || imdbId.isNullOrBlank()) return@withContext false
            val body = JSONObject()
                .put(
                    if (mediaType == "show") "shows" else "movies",
                    org.json.JSONArray().put(
                        JSONObject().put("ids", JSONObject().put("imdb", imdbId)),
                    ),
                )
                .toString()
            post("/sync/watchlist", body)
        }

    private suspend fun scrobble(action: String, imdbId: String?, progress: Double): Boolean =
        withContext(Dispatchers.IO) {
            if (!TraktConfig.configured() || imdbId.isNullOrBlank()) return@withContext false
            val media = JSONObject()
                .put("ids", JSONObject().put("imdb", imdbId))
            val body = JSONObject()
                .put("movie", media)
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
                if (!ok) {
                    Log.w(TAG, "Trakt $path → ${response.code}")
                }
                ok
            }
        }.getOrElse {
            Log.w(TAG, "Trakt $path failed: ${it.message}")
            false
        }
    }
}
