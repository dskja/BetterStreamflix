package com.streamflixreborn.streamflix.extractors

import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import com.streamflixreborn.streamflix.models.Video
import com.streamflixreborn.streamflix.utils.StringConverterFactory
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Url
import java.net.URI

open class DoodLaExtractor : Extractor() {

    override val name = "DoodStream"
    override val mainUrl = "https://dood.la"
    override val aliasUrls = listOf("https://dsvplay.com", "https://mikaylaarealike.com", "https://myvidplay.com")

    private val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

    override suspend fun extract(link: String): Video {
        val service = Service.build(mainUrl)

        val embedUrl = link.replace("/d/", "/e/")
        val response = service.get(embedUrl, link)
        val document = response.body() ?: throw Exception("Failed to load embed page")
        
        // Get the final URL after redirects to use the correct domain for pass_md5
        val finalUrl = response.raw().request.url.toString()
        val finalBaseUrl = getBaseUrl(finalUrl)

        val md5Path = Regex("/pass_md5/[^']*").find(document.toString())?.value
            ?: throw Exception("Could not find md5 path")
        
        val md5Url = finalBaseUrl + md5Path

        val videoPrefix = service.getString(md5Url, finalUrl)
        
        val url = videoPrefix +
                createHashTable() +
                "?token=${md5Url.substringAfterLast("/")}"

        return Video(
            source = url,
            headers = mapOf(
                "Referer" to finalBaseUrl
            )
        )
    }

    private fun createHashTable(): String {
        return buildString {
            repeat(10) {
                append(alphabet.random())
            }
        }
    }

    private fun getBaseUrl(url: String) = URI(url).let { "${it.scheme}://${it.host}" }


    class DoodLiExtractor : DoodLaExtractor() {
        override var mainUrl = "https://dood.li"
    }

    class DoodExtractor : DoodLaExtractor() {
        override val mainUrl = "https://vide0.net"
    }


    private interface Service {

        companion object {
            fun build(baseUrl: String): Service {
                val client = OkHttpClient.Builder()
                    .build()

                val retrofit = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .addConverterFactory(JsoupConverterFactory.create())
                    .addConverterFactory(StringConverterFactory.create())
                    .client(client)
                    .build()

                return retrofit.create(Service::class.java)
            }
        }


        @GET
        suspend fun get(
            @Url url: String,
            @Header("Referer") referer: String,
        ): Response<Document>

        @GET
        suspend fun getString(
            @Url url: String,
            @Header("Referer") referer: String,
        ): String
    }
}
