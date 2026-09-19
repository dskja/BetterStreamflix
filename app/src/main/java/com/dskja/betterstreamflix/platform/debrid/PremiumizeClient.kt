package com.dskja.betterstreamflix.platform.debrid

import android.util.Log
import com.dskja.betterstreamflix.utils.NetworkClient
import com.dskja.betterstreamflix.utils.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONObject

/**
 * Premiumize.me transfer/direct-download client.
 * https://www.premiumize.me/api
 */
class PremiumizeClient(
    private val keyProvider: () -> String = { UserPreferences.premiumizeApiKey },
    private val maxPollAttempts: Int = 12,
    private val pollDelayMs: Long = 1_500L,
) : DebridService {
    override val name: String = "Premiumize"

    companion object {
        private const val TAG = "Premiumize"
        private const val API = "https://www.premiumize.me/api"
        private val VIDEO_EXT = setOf(
            "mkv", "mp4", "avi", "m4v", "mov", "wmv", "flv", "webm", "ts", "m2ts",
        )
    }

    override suspend fun isAuthenticated(): Boolean = withContext(Dispatchers.IO) {
        val key = keyProvider().trim()
        if (key.isEmpty()) return@withContext false
        runCatching {
            val request = Request.Builder()
                .url("$API/account/info?apikey=$key")
                .get()
                .build()
            NetworkClient.default.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use false
                val raw = response.body?.string().orEmpty()
                JSONObject(raw).optString("status") != "error"
            }
        }.getOrDefault(false)
    }

    override suspend fun unrestrict(link: String): DebridResult = withContext(Dispatchers.IO) {
        val key = keyProvider().trim()
        if (key.isEmpty()) return@withContext DebridResult.Failure("Premiumize API key missing")
        directDl(key, link)
    }

    override suspend fun resolveMagnet(magnet: String): DebridResult = withContext(Dispatchers.IO) {
        val key = keyProvider().trim()
        if (key.isEmpty()) return@withContext DebridResult.Failure("Premiumize API key missing")
        // Prefer instant directdl; fall back to create + poll when caching is needed.
        when (val instant = directDl(key, magnet)) {
            is DebridResult.Stream -> instant
            else -> createAndPoll(key, magnet)
        }
    }

    private fun directDl(key: String, src: String): DebridResult {
        return runCatching {
            val body = FormBody.Builder()
                .add("apikey", key)
                .add("src", src)
                .build()
            val request = Request.Builder()
                .url("$API/transfer/directdl")
                .post(body)
                .build()
            NetworkClient.default.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@use DebridResult.Failure("HTTP ${response.code}")
                }
                val json = JSONObject(raw)
                if (json.optString("status") == "error") {
                    return@use DebridResult.Failure(json.optString("message", "Premiumize error"))
                }
                val location = pickBestContentLink(json).ifBlank {
                    json.optString("location")
                }
                if (location.isBlank()) DebridResult.Failure("No Premiumize stream URL")
                else DebridResult.Stream(location)
            }
        }.getOrElse {
            Log.w(TAG, "directdl failed: ${it.message}")
            DebridResult.Failure(it.message ?: "Premiumize failed")
        }
    }

    private suspend fun createAndPoll(key: String, src: String): DebridResult {
        return runCatching {
            val body = FormBody.Builder()
                .add("apikey", key)
                .add("src", src)
                .build()
            val create = Request.Builder()
                .url("$API/transfer/create")
                .post(body)
                .build()
            val id = NetworkClient.default.newCall(create).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@runCatching DebridResult.Failure("transfer create HTTP ${response.code}")
                }
                val json = JSONObject(raw)
                if (json.optString("status") == "error") {
                    return@runCatching DebridResult.Failure(json.optString("message", "create failed"))
                }
                json.optString("id").ifBlank { json.opt("id")?.toString().orEmpty() }
            }
            if (id.isBlank()) return@runCatching DebridResult.Failure("No Premiumize transfer id")
            repeat(maxPollAttempts) { attempt ->
                val listReq = Request.Builder()
                    .url("$API/transfer/list?apikey=$key")
                    .get()
                    .build()
                val readyFolder = NetworkClient.default.newCall(listReq).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    if (!response.isSuccessful) return@use null
                    val transfers = JSONObject(raw).optJSONArray("transfers") ?: return@use null
                    for (i in 0 until transfers.length()) {
                        val t = transfers.optJSONObject(i) ?: continue
                        if (t.optString("id") != id && t.opt("id")?.toString() != id) continue
                        val status = t.optString("status")
                        if (status.equals("finished", ignoreCase = true) ||
                            status.equals("ok", ignoreCase = true)
                        ) {
                            return@use t.optString("folder_id").ifBlank { t.optString("id") }
                        }
                        if (status.equals("error", ignoreCase = true)) return@use ""
                    }
                    null
                }
                when {
                    readyFolder == "" -> return@runCatching DebridResult.Failure("Premiumize transfer error")
                    !readyFolder.isNullOrBlank() -> {
                        val folderLink = folderBestLink(key, readyFolder)
                        if (folderLink.isNotBlank()) return@runCatching DebridResult.Stream(folderLink)
                        return@runCatching directDl(key, src)
                    }
                    else -> {
                        Log.d(TAG, "transfer $id pending attempt=${attempt + 1}")
                        delay(pollDelayMs)
                    }
                }
            }
            DebridResult.Pending(id, "Premiumize still caching")
        }.getOrElse {
            Log.w(TAG, "magnet poll failed: ${it.message}")
            DebridResult.Failure(it.message ?: "Premiumize magnet failed")
        }
    }

    private fun folderBestLink(key: String, folderId: String): String {
        val request = Request.Builder()
            .url("$API/folder/list?apikey=$key&id=$folderId")
            .get()
            .build()
        return NetworkClient.default.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) return ""
            val content = JSONObject(raw).optJSONArray("content") ?: return ""
            var best = ""
            var bestSize = -1L
            for (i in 0 until content.length()) {
                val item = content.optJSONObject(i) ?: continue
                val name = item.optString("name").lowercase()
                val ext = name.substringAfterLast('.', "")
                val size = item.optLong("size", 0L)
                val link = item.optString("link").ifBlank { item.optString("stream_link") }
                if (link.isBlank()) continue
                if (ext in VIDEO_EXT || size > 50_000_000L) {
                    if (size >= bestSize) {
                        bestSize = size
                        best = link
                    }
                }
            }
            best
        }
    }

    private fun pickBestContentLink(json: JSONObject): String {
        val content = json.optJSONArray("content") ?: return ""
        var best = ""
        var bestSize = -1L
        for (i in 0 until content.length()) {
            val item = content.optJSONObject(i) ?: continue
            val size = item.optLong("size", 0L)
            val link = item.optString("link").ifBlank { item.optString("stream_link") }
            if (link.isNotBlank() && size >= bestSize) {
                bestSize = size
                best = link
            }
        }
        return best
    }
}
