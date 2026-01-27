package com.streamflixreborn.streamflix.extractors

import androidx.media3.common.MimeTypes
import com.streamflixreborn.streamflix.models.Video
import com.streamflixreborn.streamflix.utils.DnsResolver
import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

class GxPlayerExtractor : Extractor() {

    override val name = "GxPlayer"
    override val mainUrl = "https://watch.gxplayer.xyz"
    
    companion object {
        private const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    override suspend fun extract(link: String): Video {
        val service = GxPlayerService.build(mainUrl)
        val document = service.get(link, DEFAULT_USER_AGENT, mainUrl)

        val scriptContent = document.select("script").find { 
            it.html().contains("var video =") 
        }?.html() ?: throw Exception("Video script not found")

        val id = Regex("\"id\":\"([^\"]+)\"").find(scriptContent)?.groupValues?.get(1) ?: ""
        val uid = Regex("\"uid\":\"([^\"]+)\"").find(scriptContent)?.groupValues?.get(1) ?: ""
        val md5 = Regex("\"md5\":\"([^\"]+)\"").find(scriptContent)?.groupValues?.get(1) ?: ""
        val status = Regex("\"status\":\"([^\"]+)\"").find(scriptContent)?.groupValues?.get(1) ?: ""

        if (uid.isEmpty() || md5.isEmpty() || id.isEmpty()) {
            throw Exception("Could not extract video parameters")
        }

        val videoUrl = "$mainUrl/m3u8/$uid/$md5/master.txt?s=1&id=$id&cache=$status"

        return Video(
            source = videoUrl,
            headers = mapOf(
                "User-Agent" to DEFAULT_USER_AGENT,
                "Referer" to mainUrl
            ),
            type = MimeTypes.APPLICATION_M3U8
        )
    }

    interface GxPlayerService {
        @GET
        suspend fun get(
            @Url url: String, 
            @Header("User-Agent") userAgent: String, 
            @Header("Referer") referer: String
        ): Document

        companion object {
            fun build(baseUrl: String): GxPlayerService {
                val client = OkHttpClient.Builder()
                    .dns(DnsResolver.doh)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .build()

                return Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .addConverterFactory(JsoupConverterFactory.create())
                    .client(client)
                    .build()
                    .create(GxPlayerService::class.java)
            }
        }
    }
}
