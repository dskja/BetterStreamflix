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
 * Premiumize.me transfer/direct-download client.
 * https://docs.premiumize.me/
 */
class PremiumizeClient(
    private val keyProvider: () -> String = { UserPreferences.premiumizeApiKey },
) : DebridService {
    override val name: String = "Premiumize"

    companion object {
        private const val TAG = "Premiumize"
        private const val API = "https://www.premiumize.me/api"
    }

    override suspend fun isAuthenticated(): Boolean = keyProvider().trim().isNotEmpty()

    override suspend fun unrestrict(link: String): DebridResult = withContext(Dispatchers.IO) {
        val key = keyProvider().trim()
        if (key.isEmpty()) return@withContext DebridResult.Failure("Premiumize API key missing")
        runCatching {
            val body = FormBody.Builder()
                .add("apikey", key)
                .add("src", link)
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
                val location = json.optString("location").ifBlank {
                    json.optJSONArray("content")?.optJSONObject(0)?.optString("link").orEmpty()
                }
                if (location.isBlank()) DebridResult.Failure("No Premiumize stream URL")
                else DebridResult.Stream(location)
            }
        }.getOrElse {
            Log.w(TAG, "unrestrict failed: ${it.message}")
            DebridResult.Failure(it.message ?: "Premiumize failed")
        }
    }

    override suspend fun resolveMagnet(magnet: String): DebridResult = unrestrict(magnet)
}
