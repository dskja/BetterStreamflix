package com.dskja.betterstreamflix.platform.debrid

import android.util.Log
import com.dskja.betterstreamflix.utils.NetworkClient
import com.dskja.betterstreamflix.utils.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONObject

/**
 * Real-Debrid API client (token from settings).
 * https://api.real-debrid.com/
 */
class RealDebridClient(
    private val tokenProvider: () -> String = { UserPreferences.realDebridToken },
) : DebridService {
    override val name: String = "Real-Debrid"

    companion object {
        private const val TAG = "RealDebrid"
        private const val API = "https://api.real-debrid.com/rest/1.0"
    }

    override suspend fun isAuthenticated(): Boolean =
        tokenProvider().trim().isNotEmpty()

    override suspend fun unrestrict(link: String): DebridResult = withContext(Dispatchers.IO) {
        val token = tokenProvider().trim()
        if (token.isEmpty()) return@withContext DebridResult.Failure("Real-Debrid token missing")
        runCatching {
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
            Log.w(TAG, "unrestrict failed: ${it.message}")
            DebridResult.Failure(it.message ?: "unrestrict failed")
        }
    }

    override suspend fun resolveMagnet(magnet: String): DebridResult = withContext(Dispatchers.IO) {
        val token = tokenProvider().trim()
        if (token.isEmpty()) return@withContext DebridResult.Failure("Real-Debrid token missing")
        runCatching {
            val addBody = FormBody.Builder().add("magnet", magnet).build()
            val addReq = Request.Builder()
                .url("$API/torrents/addMagnet")
                .header("Authorization", "Bearer $token")
                .post(addBody)
                .build()
            val id = NetworkClient.default.newCall(addReq).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext DebridResult.Failure("addMagnet HTTP ${response.code}")
                }
                JSONObject(raw).optString("id")
            }
            if (id.isBlank()) return@withContext DebridResult.Failure("No torrent id")
            DebridResult.Pending(id, "Magnet added — select files / wait for RD cache")
        }.getOrElse {
            DebridResult.Failure(it.message ?: "magnet resolve failed")
        }
    }
}
