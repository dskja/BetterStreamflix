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

        val payloadRegex = Regex("""decodePayload\(['"]([^'"]+)['"]\)""")
        val payload = payloadRegex.find(html)?.groupValues?.get(1)
            ?: throw Exception("decodePayload not found in HTML")

        val decoded = String(Base64.decode(payload, Base64.DEFAULT))
        val jsonStr = decoded.substringAfter("|")
        val json = JSONObject(jsonStr)
        val videoUrl = json.optString("videoUrl").takeIf { it.isNotBlank() }
            ?: throw Exception("Video URL not found in payload")

        return Video(
            source = videoUrl,
            headers = mapOf(
                "User-Agent" to DEFAULT_USER_AGENT,
                "Referer" to mainUrl
            )
        )
    }
}

