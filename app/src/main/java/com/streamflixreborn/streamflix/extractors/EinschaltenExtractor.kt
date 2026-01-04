package com.streamflixreborn.streamflix.extractors

import com.streamflixreborn.streamflix.models.Video
import com.streamflixreborn.streamflix.utils.DnsResolver
import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

class EinschaltenExtractor : Extractor() {

    override val name = "Einschalten"
    override val mainUrl = "https://einschalten.in"

    private interface Service {
        companion object {
            fun build(baseUrl: String): Service {
                val clientBuilder = OkHttpClient.Builder()
                    .readTimeout(30, TimeUnit.SECONDS)
                    .connectTimeout(30, TimeUnit.SECONDS)

                return Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .addConverterFactory(JsoupConverterFactory.create())
                    .client(clientBuilder.dns(DnsResolver.doh).build())
                    .build()
                    .create(Service::class.java)
            }
        }

        @Headers(
            "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            "Content-Type: application/json"
        )
        @GET
        suspend fun getWatch(@Url url: String): ResponseBody
    }

    private val service = Service.build(mainUrl)

    fun server(videoType: Video.Type): Video.Server {
        return Video.Server(
            id = name,
            name = name,
            src = when (videoType) {
                is Video.Type.Movie -> "$mainUrl/api/movies/${videoType.id}/watch"
                is Video.Type.Episode -> ""
            },
        )
    }

    override suspend fun extract(link: String): Video {
        if (link.isEmpty()) throw Exception("Invalid link")

        val responseBody = service.getWatch(link)
        val body = responseBody.string()
        val json = JSONObject(body)
        val streamUrl = json.optString("streamUrl", "").trim()

        if (streamUrl.isBlank()) {
            throw Exception("No stream found")
        }

        return DoodLaExtractor().extract(streamUrl)
    }
}
