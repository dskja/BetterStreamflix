package com.streamflixreborn.streamflix.extractors

import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import com.streamflixreborn.streamflix.models.Video
import androidx.media3.common.MimeTypes
import org.jsoup.nodes.Document
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.HeaderMap
import retrofit2.http.Url
import java.lang.Exception

class MP4UploadExtractor : Extractor() {

    override val name = "MP4Upload"
    override val mainUrl = "https://www.mp4upload.com"

    override suspend fun extract(link: String): Video {
        try {
            val service = Service.build(mainUrl)

            val response = service.getSource(
                url = link,
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                    "Referer" to "https://animeav1.com/",
                    "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,webp,*/*;q=0.8"
                )
            )

            val html = response.toString()

            val videoUrl = Regex("""src:\s*["'](https?://a[0-9]\.mp4upload\.com:183/[^"']+\.mp4)["']""").find(html)?.groupValues?.get(1)
                ?: Regex("""src:\s*["'](https?://[^"']+\.mp4)["']""").find(html)?.groupValues?.get(1)
                ?: throw Exception("No se encontró la URL del video en el HTML de MP4Upload")

            return Video(
                source = videoUrl,
                type = MimeTypes.VIDEO_MP4,
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                    "Referer" to "https://www.mp4upload.com/",
                    "Origin" to "https://www.mp4upload.com",
                    "Connection" to "keep-alive"
                )
            )

        } catch (e: Exception) {
            throw Exception("MP4UploadExtractor failed: ${e.message}", e)
        }
    }

    private interface Service {
        companion object {
            fun build(baseUrl: String): Service {
                val retrofit = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .addConverterFactory(JsoupConverterFactory.create())
                    .build()
                return retrofit.create(Service::class.java)
            }
        }

        @GET
        suspend fun getSource(
            @Url url: String,
            @HeaderMap headers: Map<String, String>
        ): Document
    }
}
