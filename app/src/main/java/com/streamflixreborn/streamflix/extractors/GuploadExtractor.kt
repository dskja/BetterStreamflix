package com.streamflixreborn.streamflix.extractors

import android.util.Base64
import com.streamflixreborn.streamflix.models.Video
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.GET
import retrofit2.http.Url

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
        .baseUrl(mainUrl)
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

        // 1. Extract XOR key (_k)
        val pRegex = Regex("""_p=\[([^\]]+)\]""")
        val pContent = pRegex.find(html)?.groupValues?.get(1)
            ?: throw Exception("XOR key list _p not found in HTML")
        
        val key = Regex("""['"]([^'"]+)['Mult'"]""").findAll(pContent)
            .map { it.groupValues[1] }
            .joinToString("")

        // 2. Extract obfuscated config (_cfg)
        val cfgRegex = Regex("""_cfg\s*=\s*_(?:dp|xd)\(['"]([^'"]+)['"]\)""")
        val cfgEncoded = cfgRegex.find(html)?.groupValues?.get(1)
            ?: throw Exception("_cfg configuration not found")

        // 3. XOR Decoding
        val cfgJsonStr = xd(cfgEncoded, key)
            ?: throw Exception("Failed to decode _cfg configuration")

        val json = JSONObject(cfgJsonStr)
        val videoUrl = json.optString("videoUrl").takeIf { it.isNotBlank() }
            ?: throw Exception("Video URL not found in configuration")

        return Video(
            source = videoUrl,
            headers = mapOf(
                "User-Agent" to DEFAULT_USER_AGENT,
                "Referer" to mainUrl
            )
        )
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

