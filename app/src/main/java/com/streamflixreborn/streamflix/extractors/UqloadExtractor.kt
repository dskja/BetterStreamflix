package com.streamflixreborn.streamflix.extractors

import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import com.streamflixreborn.streamflix.models.Video
import com.streamflixreborn.streamflix.utils.DnsResolver
import okhttp3.OkHttpClient
import okhttp3.Interceptor
import org.jsoup.nodes.Document
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Url
import java.net.URL
import com.streamflixreborn.streamflix.utils.JsUnpacker

class UqloadExtractor : Extractor() {
    override val name = "Uqload"
    override val mainUrl = "https://uqload.cx"
    override val aliasUrls = listOf("https://uqload.is")

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }


    override suspend fun extract(link: String): Video {
        val baseUrl = URL(link).protocol + "://" + URL(link).host
        val service = Service.build(baseUrl)
        val document = service.getSource(url = link)

        val scripts = document.select("script[type=\"text/javascript\"]")
        val scriptContent = scripts.find { it.html().contains("eval(function(p,a,c,k,e,d)") }?.html()
            ?: throw Exception("Script with eval function not found")

        val scriptData = scriptContent
            .substringAfter("eval(function(p,a,c,k,e,d)")
            .let { "eval(function(p,a,c,k,e,d)$it" }

        val unpacked = JsUnpacker(scriptData).unpack() ?: throw Exception("Unpack failed")

        val fileRegex = Regex("""file\s*:\s*[\"']([^\"']+)[\"']""")
        val sourceUrl = fileRegex.find(unpacked)?.groupValues?.get(1)
            ?: throw Exception("Sources not found in unpacked script")

        return Video(
            source = sourceUrl,
            headers = mapOf(
                "Referer" to baseUrl,
                "User-Agent" to USER_AGENT
            )
        )
    }

    private interface Service {
        @GET
        suspend fun getSource(
            @Url url: String
        ): Document

        companion object {
            fun build(baseUrl: String): Service {
                val client = OkHttpClient.Builder()
                    .dns(DnsResolver.doh)
                    .addInterceptor { chain ->
                        val request = chain.request().newBuilder()
                            .header("User-Agent", USER_AGENT)
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
