package com.dskja.betterstreamflix.platform.jellyfin

import com.dskja.betterstreamflix.utils.NetworkClient
import com.dskja.betterstreamflix.utils.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Jellyfin REST client (Items + PlaybackInfo + Auth).
 */
class JellyfinApi(
    private val baseUrlProvider: () -> String = { UserPreferences.jellyfinBaseUrl },
    private val tokenProvider: () -> String = { UserPreferences.jellyfinAccessToken },
    private val userIdProvider: () -> String = { UserPreferences.jellyfinUserId },
) {
    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    fun configured(): Boolean {
        val base = baseUrlProvider().trim().trimEnd('/')
        return base.startsWith("http") &&
            tokenProvider().isNotBlank() &&
            userIdProvider().isNotBlank()
    }

    private fun base(): String = baseUrlProvider().trim().trimEnd('/')

    private fun authHeaders(): Map<String, String> {
        val token = tokenProvider().trim()
        val deviceId = UserPreferences.jellyfinDeviceId.ifBlank {
            UUID.randomUUID().toString().also { UserPreferences.jellyfinDeviceId = it }
        }
        val auth = "MediaBrowser Client=\"BetterStreamflix\", Device=\"Android\", " +
            "DeviceId=\"$deviceId\", Version=\"1.0.0\", Token=\"$token\""
        return mapOf(
            "Authorization" to auth,
            "X-Emby-Token" to token,
            "Accept" to "application/json",
        )
    }

    /**
     * Username/password login → stores userId + accessToken in UserPreferences.
     */
    suspend fun authenticateByName(username: String, password: String): Boolean =
        withContext(Dispatchers.IO) {
            val deviceId = UserPreferences.jellyfinDeviceId.ifBlank {
                UUID.randomUUID().toString().also { UserPreferences.jellyfinDeviceId = it }
            }
            val body = JSONObject()
                .put("Username", username)
                .put("Pw", password)
                .toString()
                .toRequestBody(jsonMedia)
            val authHeader =
                "MediaBrowser Client=\"BetterStreamflix\", Device=\"Android\", " +
                    "DeviceId=\"$deviceId\", Version=\"1.0.0\""
            val request = Request.Builder()
                .url("${base()}/Users/AuthenticateByName")
                .post(body)
                .header("Authorization", authHeader)
                .header("Content-Type", "application/json")
                .build()
            NetworkClient.default.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) return@use false
                val json = JSONObject(raw)
                val token = json.optString("AccessToken")
                val user = json.optJSONObject("User")
                val userId = user?.optString("Id").orEmpty()
                if (token.isBlank() || userId.isBlank()) return@use false
                UserPreferences.jellyfinAccessToken = token
                UserPreferences.jellyfinUserId = userId
                true
            }
        }

    suspend fun resumeItems(limit: Int = 20): JSONArray = withContext(Dispatchers.IO) {
        getJson("/Users/${userIdProvider()}/Items/Resume?Limit=$limit&MediaTypes=Video")
            .optJSONArray("Items") ?: JSONArray()
    }

    suspend fun latestMovies(limit: Int = 20): JSONArray = withContext(Dispatchers.IO) {
        getJson(
            "/Users/${userIdProvider()}/Items/Latest?Limit=$limit&IncludeItemTypes=Movie",
        ).optJSONArray("Items") ?: JSONArray()
    }

    suspend fun latestSeries(limit: Int = 20): JSONArray = withContext(Dispatchers.IO) {
        getJson(
            "/Users/${userIdProvider()}/Items/Latest?Limit=$limit&IncludeItemTypes=Series",
        ).optJSONArray("Items") ?: JSONArray()
    }

    suspend fun libraryItems(
        includeTypes: String,
        startIndex: Int,
        limit: Int,
    ): JSONArray = withContext(Dispatchers.IO) {
        getJson(
            "/Users/${userIdProvider()}/Items?IncludeItemTypes=$includeTypes" +
                "&Recursive=true&SortBy=DateCreated&SortOrder=Descending" +
                "&StartIndex=$startIndex&Limit=$limit",
        ).optJSONArray("Items") ?: JSONArray()
    }

    suspend fun search(query: String, limit: Int = 30): JSONArray = withContext(Dispatchers.IO) {
        val q = java.net.URLEncoder.encode(query, Charsets.UTF_8.name())
        getJson(
            "/Users/${userIdProvider()}/Items?SearchTerm=$q&Recursive=true" +
                "&IncludeItemTypes=Movie,Series&Limit=$limit",
        ).optJSONArray("Items") ?: JSONArray()
    }

    suspend fun item(id: String): JSONObject = withContext(Dispatchers.IO) {
        getJson("/Users/${userIdProvider()}/Items/$id")
    }

    suspend fun seasons(seriesId: String): JSONArray = withContext(Dispatchers.IO) {
        getJson("/Shows/$seriesId/Seasons?userId=${userIdProvider()}")
            .optJSONArray("Items") ?: JSONArray()
    }

    suspend fun episodesForSeries(seriesId: String, seasonId: String): JSONArray =
        withContext(Dispatchers.IO) {
            getJson(
                "/Shows/$seriesId/Episodes?seasonId=$seasonId&userId=${userIdProvider()}",
            ).optJSONArray("Items") ?: JSONArray()
        }

    suspend fun playbackUrl(itemId: String): String? = withContext(Dispatchers.IO) {
        val profile = JSONObject()
            .put("MaxStreamingBitrate", 120_000_000)
            .put("DirectPlayProfiles", JSONArray().put(
                JSONObject().put("Type", "Video"),
            ))
            .put("TranscodingProfiles", JSONArray())
        val body = JSONObject()
            .put("DeviceProfile", profile)
            .toString()
            .toRequestBody(jsonMedia)
        val request = Request.Builder()
            .url("${base()}/Items/$itemId/PlaybackInfo?UserId=${userIdProvider()}")
            .post(body)
            .apply { authHeaders().forEach { (k, v) -> header(k, v) } }
            .build()
        NetworkClient.default.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) return@use null
            val media = JSONObject(raw).optJSONArray("MediaSources")?.optJSONObject(0)
            val sourceId = media?.optString("Id").orEmpty()
            if (sourceId.isBlank()) return@use null
            // Prefer authenticated stream endpoint over raw filesystem Path.
            "${base()}/Videos/$itemId/stream?static=true&MediaSourceId=$sourceId" +
                "&api_key=${tokenProvider()}"
        }
    }

    private fun getJson(path: String): JSONObject {
        val request = Request.Builder()
            .url(base() + path)
            .get()
            .apply { authHeaders().forEach { (k, v) -> header(k, v) } }
            .build()
        return NetworkClient.default.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("Jellyfin HTTP ${response.code} for $path")
            }
            if (raw.trimStart().startsWith("[")) {
                JSONObject().put("Items", JSONArray(raw))
            } else {
                JSONObject(raw)
            }
        }
    }
}
