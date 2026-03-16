package com.streamflixreborn.streamflix.extractors

import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import com.streamflixreborn.streamflix.models.Video
import com.streamflixreborn.streamflix.utils.JsUnpacker
import org.jsoup.nodes.Document
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Url

class MixDropExtractor : Extractor() {

    override val name = "MixDrop"
    override val mainUrl = "https://mixdrop.co"
    override val aliasUrls = listOf(
        "https://mixdrop.bz",
        "https://mixdrop.ag",
        "https://mixdrop.ch",
        "https://mixdrop.to",
        "https://mixdrop.cv",
        "https://mxdrop.to",
        "https://mixdrop.club",
        "https://m1xdrop.net"
    )
    override val rotatingDomain = listOf(
        Regex("^md[3bfyz][a-z0-9]*\\.[a-z0-9]+", RegexOption.IGNORE_CASE)
    )

    companion object {
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }

    override suspend fun extract(link: String): Video {
        val service = Service.build(mainUrl)

        val document = service.get(
            url = link
                .replace("/f/", "/e/")
                .replace(".club/", ".ag/")
                .replace(Regex("^(https?://[^/]+/e/[^/?#]+).*$", RegexOption.IGNORE_CASE), "$1")
        )

        val html = document.toString()

        val packedJS = Regex("(eval\\(function\\(p,a,c,k,e,d\\)(.|\\n)*?)</script>")
            .find(html)
            ?.groupValues?.get(1)

        val script = packedJS?.let { JsUnpacker(it).unpack() } ?: html

        val srcRegex = Regex("""wurl.*?=.*?"(.*?)";""")
        val sourceUrl = srcRegex.find(script)?.groupValues?.get(1)
            ?: throw Exception("Source not found")

        val finalUrl = when {
            sourceUrl.startsWith("//") -> "https:$sourceUrl"
            sourceUrl.startsWith("http") -> sourceUrl
            else -> "https://$sourceUrl"
        }

        return Video(
            source = finalUrl,
            headers = mapOf(
                "User-Agent" to DEFAULT_USER_AGENT
            ),
            extraBuffering = true
        )
    }

    private interface Service {
        @GET
        @Headers(
            "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
            "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
            "Accept-Language: it-IT,it;q=0.9,en-US;q=0.8,en;q=0.7",
            "X-Requested-With: XMLHttpRequest"
        )
        suspend fun get(
            @Url url: String
        ): Document

        companion object {
            fun build(baseUrl: String): Service {
                val client = okhttp3.OkHttpClient.Builder()
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .addInterceptor { chain ->
                        val request = chain.request().newBuilder()
                            .header("Referer", baseUrl)
                            .build()
                        chain.proceed(request)
                    }
                    .build()

                val retrofit = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(client)
                    .addConverterFactory(JsoupConverterFactory.create())
                    .build()
                return retrofit.create(Service::class.java)
            }
        }
    }
}


