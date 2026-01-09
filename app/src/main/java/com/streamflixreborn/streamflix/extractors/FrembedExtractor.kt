package com.streamflixreborn.streamflix.extractors

import com.streamflixreborn.streamflix.models.Video
import com.streamflixreborn.streamflix.utils.DnsResolver
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query
import java.util.concurrent.TimeUnit
import kotlin.text.replaceFirstChar

class FrembedExtractor : Extractor() {

    override val name = "Frembed"
    override val mainUrl = "https://frembed.life"

    data class listLinks (
        val link1: String?=null,
        val link2: String?=null,
        val link3: String?=null,
        val link4: String?=null,
        val link5: String?=null,
        val link6: String?=null,
        val link7: String?=null,
        val link1vostfr: String?=null,
        val link2vostfr: String?=null,
        val link3vostfr: String?=null,
        val link4vostfr: String?=null,
        val link5vostfr: String?=null,
        val link6vostfr: String?=null,
        val link7vostfr: String?=null,
        val link1vo: String?=null,
        val link2vo: String?=null,
        val link3vo: String?=null,
        val link4vo: String?=null,
        val link5vo: String?=null,
        val link6vo: String?=null,
        val link7vo: String?=null,
    )

    fun listLinks.toServers(): List<Video.Server> {
        return listOf(link1, link2, link3, link4, link5, link6, link7,
                                         link1vostfr, link2vostfr, link3vostfr, link4vostfr, link5vostfr, link6vostfr, link7vostfr,
                                         link1vo, link2vo, link3vo, link4vo, link5vo, link6vo, link7vo)
            .mapIndexedNotNull { index, data ->
                if (data.isNullOrEmpty()) return@mapIndexedNotNull null
                val lang = when { index < 7 -> "French"
                                  index < 14 -> "VOSTFR"
                                  else -> "VO" }
                val name = data.substringAfter("://")
                               .substringBefore(".")
                               .replace("crystaltreatmenteast", "voe")
                               .replace("myvidplay", "dood")
                               .replaceFirstChar{ it.uppercase() }  + " (${lang})"
                Video.Server ( id = "link$index",
                                    name = name,
                              src = data ) }
    }

    private interface Service {
        companion object {
            private const val USER_AGENT =
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"

            fun build(baseUrl: String): Service {
                val clientBuilder = OkHttpClient.Builder()
                    .readTimeout(30, TimeUnit.SECONDS)
                    .connectTimeout(30, TimeUnit.SECONDS)

                return Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .addConverterFactory( GsonConverterFactory.create())
                    .client(clientBuilder.dns(DnsResolver.doh).build())
                    .build()
                    .create(Service::class.java)
            }
        }

        @GET("api/films")
        suspend fun getMovieLinks(
            @Query("id") id: String,
            @Query("idType") idType: String = "tmdb",
            @Header("Referer") referer: String,
            @Header("User-Agent") userAgent: String = USER_AGENT,
            @Header("Content-Type") contentType: String = "application/json"
        ): listLinks

        @GET("api/series")
        suspend fun getTvShowLinks(
            @Query("id") id: String,
            @Query( "sa") sa: Int,
            @Query( "epi") epi: Int,
            @Query("idType") idType: String = "tmdb",
            @Header("Referer") referer: String,
            @Header("User-Agent") userAgent: String = USER_AGENT,
            @Header("Content-Type") contentType: String = "application/json"
        ): listLinks
    }

    private val service = Service.build(mainUrl)

    override suspend fun extract(link: String): Video {
        throw Exception("None")
    }

    suspend fun servers(videoType: Video.Type): List<Video.Server> {
        val ret = when(videoType) { is Video.Type.Movie -> service.getMovieLinks( videoType.id, referer = mainUrl)
                                    is Video.Type.Episode -> service.getTvShowLinks(videoType.tvShow.id, videoType.season.number, videoType.number, referer = mainUrl) }
        return ret.toServers()
    }

    suspend fun server(videoType: Video.Type): Video.Server {
        return this.servers(videoType).first()
    }
}