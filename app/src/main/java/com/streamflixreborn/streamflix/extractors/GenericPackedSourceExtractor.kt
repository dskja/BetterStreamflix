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
        val service = Service.build(baseUrl)
        var currentUrl = link
        var referer = "$refererUrl/"
        var source: String? = null

        for (attempt in 0 until MAX_REDIRECT_HOPS) {
            val document = service.get(
                url = currentUrl,
                referer = referer,
                userAgent = USER_AGENT
            )

            val html = document.html()
            source = findSource(html)
                ?: document.select("script")
                    .asSequence()
                    .mapNotNull { JsUnpacker(it.html()).unpack() }
                    .mapNotNull { findSource(it) }
                    .firstOrNull()

            if (source != null) {
                break
            }

            val redirectUrl = findRedirectUrl(html)
                ?: document.select("script")
                    .asSequence()
                    .mapNotNull { JsUnpacker(it.html()).unpack() }
                    .mapNotNull { findRedirectUrl(it) }
                    .firstOrNull()

            if (redirectUrl == null) {
                break
            }

            val resolvedUrl = URL(URL(currentUrl), redirectUrl).toString()
            referer = currentUrl
            currentUrl = resolvedUrl
        }

        val videoSource = source ?: throw Exception("Can't extract video source from $name")
        val playbackBaseUrl = URL(currentUrl).let { "${it.protocol}://${it.host}" }

        return Video(
            source = videoSource,
            headers = mapOf(
                "Referer" to "$playbackBaseUrl/",
                "Origin" to playbackBaseUrl,
                "User-Agent" to USER_AGENT
            )
        )
    }

    private fun findRedirectUrl(text: String): String? {
        val decoded = normalize(text)
        return redirectPatterns
            .asSequence()
            .mapNotNull { it.find(decoded)?.groupValues?.getOrNull(1) }
            .firstOrNull { it.isNotBlank() }
    }

    private fun findSource(text: String): String? {
        val decoded = normalize(text)

        return sourcePatterns
            .asSequence()
            .mapNotNull { it.find(decoded)?.groupValues?.getOrNull(1) }
            .firstOrNull { it.startsWith("http") }
    }

    private fun normalize(text: String): String {
        return text
            .replace("\\/", "/")
            .replace("\\u0026", "&")
            .replace("&amp;", "&")
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
        private const val MAX_REDIRECT_HOPS = 5
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

        private val redirectPatterns = listOf(
            Regex("""(?is)window\.location(?:\.href)?\.replace\(\s*['"]([^'"]+)['"]\s*\)"""),
            Regex("""(?is)window\.location(?:\.href)?\s*=\s*['"]([^'"]+)['"]"""),
            Regex("""(?is)location\.replace\(\s*['"]([^'"]+)['"]\s*\)"""),
            Regex("""(?is)location\.href\s*=\s*['"]([^'"]+)['"]"""),
            Regex("""(?is)<meta[^>]+http-equiv=["']refresh["'][^>]+content=["'][^"']*url=([^"'>]+)["']"""),
            Regex("""(?is)document\.write\([^)]*?href\s*=\s*["']([^"']+)["']""")
        )

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

    override suspend fun extract(link: String): Video {
        return runCatching { super.extract(link) }
            .getOrElse { error ->
                throw Exception(
                    "StreamSB did not return a player page. The current domain redirects to parked/ad content.",
                    error
                )
            }
    }
}

class Mp4UploadExtractor : GenericPackedSourceExtractor() {
    override val name = "Mp4Upload"
    override val mainUrl = "https://mp4upload.com"
    override val aliasUrls = listOf("https://www.mp4upload.com")
}

class StreamlareExtractor : GenericPackedSourceExtractor() {
    override val name = "Streamlare"
    override val mainUrl = "https://streamlare.com"

    override suspend fun extract(link: String): Video {
        return runCatching { super.extract(link) }
            .getOrElse { error ->
                throw Exception(
                    "Streamlare did not return a player page. The current domain is served by ParkLogic parking.",
                    error
                )
            }
    }
}

class NinjaStreamExtractor : GenericPackedSourceExtractor() {
    override val name = "NinjaStream"
    override val mainUrl = "https://ninjastream"
    override val rotatingDomain = listOf(Regex("""(^|\.)ninjastream(\.|/)"""))
}

class UchExtractor : GenericPackedSourceExtractor() {
    override val name = "Uch"
    override val mainUrl = "https://uch"
    override val rotatingDomain = listOf(Regex("""(^|\.)uch(\.|/)"""))
}
