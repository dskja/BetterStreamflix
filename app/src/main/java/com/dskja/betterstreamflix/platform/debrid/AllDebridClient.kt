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
 * AllDebrid link unlock + magnet status poll.
 * https://docs.alldebrid.com/
 */
class AllDebridClient(
    private val keyProvider: () -> String = { UserPreferences.allDebridApiKey },
    private val maxPollAttempts: Int = 12,
    private val pollDelayMs: Long = 1_500L,
) : DebridService {
    override val name: String = "AllDebrid"

    companion object {
        private const val TAG = "AllDebrid"
        private const val API = "https://api.alldebrid.com/v4"
    }

    override suspend fun isAuthenticated(): Boolean = keyProvider().trim().isNotEmpty()

    override suspend fun unrestrict(link: String): DebridResult = withContext(Dispatchers.IO) {
        val key = keyProvider().trim()
        if (key.isEmpty()) return@withContext DebridResult.Failure("AllDebrid API key missing")
        unlockLink(key, link)
    }

    override suspend fun resolveMagnet(magnet: String): DebridResult = withContext(Dispatchers.IO) {
        val key = keyProvider().trim()
        if (key.isEmpty()) return@withContext DebridResult.Failure("AllDebrid API key missing")
        runCatching {
            val body = FormBody.Builder().add("magnets[]", magnet).build()
            val request = Request.Builder()
                .url("$API/magnet/upload?agent=BetterStreamflix&apikey=$key")
                .post(body)
                .build()
            val id = NetworkClient.default.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext DebridResult.Failure("magnet upload HTTP ${response.code}")
                }
                JSONObject(raw).optJSONObject("data")
                    ?.optJSONArray("magnets")
                    ?.optJSONObject(0)
                    ?.opt("id")
                    ?.toString()
                    .orEmpty()
            }
            if (id.isBlank()) return@withContext DebridResult.Failure("No AllDebrid magnet id")
            val link = pollMagnetLink(key, id)
                ?: return@withContext DebridResult.Pending(id, "AllDebrid still downloading")
            unlockLink(key, link)
        }.getOrElse {
            Log.w(TAG, "magnet failed: ${it.message}")
            DebridResult.Failure(it.message ?: "AllDebrid magnet failed")
        }
    }

    private suspend fun pollMagnetLink(key: String, id: String): String? {
        repeat(maxPollAttempts) { attempt ->
            val request = Request.Builder()
                .url("$API/magnet/status?agent=BetterStreamflix&apikey=$key&id=$id")
                .get()
                .build()
            val data = NetworkClient.default.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) return null
                JSONObject(raw).optJSONObject("data")?.optJSONObject("magnets")
            } ?: return null
            val status = data.optString("statusCode").ifBlank { data.optString("status") }
            val ready = status.equals("Ready", ignoreCase = true) ||
                status == "4" ||
                data.optBoolean("ready")
            if (ready) {
                val links = data.optJSONArray("links")
                if (links != null && links.length() > 0) {
                    // Prefer largest file by size when available.
                    var bestLink = ""
                    var bestSize = -1L
                    for (i in 0 until links.length()) {
                        val entry = links.optJSONObject(i) ?: continue
                        val size = entry.optLong("size", 0L)
                        val link = entry.optString("link").ifBlank { entry.optString("download") }
                        if (link.isNotBlank() && size >= bestSize) {
                            bestSize = size
                            bestLink = link
                        }
                    }
                    if (bestLink.isNotBlank()) return bestLink
                }
                return data.optString("filename").takeIf { it.startsWith("http") }
            }
            Log.d(TAG, "magnet $id status=$status attempt=${attempt + 1}")
            delay(pollDelayMs)
        }
        return null
    }

    private fun unlockLink(key: String, link: String): DebridResult {
        return runCatching {
            val request = Request.Builder()
                .url(
                    "$API/link/unlock?agent=BetterStreamflix&apikey=$key" +
                        "&link=${java.net.URLEncoder.encode(link, "UTF-8")}",
                )
                .get()
                .build()
            NetworkClient.default.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@use DebridResult.Failure("HTTP ${response.code}")
                }
                val json = JSONObject(raw)
                if (json.optString("status") != "success") {
                    return@use DebridResult.Failure(
                        json.optJSONObject("error")?.optString("message") ?: "AllDebrid error",
                    )
                }
                val stream = json.optJSONObject("data")?.optString("link").orEmpty()
                if (stream.isBlank()) DebridResult.Failure("No AllDebrid stream URL")
                else DebridResult.Stream(stream)
            }
        }.getOrElse {
            DebridResult.Failure(it.message ?: "AllDebrid unlock failed")
        }
    }
}
