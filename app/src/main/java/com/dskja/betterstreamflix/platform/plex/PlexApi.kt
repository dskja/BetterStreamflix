package com.dskja.betterstreamflix.platform.plex

import com.dskja.betterstreamflix.utils.NetworkClient
import com.dskja.betterstreamflix.utils.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * Minimal Plex Media Server client (JSON accept header).
 */
class PlexApi(
    private val baseUrlProvider: () -> String = { UserPreferences.plexBaseUrl },
    private val tokenProvider: () -> String = { UserPreferences.plexToken },
) {
    fun configured(): Boolean {
        val base = baseUrlProvider().trim().trimEnd('/')
        return base.startsWith("http") && tokenProvider().isNotBlank()
    }

    private fun base(): String = baseUrlProvider().trim().trimEnd('/')

    private fun headers(): Map<String, String> = mapOf(
        "Accept" to "application/json",
        "X-Plex-Token" to tokenProvider().trim(),
        "X-Plex-Product" to "BetterStreamflix",
        "X-Plex-Version" to "1.0.0",
        "X-Plex-Client-Identifier" to UserPreferences.plexClientId.ifBlank {
            java.util.UUID.randomUUID().toString().also { UserPreferences.plexClientId = it }
        },
    )

    suspend fun librarySections(): JSONArray = withContext(Dispatchers.IO) {
        getJson("/library/sections").optJSONObject("MediaContainer")
            ?.optJSONArray("Directory") ?: JSONArray()
    }

    suspend fun sectionItems(sectionKey: String, start: Int = 0, size: Int = 40): JSONArray =
        withContext(Dispatchers.IO) {
            getJson(
                "/library/sections/$sectionKey/all" +
                    "?X-Plex-Container-Start=$start&X-Plex-Container-Size=$size",
            ).optJSONObject("MediaContainer")
                ?.optJSONArray("Metadata") ?: JSONArray()
        }

    suspend fun onDeck(): JSONArray = withContext(Dispatchers.IO) {
        getJson("/library/onDeck").optJSONObject("MediaContainer")
            ?.optJSONArray("Metadata") ?: JSONArray()
    }

    suspend fun search(query: String): JSONArray = withContext(Dispatchers.IO) {
        val q = java.net.URLEncoder.encode(query, Charsets.UTF_8.name())
        getJson("/hubs/search?query=$q&limit=30")
            .optJSONObject("MediaContainer")
            ?.optJSONArray("Hub") ?: JSONArray()
    }

    suspend fun metadata(ratingKey: String): JSONObject = withContext(Dispatchers.IO) {
        getJson("/library/metadata/$ratingKey")
            .optJSONObject("MediaContainer")
            ?.optJSONArray("Metadata")
            ?.optJSONObject(0)
            ?: JSONObject()
    }

    suspend fun children(ratingKey: String): JSONArray = withContext(Dispatchers.IO) {
        getJson("/library/metadata/$ratingKey/children")
            .optJSONObject("MediaContainer")
            ?.optJSONArray("Metadata") ?: JSONArray()
    }

    /** Items matching a genre name across all movie/show sections. */
    suspend fun itemsByGenre(genre: String, start: Int = 0, size: Int = 40): JSONArray =
        withContext(Dispatchers.IO) {
            val enc = java.net.URLEncoder.encode(genre, Charsets.UTF_8.name())
            val sections = librarySections()
            val out = JSONArray()
            for (i in 0 until sections.length()) {
                val section = sections.optJSONObject(i) ?: continue
                val type = section.optString("type")
                if (type != "movie" && type != "show") continue
                val key = section.optString("key")
                if (key.isBlank()) continue
                val items = getJson(
                    "/library/sections/$key/all?genre=$enc" +
                        "&X-Plex-Container-Start=$start&X-Plex-Container-Size=$size",
                ).optJSONObject("MediaContainer")?.optJSONArray("Metadata") ?: continue
                for (j in 0 until items.length()) {
                    out.put(items.getJSONObject(j))
                }
            }
            out
        }

    suspend fun person(ratingKey: String): JSONObject = withContext(Dispatchers.IO) {
        getJson("/library/people/$ratingKey")
            .optJSONObject("MediaContainer")
            ?.optJSONArray("Metadata")
            ?.optJSONObject(0)
            ?: getJson("/library/metadata/$ratingKey")
                .optJSONObject("MediaContainer")
                ?.optJSONArray("Metadata")
                ?.optJSONObject(0)
            ?: JSONObject()
    }

    suspend fun personMedia(ratingKey: String, start: Int = 0, size: Int = 40): JSONArray =
        withContext(Dispatchers.IO) {
            getJson(
                "/library/people/$ratingKey/media" +
                    "?X-Plex-Container-Start=$start&X-Plex-Container-Size=$size",
            ).optJSONObject("MediaContainer")
                ?.optJSONArray("Metadata")
                ?: JSONArray()
        }

    fun streamUrl(partKey: String): String {
        val key = if (partKey.startsWith("/")) partKey else "/library/parts/$partKey"
        val sep = if (key.contains("?")) "&" else "?"
        return "${base()}$key${sep}X-Plex-Token=${tokenProvider().trim()}"
    }

    fun thumbUrl(path: String?): String? {
        if (path.isNullOrBlank()) return null
        val sep = if (path.contains("?")) "&" else "?"
        return "${base()}$path${sep}X-Plex-Token=${tokenProvider().trim()}"
    }

    private fun getJson(path: String): JSONObject {
        val request = Request.Builder()
            .url(base() + path)
            .get()
            .apply { headers().forEach { (k, v) -> header(k, v) } }
            .build()
        return NetworkClient.default.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) error("Plex HTTP ${response.code} for $path")
            JSONObject(raw)
        }
    }
}
