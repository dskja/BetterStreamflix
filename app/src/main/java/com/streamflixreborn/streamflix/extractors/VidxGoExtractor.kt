package com.streamflixreborn.streamflix.extractors

import android.util.Base64
import android.util.Log
import android.net.Uri
import com.streamflixreborn.streamflix.models.Video
import com.streamflixreborn.streamflix.utils.DnsResolver
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.DelicateCoroutinesApi

object TokenManager {
    var latestQuery: String? = null
}

class VidxGoExtractor : Extractor() {
    override val name = "VidxGo"
    override val mainUrl = "https://v.vidxgo.co"

    @OptIn(DelicateCoroutinesApi::class)
    override suspend fun extract(link: String): Video {
        val client = OkHttpClient.Builder()
            .dns(DnsResolver.doh)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val uri = Uri.parse(link)
        val referer = "${uri.scheme}://${uri.host}/"
        val requestBuilder = Request.Builder()
            .url(link)
            .header("Referer", referer)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        
        if (!link.contains("/t/")) {
            requestBuilder.header("sec-fetch-dest", "iframe")
        }

        val request = requestBuilder.build()

        val response = client.newCall(request).execute()
        val html = response.body?.string() ?: throw Exception("Failed to get HTML from VidxGo")

        if (link.contains("/t/")) {
            // TV Series logic: the response is JSON-like with an "url" field
            val videoUrlRaw = Regex("\"url\"\\s*:\\s*\"([^\"]+)\"").find(html)?.groupValues?.get(1)
                ?: throw Exception("VidxGo: Could not find url in TV series response")
            val videoUrl = videoUrlRaw.replace("\\/", "/")
            
            var expireTime = Regex("\"expire\"\\s*:\\s*(\\d+)").find(html)?.groupValues?.get(1)?.toLongOrNull()
            
            val initialUri = android.net.Uri.parse(videoUrl)
            TokenManager.latestQuery = initialUri.encodedQuery
            Log.d("TokenManager", "[INIT] Initial token set. expire=${expireTime}, query=${TokenManager.latestQuery?.take(60)}...")

            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                while (true) {
                    val delayMs = if (expireTime != null) {
                        val remaining = expireTime!! - System.currentTimeMillis()
                        val delay = (remaining - 15_000).coerceAtLeast(5_000)
                        Log.d("TokenManager", "[SCHEDULE] Next refresh in ${delay / 1000}s (expiry in ${remaining / 1000}s)")
                        delay
                    } else {
                        Log.d("TokenManager", "[SCHEDULE] expire not found, retry in 150s")
                        150_000L
                    }

                    kotlinx.coroutines.delay(delayMs)
                    Log.d("TokenManager", "[REFRESH] Starting token refresh request at: $link")
                    try {
                        val updateRequest = Request.Builder()
                            .url(link)
                            .header("Referer", referer)
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                            .header("sec-fetch-dest", "empty")
                            .build()
                        val res = client.newCall(updateRequest).execute()
                        val statusCode = res.code
                        val isSuccessful = res.isSuccessful
                        val newHtml = res.body?.string()
                        res.close()
                        if (!isSuccessful) {
                            Log.w("TokenManager", "[REFRESH] HTTP response error: $statusCode")
                        }
                        
                        if (newHtml != null) {
                            expireTime = Regex("\"expire\"\\s*:\\s*(\\d+)").find(newHtml)?.groupValues?.get(1)?.toLongOrNull()
                            val newUrlStr = Regex("\"url\"\\s*:\\s*\"([^\"]+)\"").find(newHtml)?.groupValues?.get(1)?.replace("\\/", "/")
                            if (newUrlStr != null) {
                                val newUri = android.net.Uri.parse(newUrlStr)
                                TokenManager.latestQuery = newUri.encodedQuery
                                Log.d("TokenManager", "[REFRESH] New token saved. New expire=${expireTime}, query=${TokenManager.latestQuery?.take(60)}...")
                            } else {
                                Log.w("TokenManager", "[REFRESH] url not found in the response. Body: ${newHtml.take(200)}")
                            }
                        } else {
                            Log.w("TokenManager", "[REFRESH] Empty response body")
                        }
                    } catch (e: Exception) {
                        Log.e("TokenManager", "[REFRESH] Error during token refresh", e)
                        expireTime = System.currentTimeMillis() + 15_000L
                    }
                }
            }

            return Video(
                source = videoUrl,
                headers = mapOf(
                    "origin" to "https://v.vidxgo.co",
                    "referer" to "https://v.vidxgo.co/",
                    "sec-fetch-dest" to "empty",
                    "sec-fetch-site" to "cross-site"
                ),
                maintainToken = true
            )
        }

        // Search for the encrypted script blocks: (function() { var k = ... })()
        val scriptRegex = Regex("<script[\\s\\S]*?>[\\s\\S]*?\\(function\\(\\)\\s*\\{[\\s\\S]*?\\}\\s*\\)\\(\\);[\\s\\S]*?</script>", RegexOption.IGNORE_CASE)
        val scriptMatches = scriptRegex.findAll(html).toList()
        
        if (scriptMatches.size < 5) {
            Log.e("VidxGoExtractor", "Could not find enough encrypted scripts. Found: ${scriptMatches.size}")
            throw Exception("VidxGo: Could not find fifth encrypted script")
        }

        val targetScript = scriptMatches[4].value

        val k = Regex("var\\s+k\\s*=\\s*['\"]([^'\"]+)['\"]").find(targetScript)?.groupValues?.get(1)
            ?: throw Exception("VidxGo: Could not find key 'k'")
        val d = Regex("atob\\(['\"]([^'\"]+)['\"]\\)").find(targetScript)?.groupValues?.get(1)
            ?: throw Exception("VidxGo: Could not find data 'd'")

        val decodedD = Base64.decode(d, Base64.DEFAULT)
        val decrypted = ByteArray(decodedD.size)
        for (i in decodedD.indices) {
            decrypted[i] = ((decodedD[i].toInt() and 0xFF) xor (k[i % k.length].code and 0xFF)).toByte()
        }

        val decryptedText = String(decrypted)
        
        // Extract the source URL from currentSrc
        val videoUrlRaw = Regex("currentSrc\\s*=\\s*['\"]([^'\"]+)['\"]").find(decryptedText)?.groupValues?.get(1)
            ?: throw Exception("VidxGo: Could not find currentSrc in decrypted script")

        val videoUrl = videoUrlRaw.replace("\\/", "/")

        // For film URLs (https://v.vidxgo.co/$imdbId), derive the refresh URL using the /t/ endpoint
        val filmPathSegment = uri.pathSegments.firstOrNull()
        val filmRefreshUrl = if (filmPathSegment != null) "https://v.vidxgo.co/t/$filmPathSegment" else null

        val initialUri = android.net.Uri.parse(videoUrl)
        
        // Extract currentToken and currentExpire directly from the decrypted JS variables
        val currentToken = Regex("let\\s+currentToken\\s*=\\s*['\"]([^'\"]+)['\"]").find(decryptedText)?.groupValues?.get(1)
        val initialExpireRaw = Regex("let\\s+currentExpire\\s*=\\s*(\\d+)").find(decryptedText)?.groupValues?.get(1)?.toLongOrNull()
        
        TokenManager.latestQuery = initialUri.encodedQuery

        val initialExpireTime = initialExpireRaw
        Log.d("TokenManager", "[FILM-INIT] Token/Expiry extracted from JS. token=$currentToken, expireTime=${initialExpireTime}")

        if (filmRefreshUrl != null) {
            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                var expireTime: Long? = initialExpireTime
                while (true) {
                    val delayMs = if (expireTime != null) {
                        val remaining = expireTime!! - System.currentTimeMillis()
                        val delay = (remaining - 15_000).coerceAtLeast(5_000)
                        Log.d("TokenManager", "[FILM-SCHEDULE] Next refresh in ${delay / 1000}s")
                        delay
                    } else {
                        Log.d("TokenManager", "[FILM-SCHEDULE] First refresh in 150s")
                        150_000L
                    }

                    kotlinx.coroutines.delay(delayMs)
                    Log.d("TokenManager", "[FILM-REFRESH] Starting refresh at: $filmRefreshUrl")
                    try {
                        val updateRequest = Request.Builder()
                            .url(filmRefreshUrl)
                            .header("Referer", referer)
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                            .header("sec-fetch-dest", "empty")
                            .build()
                        val res = client.newCall(updateRequest).execute()
                        val statusCode = res.code
                        val isSuccessful = res.isSuccessful
                        val newHtml = res.body?.string()
                        res.close()
                        if (!isSuccessful) {
                            Log.w("TokenManager", "[FILM-REFRESH] HTTP response error: $statusCode")
                        }

                        if (newHtml != null) {
                            expireTime = Regex("\"expire\"\\s*:\\s*(\\d+)").find(newHtml)?.groupValues?.get(1)?.toLongOrNull()
                            val newUrlStr = Regex("\"url\"\\s*:\\s*\"([^\"]+)\"").find(newHtml)?.groupValues?.get(1)?.replace("\\/", "/")
                            if (newUrlStr != null) {
                                val newUri = android.net.Uri.parse(newUrlStr)
                                TokenManager.latestQuery = newUri.encodedQuery
                                Log.d("TokenManager", "[FILM-REFRESH] New token saved. expire=${expireTime}, query=${TokenManager.latestQuery?.take(60)}...")
                            } else {
                                Log.w("TokenManager", "[FILM-REFRESH] url not found. Body: ${newHtml.take(200)}")
                            }
                        } else {
                            Log.w("TokenManager", "[FILM-REFRESH] Empty body")
                        }
                    } catch (e: Exception) {
                        Log.e("TokenManager", "[FILM-REFRESH] Refresh error", e)
                        expireTime = System.currentTimeMillis() + 150_000L
                    }
                }
            }
        }

        return Video(
            source = videoUrl,
            headers = mapOf(
                "origin" to "https://v.vidxgo.co",
                "referer" to "https://v.vidxgo.co/",
                "sec-fetch-dest" to "empty",
                "sec-fetch-site" to "cross-site"
            ),
            maintainToken = true
        )
    }
}
