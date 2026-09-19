package com.dskja.betterstreamflix.platform.subtitles

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
 * OpenSubtitles.com REST API v1 client.
 * Docs: https://opensubtitles.stoplight.io/docs/opensubtitles-api
 *
 * Requires Api-Key + descriptive User-Agent. Optional JWT login raises quotas.
 */
object OpenSubtitlesV1Client {
    private const val TAG = "OpenSubtitlesV1"
    private const val API = "https://api.opensubtitles.com/api/v1"
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    sealed class Result<out T> {
        data class Ok<T>(val value: T) : Result<T>()
        data class Err(val reason: String) : Result<Nothing>()
    }

    data class SubtitleHit(
        val id: String,
        val fileId: Int,
        val language: String,
        val release: String,
        val downloadCount: Int,
        val hearingImpaired: Boolean,
    )

    fun configured(): Boolean =
        runCatching { UserPreferences.openSubtitlesApiKey.isNotBlank() }.getOrDefault(false)

    fun signedIn(): Boolean =
        configured() && runCatching { UserPreferences.openSubtitlesJwt.isNotBlank() }.getOrDefault(false)

    fun clearSession() {
        UserPreferences.openSubtitlesJwt = ""
    }

    /**
     * Validates the API key (and optional JWT). Returns a short status string or null.
     */
    suspend fun ping(): String? = withContext(Dispatchers.IO) {
        val key = UserPreferences.openSubtitlesApiKey.trim()
        if (key.isBlank()) return@withContext null
        runCatching {
            val path = if (UserPreferences.openSubtitlesJwt.isNotBlank()) {
                "$API/infos/user"
            } else {
                "$API/infos"
            }
            val request = Request.Builder()
                .url(path)
                .get()
                .apply { applyAuthHeaders(this, key) }
                .build()
            NetworkClient.default.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.w(TAG, "ping HTTP ${response.code}")
                    return@use null
                }
                val json = JSONObject(raw).optJSONObject("data") ?: JSONObject(raw)
                val remaining = json.optJSONObject("remaining_downloads")
                    ?: json.opt("remaining_downloads")
                when {
                    remaining is JSONObject -> {
                        val vip = remaining.optInt("vip", -1)
                        val user = remaining.optInt("user", -1)
                        "OpenSubtitles OK · downloads left vip=$vip user=$user"
                    }
                    remaining != null -> "OpenSubtitles OK · remaining=$remaining"
                    else -> "OpenSubtitles OK"
                }
            }
        }.getOrElse {
            Log.w(TAG, "ping failed: ${it.message}")
            null
        }
    }

    fun userAgent(): String {
        val version = runCatching { UserPreferences.openSubtitlesUserAgent }.getOrDefault("")
            .ifBlank { "BetterStreamflix v1.1.0" }
        return version
    }

    suspend fun login(username: String, password: String): Result<String> =
        withContext(Dispatchers.IO) {
            val key = UserPreferences.openSubtitlesApiKey.trim()
            if (key.isBlank()) return@withContext Result.Err("OpenSubtitles API key missing")
            runCatching {
                val body = JSONObject()
                    .put("username", username)
                    .put("password", password)
                    .toString()
                    .toRequestBody(jsonMedia)
                val request = Request.Builder()
                    .url("$API/login")
                    .post(body)
                    .header("Api-Key", key)
                    .header("User-Agent", userAgent())
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .build()
                NetworkClient.default.newCall(request).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@use Result.Err("login HTTP ${response.code}: ${raw.take(120)}")
                    }
                    val token = JSONObject(raw).optString("token")
                    if (token.isBlank()) Result.Err("No JWT in login response")
                    else {
                        UserPreferences.openSubtitlesJwt = token
                        Result.Ok(token)
                    }
                }
            }.getOrElse {
                Log.w(TAG, "login failed: ${it.message}")
                Result.Err(it.message ?: "login failed")
            }
        }

    suspend fun search(
        tmdbId: Int? = null,
        imdbId: String? = null,
        query: String? = null,
        languages: String = "en",
        season: Int? = null,
        episode: Int? = null,
    ): Result<List<SubtitleHit>> = withContext(Dispatchers.IO) {
        val key = UserPreferences.openSubtitlesApiKey.trim()
        if (key.isBlank()) return@withContext Result.Err("OpenSubtitles API key missing")
        runCatching {
            val params = buildString {
                append("languages=").append(java.net.URLEncoder.encode(languages, "UTF-8"))
                if (tmdbId != null) append("&tmdb_id=").append(tmdbId)
                if (!imdbId.isNullOrBlank()) {
                    val clean = imdbId.removePrefix("tt")
                    append("&imdb_id=").append(clean)
                }
                if (!query.isNullOrBlank()) {
                    append("&query=").append(java.net.URLEncoder.encode(query, "UTF-8"))
                }
                if (season != null) append("&season_number=").append(season)
                if (episode != null) append("&episode_number=").append(episode)
            }
            val request = Request.Builder()
                .url("$API/subtitles?$params")
                .get()
                .apply { applyAuthHeaders(this, key) }
                .build()
            NetworkClient.default.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (response.code == 429) {
                    return@use Result.Err("Rate limited (429)")
                }
                if (!response.isSuccessful) {
                    return@use Result.Err("search HTTP ${response.code}")
                }
                val data = JSONObject(raw).optJSONArray("data") ?: org.json.JSONArray()
                val hits = buildList {
                    for (i in 0 until data.length()) {
                        val item = data.optJSONObject(i) ?: continue
                        val attrs = item.optJSONObject("attributes") ?: continue
                        val files = attrs.optJSONArray("files") ?: continue
                        val file = files.optJSONObject(0) ?: continue
                        val fileId = file.optInt("file_id", -1)
                        if (fileId < 0) continue
                        add(
                            SubtitleHit(
                                id = item.optString("id"),
                                fileId = fileId,
                                language = attrs.optString("language"),
                                release = attrs.optString("release").ifBlank {
                                    file.optString("file_name")
                                },
                                downloadCount = attrs.optInt("download_count"),
                                hearingImpaired = attrs.optBoolean("hearing_impaired"),
                            ),
                        )
                    }
                }
                Result.Ok(hits)
            }
        }.getOrElse {
            Log.w(TAG, "search failed: ${it.message}")
            Result.Err(it.message ?: "search failed")
        }
    }

    /**
     * Requests a temporary download URL for [fileId]. Caller streams the file.
     */
    suspend fun requestDownload(fileId: Int): Result<String> = withContext(Dispatchers.IO) {
        val key = UserPreferences.openSubtitlesApiKey.trim()
        if (key.isBlank()) return@withContext Result.Err("OpenSubtitles API key missing")
        runCatching {
            val body = JSONObject().put("file_id", fileId).toString().toRequestBody(jsonMedia)
            val request = Request.Builder()
                .url("$API/download")
                .post(body)
                .apply { applyAuthHeaders(this, key) }
                .header("Content-Type", "application/json")
                .build()
            NetworkClient.default.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@use Result.Err("download HTTP ${response.code}: ${raw.take(120)}")
                }
                val link = JSONObject(raw).optString("link")
                if (link.isBlank()) Result.Err("No download link")
                else Result.Ok(link)
            }
        }.getOrElse {
            Result.Err(it.message ?: "download failed")
        }
    }

    /** Build search query string for unit tests without network. */
    fun buildSearchQuery(
        tmdbId: Int? = null,
        imdbId: String? = null,
        query: String? = null,
        languages: String = "en",
        season: Int? = null,
        episode: Int? = null,
    ): String = buildString {
        append("languages=").append(java.net.URLEncoder.encode(languages, "UTF-8"))
        if (tmdbId != null) append("&tmdb_id=").append(tmdbId)
        if (!imdbId.isNullOrBlank()) {
            append("&imdb_id=").append(imdbId.removePrefix("tt"))
        }
        if (!query.isNullOrBlank()) {
            append("&query=").append(java.net.URLEncoder.encode(query, "UTF-8"))
        }
        if (season != null) append("&season_number=").append(season)
        if (episode != null) append("&episode_number=").append(episode)
    }

    fun parseSearchHits(rawJson: String): List<SubtitleHit> {
        val data = JSONObject(rawJson).optJSONArray("data") ?: return emptyList()
        return buildList {
            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue
                val attrs = item.optJSONObject("attributes") ?: continue
                val files = attrs.optJSONArray("files") ?: continue
                val file = files.optJSONObject(0) ?: continue
                val fileId = file.optInt("file_id", -1)
                if (fileId < 0) continue
                add(
                    SubtitleHit(
                        id = item.optString("id"),
                        fileId = fileId,
                        language = attrs.optString("language"),
                        release = attrs.optString("release").ifBlank {
                            file.optString("file_name")
                        },
                        downloadCount = attrs.optInt("download_count"),
                        hearingImpaired = attrs.optBoolean("hearing_impaired"),
                    ),
                )
            }
        }
    }

    private fun applyAuthHeaders(builder: Request.Builder, key: String) {
        builder.header("Api-Key", key)
        builder.header("User-Agent", userAgent())
        builder.header("Accept", "application/json")
        val jwt = UserPreferences.openSubtitlesJwt.trim()
        if (jwt.isNotBlank()) {
            builder.header("Authorization", "Bearer $jwt")
        }
    }
}
