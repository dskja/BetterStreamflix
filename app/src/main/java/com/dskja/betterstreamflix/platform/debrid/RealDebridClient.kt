package com.dskja.betterstreamflix.platform.debrid

import android.util.Log
import com.dskja.betterstreamflix.utils.NetworkClient
import com.dskja.betterstreamflix.utils.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * Real-Debrid API client (token from settings).
 * Magnet path: addMagnet → selectFiles → poll → unrestrict largest video.
 */
class RealDebridClient(
    private val tokenProvider: () -> String = { UserPreferences.realDebridToken },
    private val maxPollAttempts: Int = 12,
    private val pollDelayMs: Long = 1_500L,
) : DebridService {
    override val name: String = "Real-Debrid"

    companion object {
        private const val TAG = "RealDebrid"
        private const val API = "https://api.real-debrid.com/rest/1.0"
        private val VIDEO_EXT = setOf(
            "mkv", "mp4", "avi", "m4v", "mov", "wmv", "flv", "webm", "ts", "m2ts",
        )
    }

    override suspend fun isAuthenticated(): Boolean =
        tokenProvider().trim().isNotEmpty()

    override suspend fun unrestrict(link: String): DebridResult = withContext(Dispatchers.IO) {
        val token = tokenProvider().trim()
        if (token.isEmpty()) return@withContext DebridResult.Failure("Real-Debrid token missing")
        unrestrictInternal(token, link)
    }

    override suspend fun resolveMagnet(magnet: String): DebridResult = withContext(Dispatchers.IO) {
        val token = tokenProvider().trim()
        if (token.isEmpty()) return@withContext DebridResult.Failure("Real-Debrid token missing")
        runCatching {
            val id = addMagnet(token, magnet)
                ?: return@withContext DebridResult.Failure("No torrent id")
            selectBestFiles(token, id)
            val info = pollUntilReady(token, id)
                ?: return@withContext DebridResult.Pending(id, "Torrent still downloading on RD")
            val link = pickBestLink(info)
                ?: return@withContext DebridResult.Failure("No streamable link in torrent")
            unrestrictInternal(token, link)
        }.getOrElse {
            Log.w(TAG, "magnet resolve failed: ${it.message}")
            DebridResult.Failure(it.message ?: "magnet resolve failed")
        }
    }

    private fun addMagnet(token: String, magnet: String): String? {
        val body = FormBody.Builder().add("magnet", magnet).build()
        val request = Request.Builder()
            .url("$API/torrents/addMagnet")
            .header("Authorization", "Bearer $token")
            .post(body)
            .build()
        return NetworkClient.default.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                Log.w(TAG, "addMagnet HTTP ${response.code}: ${raw.take(120)}")
                return null
            }
            JSONObject(raw).optString("id").takeIf { it.isNotBlank() }
        }
    }

    private fun selectBestFiles(token: String, id: String) {
        val infoReq = Request.Builder()
            .url("$API/torrents/info/$id")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        val files = NetworkClient.default.newCall(infoReq).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) return
            JSONObject(raw).optJSONArray("files") ?: JSONArray()
        }
        val videoIds = buildList {
            for (i in 0 until files.length()) {
                val f = files.getJSONObject(i)
                val path = f.optString("path").lowercase()
                val ext = path.substringAfterLast('.', "")
                if (ext in VIDEO_EXT || f.optInt("bytes", 0) > 50_000_000) {
                    add(f.optInt("id").toString())
                }
            }
        }
        val filesParam = when {
            videoIds.isNotEmpty() -> videoIds.joinToString(",")
            else -> "all"
        }
        val body = FormBody.Builder().add("files", filesParam).build()
        val selectReq = Request.Builder()
            .url("$API/torrents/selectFiles/$id")
            .header("Authorization", "Bearer $token")
            .post(body)
            .build()
        NetworkClient.default.newCall(selectReq).execute().close()
    }

    private suspend fun pollUntilReady(token: String, id: String): JSONObject? {
        repeat(maxPollAttempts) { attempt ->
            val info = torrentInfo(token, id) ?: return null
            val status = info.optString("status")
            when (status) {
                "downloaded" -> return info
                "magnet_error", "error", "virus", "dead" -> {
                    Log.w(TAG, "torrent $id status=$status")
                    return null
                }
                else -> {
                    Log.d(TAG, "torrent $id status=$status attempt=${attempt + 1}")
                    delay(pollDelayMs)
                }
            }
        }
        return torrentInfo(token, id)?.takeIf { it.optString("status") == "downloaded" }
    }

    private fun torrentInfo(token: String, id: String): JSONObject? {
        val request = Request.Builder()
            .url("$API/torrents/info/$id")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        return NetworkClient.default.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) return null
            JSONObject(raw)
        }
    }

    private fun pickBestLink(info: JSONObject): String? {
        val links = info.optJSONArray("links") ?: return null
        val files = info.optJSONArray("files")
        // Match selected video files by size when RD returns parallel arrays.
        if (files != null && files.length() > 0) {
            var bestLink: String? = null
            var bestBytes = -1L
            val selectedVideos = mutableListOf<Pair<Int, Long>>()
            for (i in 0 until files.length()) {
                val f = files.optJSONObject(i) ?: continue
                if (f.optInt("selected", 0) != 1) continue
                val path = f.optString("path").lowercase()
                val ext = path.substringAfterLast('.', "")
                val bytes = f.optLong("bytes", 0L)
                if (ext in VIDEO_EXT || bytes > 50_000_000L) {
                    selectedVideos.add(f.optInt("id") to bytes)
                }
            }
            selectedVideos.sortByDescending { it.second }
            // links[] order roughly follows selected files; prefer largest by bytes.
            if (selectedVideos.isNotEmpty() && links.length() > 0) {
                for (i in 0 until links.length()) {
                    val link = links.optString(i)
                    if (link.isBlank()) continue
                    val bytes = selectedVideos.getOrNull(i)?.second
                        ?: selectedVideos.firstOrNull()?.second
                        ?: 0L
                    if (bytes >= bestBytes) {
                        bestBytes = bytes
                        bestLink = link
                    }
                }
                if (!bestLink.isNullOrBlank()) return bestLink
            }
        }
        // Fallback: last non-blank link (historically often the main video).
        for (i in links.length() - 1 downTo 0) {
            val link = links.optString(i)
            if (link.isNotBlank()) return link
        }
        return null
    }

    private fun unrestrictInternal(token: String, link: String): DebridResult {
        return runCatching {
            val body = FormBody.Builder().add("link", link).build()
            val request = Request.Builder()
                .url("$API/unrestrict/link")
                .header("Authorization", "Bearer $token")
                .post(body)
                .build()
            NetworkClient.default.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@use DebridResult.Failure("HTTP ${response.code}: ${raw.take(120)}")
                }
                val json = JSONObject(raw)
                val download = json.optString("download").ifBlank { json.optString("link") }
                if (download.isBlank()) {
                    DebridResult.Failure("No download URL in response")
                } else {
                    DebridResult.Stream(download)
                }
            }
        }.getOrElse {
            DebridResult.Failure(it.message ?: "unrestrict failed")
        }
    }
}
