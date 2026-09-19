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
 * AllDebrid link unlock client.
 * https://docs.alldebrid.com/
 */
class AllDebridClient(
    private val keyProvider: () -> String = { UserPreferences.allDebridApiKey },
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
        runCatching {
            val request = Request.Builder()
                .url("$API/link/unlock?agent=BetterStreamflix&apikey=$key&link=${java.net.URLEncoder.encode(link, "UTF-8")}")
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
            Log.w(TAG, "unrestrict failed: ${it.message}")
            DebridResult.Failure(it.message ?: "AllDebrid failed")
        }
    }

    override suspend fun resolveMagnet(magnet: String): DebridResult = withContext(Dispatchers.IO) {
        val key = keyProvider().trim()
        if (key.isEmpty()) return@withContext DebridResult.Failure("AllDebrid API key missing")
        runCatching {
            val body = FormBody.Builder()
                .add("magnets[]", magnet)
                .build()
            val request = Request.Builder()
                .url("$API/magnet/upload?agent=BetterStreamflix&apikey=$key")
                .post(body)
                .build()
            NetworkClient.default.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@use DebridResult.Failure("magnet upload HTTP ${response.code}")
                }
                val magnets = JSONObject(raw).optJSONObject("data")?.optJSONArray("magnets")
                val id = magnets?.optJSONObject(0)?.opt("id")?.toString().orEmpty()
                if (id.isBlank()) DebridResult.Failure("No AllDebrid magnet id")
                else DebridResult.Pending(id, "Magnet uploaded — wait for AllDebrid cache")
            }
        }.getOrElse {
            DebridResult.Failure(it.message ?: "AllDebrid magnet failed")
        }
    }
}
