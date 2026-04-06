package com.streamflixreborn.streamflix.extractors

import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import com.streamflixreborn.streamflix.models.Video
import androidx.media3.common.MimeTypes
import okhttp3.ResponseBody
import org.jsoup.nodes.Document
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Streaming
import retrofit2.http.Url

class ZillaExtractor : Extractor() {

    override val name = "Zilla"
    override val mainUrl = "https://player.zilla-networks.com"

    override suspend fun extract(link: String): Video {
        try {
            val id = link.substringAfterLast("/")


            return Video(
                source = "$mainUrl/m3u8/$id",
                type = MimeTypes.APPLICATION_M3U8,
                headers = mapOf(
                    "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64; rv:148.0) Gecko/20100101 Firefox/148.0",
                    "Accept" to "*/*",
                    "Accept-Language" to "es-MX,es-ES;q=0.9,es;q=0.8,en-US;q=0.7,en;q=0.6",
                    "Referer" to link,
                    "Origin" to mainUrl,
                    "Connection" to "keep-alive"
                )
            )

        } catch (e: Exception) {
            throw Exception("ZillaExtractor failed: ${e.message}", e)
        }
    }
}