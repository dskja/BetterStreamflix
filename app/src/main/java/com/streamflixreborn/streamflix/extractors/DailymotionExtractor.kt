package com.streamflixreborn.streamflix.extractors

import java.util.Locale
import java.util.UUID
import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import com.streamflixreborn.streamflix.models.Video
import org.jsoup.nodes.Document
import retrofit2.Retrofit
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query
import com.streamflixreborn.streamflix.utils.DnsResolver
import com.google.gson.JsonParser

class DailymotionExtractor : Extractor() {

    override val name = "Dailymotion"
    override val mainUrl = "https://www.dailymotion.com"
    override val aliasUrls = listOf("https://geo.dailymotion.com")

    override suspend fun extract(link: String): Video {
        val id = link.substringAfterLast("/").substringAfter("video=")

        val service = Service.build(aliasUrls[0])
        val response = service.getJson(
            id = id,
            referer = "${aliasUrls[0]}/player/xtv3w.html?", 
            locale = Locale.getDefault().language,
            v1st = UUID.randomUUID().toString(),
            ts = (System.currentTimeMillis() / 1000).toString(),
            viewId = List(19) { (('a'..'z') + ('0'..'9')).random() }.joinToString("")
        )

        val json = JsonParser.parseString(response.string()).asJsonObject
        val manifestUrl = json.getAsJsonObject("qualities")
            ?.getAsJsonArray("auto")
            ?.get(0)?.asJsonObject
            ?.get("url")?.asString
            ?: throw Exception("Manifest URL not found")

        return Video(
            source = manifestUrl,
            headers = mapOf("Referer" to aliasUrls[0])
        )
    }

    private interface Service {
        companion object {
            private const val DEFAULT_USER_AGENT =
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"

            val client = OkHttpClient.Builder()
                .dns(DnsResolver.doh)
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("User-Agent", DEFAULT_USER_AGENT)
                        .build()
                    chain.proceed(request)
                }
                .build()

            fun build(baseUrl: String): Service {
                val retrofit = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(client)
                    .build()

                return retrofit.create(Service::class.java)
            }
        }

        @GET("video/{id}.json?legacy=true&player-id=xtv3w&is_native_app=0&app=com.dailymotion.neon&client_type=website&section_type=player&component_style=_&parallelCalls=1")
        suspend fun getJson(
            @Path("id") id: String,
            @Header("Referer") referer: String,
            @Query("locale") locale: String,
            @Query("dmV1st") v1st: String,
            @Query("dmTs") ts: String,
            @Query("dmViewId") viewId: String
        ): ResponseBody
    }
}
