package com.streamflixreborn.streamflix.extractors

import android.util.Base64
import android.util.Log
import android.net.Uri
import com.streamflixreborn.streamflix.models.Video
import com.streamflixreborn.streamflix.utils.DnsResolver
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class VidxGoExtractor : Extractor() {
    override val name = "VidxGo"
    override val mainUrl = "https://v.vidxgo.co"

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
            return Video(
                source = videoUrl,
                headers = mapOf(
                    "origin" to "https://v.vidxgo.co",
                    "referer" to "https://v.vidxgo.co/",
                    "sec-fetch-dest" to "empty",
                    "sec-fetch-site" to "cross-site"
                )
            )
        }

        // Search for the encrypted script blocks: (function() { var k = ... })()
        val scriptRegex = Regex("<script[\\s\\S]*?>[\\s\\S]*?\\(function\\(\\)\\s*\\{[\\s\\S]*?\\}\\s*\\)\\(\\);[\\s\\S]*?</script>", RegexOption.IGNORE_CASE)
        val scriptMatches = scriptRegex.findAll(html).toList()
        
        if (scriptMatches.size < 3) {
            Log.e("VidxGoExtractor", "Could not find enough encrypted scripts. Found: ${scriptMatches.size}")
            throw Exception("VidxGo: Could not find third encrypted script")
        }

        val targetScript = scriptMatches[2].value

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

        return Video(
            source = videoUrl,
            headers = mapOf(
                "origin" to "https://v.vidxgo.co",
                "referer" to "https://v.vidxgo.co/",
                "sec-fetch-dest" to "empty",
                "sec-fetch-site" to "cross-site"
            )
        )
    }
}
