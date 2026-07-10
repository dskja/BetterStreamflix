package com.streamflixreborn.streamflix.extractors

import android.util.Base64
import com.streamflixreborn.streamflix.models.Video
import okhttp3.OkHttpClient
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.GET
import retrofit2.http.Url
import java.net.URL

class GuploadExtractor : Extractor() {
    override val name = "Gupload"
    override val mainUrl = "https://gupload.xyz"

    companion object {
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", DEFAULT_USER_AGENT)
                .build()
            chain.proceed(request)
        }
        .build()

    private val service = Retrofit.Builder()
        .baseUrl("$mainUrl/")
        .addConverterFactory(ScalarsConverterFactory.create())
        .client(client)
        .build()
        .create(GuploadService::class.java)

    private interface GuploadService {
        @GET
        suspend fun get(@Url url: String): String
    }

    override suspend fun extract(link: String): Video {
        val html = service.get(link)
        val baseUrl = runCatching {
            URL(link).let { "${it.protocol}://${it.host}" }
        }.getOrDefault(mainUrl)

        val videoUrl = findDirectConfigVideoUrl(html)
            ?: findLegacyConfigVideoUrl(html)
            ?: throw Exception("Video URL not found in Gupload page")

        return Video(
            source = videoUrl,
            headers = mapOf(
                "User-Agent" to DEFAULT_USER_AGENT,
                "Referer" to baseUrl
            )
        )
    }

    private fun findDirectConfigVideoUrl(html: String): String? {
        val configJson = findObjectLiteralAfter(html, "const config")
            ?: findObjectLiteralAfter(html, "var config")
            ?: findObjectLiteralAfter(html, "let config")

        return configJson
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
            ?.optString("videoUrl")
            ?.takeIf { it.isNotBlank() }
    }

    private fun findLegacyConfigVideoUrl(html: String): String? {
        val pContent = Regex("""_p\s*=\s*\[([^\]]+)]""")
            .find(html)
            ?.groupValues
            ?.get(1)
            ?: return null

        val key = Regex("""["']([^"']+)["']""")
            .findAll(pContent)
            .joinToString("") { it.groupValues[1] }
            .takeIf { it.isNotEmpty() }
            ?: return null

        val cfgEncoded = Regex("""_cfg\s*=\s*_(?:dp|xd)\(["']([^"']+)["']\)""")
            .find(html)
            ?.groupValues
            ?.get(1)
            ?: return null

        val cfgJsonStr = xd(cfgEncoded, key) ?: return null

        return runCatching { JSONObject(cfgJsonStr) }
            .getOrNull()
            ?.optString("videoUrl")
            ?.takeIf { it.isNotBlank() }
    }

    private fun findObjectLiteralAfter(text: String, marker: String): String? {
        val markerIndex = text.indexOf(marker)
        if (markerIndex == -1) return null

        val start = text.indexOf('{', markerIndex)
        if (start == -1) return null

        var depth = 0
        var inString: Char? = null
        var escaped = false

        for (i in start until text.length) {
            val char = text[i]

            if (inString != null) {
                if (escaped) {
                    escaped = false
                } else if (char == '\\') {
                    escaped = true
                } else if (char == inString) {
                    inString = null
                }
                continue
            }

            when (char) {
                '"', '\'' -> inString = char
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }

        return null
    }

    private fun xd(encoded: String, key: String): String? {
        return try {
            if ("~" !in encoded) return null

            val b64Data = encoded.substringAfter("~")
            val decodedBytes = Base64.decode(b64Data, Base64.DEFAULT)

            val result = StringBuilder()
            for (i in decodedBytes.indices) {
                val xorChar = decodedBytes[i].toInt() xor key[i % key.length].code
                result.append(xorChar.toChar())
            }
            result.toString()
        } catch (e: Exception) {
            null
        }
    }
}

