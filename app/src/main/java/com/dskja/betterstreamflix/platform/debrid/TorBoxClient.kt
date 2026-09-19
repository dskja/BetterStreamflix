package com.dskja.betterstreamflix.platform.debrid

import android.util.Log
import com.dskja.betterstreamflix.utils.NetworkClient
import com.dskja.betterstreamflix.utils.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * TorBox API client (Real-Debrid-compatible surface).
 * Docs: https://api-docs.torbox.app/
 */
class TorBoxClient(
    private val keyProvider: () -> String = { UserPreferences.torBoxApiKey },
    private val maxPollAttempts: Int = 12,
    private val pollDelayMs: Long = 1_500L,
) : DebridService {
    override val name: String = "TorBox"

    companion object {
        private const val TAG = "TorBox"
        private const val API = "https://api.torbox.app/v1/api"
        private val jsonMedia = "application/json; charset=utf-8".toMediaType()
        private val VIDEO_EXT = setOf(
            "mkv", "mp4", "avi", "m4v", "mov", "wmv", "flv", "webm", "ts", "m2ts",
        )
    }

    override suspend fun isAuthenticated(): Boolean = keyProvider().trim().isNotEmpty()

    override suspend fun unrestrict(link: String): DebridResult = withContext(Dispatchers.IO) {
        val key = keyProvider().trim()
        if (key.isEmpty()) return@withContext DebridResult.Failure("TorBox API key missing")
        // Hosters: create web download then request DL when ready.
        runCatching {
            val body = JSONObject().put("link", link).toString().toRequestBody(jsonMedia)
            val create = Request.Builder()
                .url("$API/webdl/createwebdownload")
                .header("Authorization", "Bearer $key")
                .post(body)
                .build()
            val id = NetworkClient.default.newCall(create).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext DebridResult.Failure("TorBox create HTTP ${response.code}")
                }
                JSONObject(raw).optJSONObject("data")?.opt("webdownload_id")?.toString()
                    ?: JSONObject(raw).opt("data")?.toString()
            }
            if (id.isNullOrBlank()) return@withContext DebridResult.Failure("No TorBox download id")
            requestDownload(key, id, isWeb = true)
        }.getOrElse {
            Log.w(TAG, "unrestrict failed: ${it.message}")
            DebridResult.Failure(it.message ?: "TorBox unrestrict failed")
        }
    }

    override suspend fun resolveMagnet(magnet: String): DebridResult = withContext(Dispatchers.IO) {
        val key = keyProvider().trim()
        if (key.isEmpty()) return@withContext DebridResult.Failure("TorBox API key missing")
        runCatching {
            val cached = checkCached(key, magnet)
            val body = JSONObject()
                .put("magnet", magnet)
                .put("seed", 3)
                .put("allow_zip", false)
                .toString()
                .toRequestBody(jsonMedia)
            val create = Request.Builder()
                .url("$API/torrents/createtorrent")
                .header("Authorization", "Bearer $key")
                .post(body)
                .build()
            val torrentId = NetworkClient.default.newCall(create).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext DebridResult.Failure("TorBox magnet HTTP ${response.code}")
                }
                JSONObject(raw).optJSONObject("data")?.opt("torrent_id")?.toString()
                    ?: JSONObject(raw).opt("data")?.toString()
            }
            if (torrentId.isNullOrBlank()) {
                return@withContext DebridResult.Failure("No TorBox torrent id")
            }
            if (!cached) {
                val ready = pollTorrentReady(key, torrentId)
                if (!ready) {
                    return@withContext DebridResult.Pending(torrentId, "TorBox still downloading")
                }
            }
            val fileId = pickBestFileId(key, torrentId)
            requestDownload(key, torrentId, isWeb = false, fileId = fileId)
        }.getOrElse {
            Log.w(TAG, "magnet failed: ${it.message}")
            DebridResult.Failure(it.message ?: "TorBox magnet failed")
        }
    }

    private fun checkCached(key: String, magnet: String): Boolean {
        val hash = magnet.substringAfter("btih:", "")
            .substringBefore('&')
            .substringBefore('#')
            .lowercase()
            .takeIf { it.length >= 32 }
            ?: return false
        val request = Request.Builder()
            .url("$API/torrents/checkcached?hash=$hash&format=object")
            .header("Authorization", "Bearer $key")
            .get()
            .build()
        return NetworkClient.default.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) return false
            val data = JSONObject(raw).optJSONObject("data") ?: return false
            data.keys().asSequence().any { true }
        }
    }

    private suspend fun pollTorrentReady(key: String, id: String): Boolean {
        repeat(maxPollAttempts) { attempt ->
            val request = Request.Builder()
                .url("$API/torrents/mylist?id=$id")
                .header("Authorization", "Bearer $key")
                .get()
                .build()
            val ready = NetworkClient.default.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) return false
                val data = JSONObject(raw).opt("data")
                val obj = when (data) {
                    is JSONObject -> data
                    is org.json.JSONArray -> data.optJSONObject(0)
                    else -> null
                } ?: return false
                val downloadFinished = obj.optBoolean("download_finished") ||
                    obj.optBoolean("cached") ||
                    obj.optString("download_state").equals("completed", ignoreCase = true)
                downloadFinished
            }
            if (ready) return true
            Log.d(TAG, "torrent $id not ready attempt=${attempt + 1}")
            delay(pollDelayMs)
        }
        return false
    }

    private fun pickBestFileId(key: String, torrentId: String): Int? {
        val request = Request.Builder()
            .url("$API/torrents/mylist?id=$torrentId")
            .header("Authorization", "Bearer $key")
            .get()
            .build()
        return NetworkClient.default.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) return null
            val data = JSONObject(raw).opt("data")
            val obj = when (data) {
                is JSONObject -> data
                is org.json.JSONArray -> data.optJSONObject(0)
                else -> null
            } ?: return null
            val files = obj.optJSONArray("files") ?: return null
            var bestId: Int? = null
            var bestSize = -1L
            for (i in 0 until files.length()) {
                val f = files.optJSONObject(i) ?: continue
                val name = f.optString("name").lowercase()
                val ext = name.substringAfterLast('.', "")
                val size = f.optLong("size", 0L)
                val id = f.optInt("id", -1)
                if (id < 0) continue
                if (ext in VIDEO_EXT || size > 50_000_000L) {
                    if (size >= bestSize) {
                        bestSize = size
                        bestId = id
                    }
                }
            }
            bestId
        }
    }

    private fun requestDownload(
        key: String,
        id: String,
        isWeb: Boolean,
        fileId: Int? = null,
    ): DebridResult {
        val path = if (isWeb) {
            "$API/webdl/requestdl?token=$key&web_id=$id&redirect=false"
        } else {
            val fileQ = fileId?.let { "&file_id=$it" }.orEmpty()
            "$API/torrents/requestdl?token=$key&torrent_id=$id$fileQ&redirect=false"
        }
        val request = Request.Builder()
            .url(path)
            .header("Authorization", "Bearer $key")
            .get()
            .build()
        return NetworkClient.default.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return@use DebridResult.Failure("TorBox requestdl HTTP ${response.code}")
            }
            val url = JSONObject(raw).optString("data").ifBlank {
                JSONObject(raw).optJSONObject("data")?.optString("url").orEmpty()
            }
            if (url.isBlank() || !url.startsWith("http")) {
                DebridResult.Failure("No TorBox stream URL")
            } else {
                DebridResult.Stream(url)
            }
        }
    }
}
