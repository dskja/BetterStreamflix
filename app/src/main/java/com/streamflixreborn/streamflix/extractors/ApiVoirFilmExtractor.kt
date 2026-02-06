package com.streamflixreborn.streamflix.extractors

import com.streamflixreborn.streamflix.models.Video
import com.streamflixreborn.streamflix.utils.DnsResolver
import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Url

class ApiVoirFilmExtractor : Extractor() {
    override val name = "ApiVoirFilm"
    override val mainUrl = "https://api.voirfilm.cam"
    override val aliasUrls = listOf("https://api.voirfilm.")

    private val service = Service.build(mainUrl)

    suspend fun expand(link: String, referer: String = mainUrl, suffix: String = ""): List<Video.Server> {
        val doc = service.get(link, referer)

        val links = doc.select("div.top ul.content > li").mapIndexedNotNull { idx, item ->
            val url = item.attr("data-url")
            if (url.isEmpty()) return@mapIndexedNotNull null
            val title = suffix+(item.text()?:"Server$idx")
            Video.Server( "${name}_${idx}",
                name = title,
                src = url
            )
        }

        return links
    }

    override suspend fun extract(link: String): Video {
        throw Exception("None")
    }

    private interface Service {
        @GET
        suspend fun get(
            @Url url: String,
            @Header("Referer") referer: String,
            @Header("User-agent") useragent: String = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)",
        ): Document

        companion object {

            fun build(baseUrl: String): Service {
                val client = OkHttpClient.Builder()
                    .dns(DnsResolver.doh).build()
                return Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(client)
                    .addConverterFactory(JsoupConverterFactory.create())
                    .build()
                    .create(Service::class.java)
            }
        }
    }
}