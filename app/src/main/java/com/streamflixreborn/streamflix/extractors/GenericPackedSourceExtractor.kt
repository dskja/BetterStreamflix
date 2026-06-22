package com.streamflixreborn.streamflix.extractors

import com.streamflixreborn.streamflix.models.Video
import com.streamflixreborn.streamflix.utils.DnsResolver
import com.streamflixreborn.streamflix.utils.JsUnpacker
import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Url
import java.net.URL

abstract class GenericPackedSourceExtractor : Extractor() {

    protected open val refererUrl: String
        get() = mainUrl

    override suspend fun extract(link: String): Video {
        val baseUrl = URL(link).let { "${it.protocol}://${it.host}" }
        val document = Service.build(baseUrl).get(
            url = link,
            referer = "$refererUrl/",
            userAgent = USER_AGENT
        )
        val source = findSource(document.toString())
            ?: document.select("script")
                .asSequence()
                .mapNotNull { JsUnpacker(it.html()).unpack() }
                .mapNotNull { findSource(it) }
                .firstOrNull()
            ?: throw Exception("Can't extract video source from $name")

        return Video(
            source = source,
            headers = mapOf(
                "Referer" to "$baseUrl/",
                "Origin" to baseUrl,
                "User-Agent" to USER_AGENT
            )
        )
    }

    private fun findSource(text: String): String? {
        val decoded = text
            .replace("\\/", "/")
            .replace("\\u0026", "&")
            .replace("&amp;", "&")

        return sourcePatterns
            .asSequence()
            .mapNotNull { it.find(decoded)?.groupValues?.getOrNull(1) }
            .firstOrNull { it.startsWith("http") }
    }

    private interface Service {
        @GET
        suspend fun get(
            @Url url: String,
            @Header("Referer") referer: String,
            @Header("User-Agent") userAgent: String
        ): Document

        companion object {
            fun build(baseUrl: String): Service {
                val client = OkHttpClient.Builder()
                    .dns(DnsResolver.doh)
                    .build()
                return Retrofit.Builder()
                    .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
                    .client(client)
                    .addConverterFactory(JsoupConverterFactory.create())
                    .build()
                    .create(Service::class.java)
            }
        }
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

        private val sourcePatterns = listOf(
            Regex("""(?i)(?:file|src)\s*[:=]\s*["'](https?://[^"']+\.(?:m3u8|mp4)(?:\?[^"']*)?)["']"""),
            Regex("""(?i)sources?\s*[:=]\s*\[\s*["'](https?://[^"']+\.(?:m3u8|mp4)(?:\?[^"']*)?)["']"""),
            Regex("""(?i)["'](https?://[^"']+\.(?:m3u8|mp4)(?:\?[^"']*)?)["']""")
        )
    }
}

class StreamSBExtractor : GenericPackedSourceExtractor() {
    override val name = "StreamSB"
    override val mainUrl = "https://streamsb.net"
    override val aliasUrls = listOf(
        "https://sbembed.com",
        "https://sbplay.org",
        "https://sbrapid.com",
        "https://sbvideo.net",
        "https://ssbstream.net",
        "https://streamsss.net"
    )
}

class Mp4UploadExtractor : GenericPackedSourceExtractor() {
    override val name = "Mp4Upload"
    override val mainUrl = "https://mp4upload.com"
    override val aliasUrls = listOf("https://www.mp4upload.com")
}

class StreamlareExtractor : GenericPackedSourceExtractor() {
    override val name = "Streamlare"
    override val mainUrl = "https://streamlare.com"
}
