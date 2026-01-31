package com.streamflixreborn.streamflix.extractors

import androidx.media3.common.MimeTypes
import com.streamflixreborn.streamflix.models.Video
import com.streamflixreborn.streamflix.utils.DnsResolver
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

class AfterDarkExtractor : Extractor() {

    override val name = "AfterDark"
    override val mainUrl = "https://afterdark.mom"

    data class listLinks(
        val meta: Meta,
        val sources: List<Source>
    )

    data class Meta(
        val title: String,
        val tmdbId: String,
        val originalTitle: String ?= "",
        val year: String ?= "0000"
    )
    data class Source(
        val label: String ?="",
        val file: String,
        val kind: String?="",
        val type: String? ="",
        val quality: String ?= "",
        val language: String ?= "",
        val proxied: Boolean? = false,
        val embed: Boolean? = false
    )

    fun listLinks.toServers(): List<Video.Server> {
        return sources.mapIndexed { index, data ->
            val name = buildString {
                append(data.label)

                val details = listOfNotNull(data.language, data.quality)
                if (details.isNotEmpty()) {
                    append(" (")
                    append(details.joinToString(" "))
                    append(")")
                }
            }
            /* try to guess video type */
            var type = if (data.type.isNullOrEmpty() == false && data.type != "null") data.type else data.kind
            if (data.file.endsWith(".m3u8", true) || data.file.contains(".m3u8?", true)) type = "m3u8"
            else if (data.file.endsWith(".mp4", true) || data.file.contains(".mp4?", true)) type = "mp4"

            Video.Server(
                id = "afdlink$index",
                name = name,
            )
                .also { server ->
                    server.video = Video(source = data.file,
                        type = if (arrayOf("hls", "tv", "movie", "m3u8").any {
                                it.equals(
                                    type ?: "unk",
                                    true
                                )}) MimeTypes.APPLICATION_M3U8 else MimeTypes.APPLICATION_MP4,
                        headers = mapOf("Referer" to Service.referer,
                                "User-Agent" to Service.USER_AGENT) )
                }
        }
    }

    private interface Service {
        companion object {
            const val USER_AGENT =
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"

            var referer = ""

            fun build(baseUrl: String): Service {
                referer = baseUrl
                val clientBuilder = OkHttpClient.Builder()
                    .readTimeout(30, TimeUnit.SECONDS)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .addNetworkInterceptor { chain ->
                        val request = chain.request()
                        val url = request.url
                        referer = "${url.scheme}://${url.host}/"
                        val newRequest = request.newBuilder()
                            .header("Referer", referer)
                            .build()
                        chain.proceed(newRequest)
                    }

                return Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .addConverterFactory( GsonConverterFactory.create())
                    .client(clientBuilder.dns(DnsResolver.doh).build())
                    .build()
                    .create(Service::class.java)
            }
        }

        @GET("api/sources/movies")
        suspend fun getMovieLinks(
            @Query("tmdbId") id: String,
            @Query("title") title: String="A",
            @Query("year") year: String="0000",
            @Query("originalTitle") originalTitle: String="0000",
            @Header("User-Agent") userAgent: String = USER_AGENT,
        ): listLinks

        @GET("api/sources/shows")
        suspend fun getTvShowLinks(
            @Query("tmdbId") id: String,
            @Query("season") sa: Int,
            @Query("episode") epi: Int,
            @Query("title") title: String="A",
            @Query("year") year: String="0000",
            @Query("originalTitle") originalTitle: String="0000",
            @Header("User-Agent") userAgent: String = USER_AGENT,
        ): listLinks
    }

    private val service = Service.build(mainUrl)

    override suspend fun extract(link: String): Video {
        throw Exception("None")
    }

    suspend fun servers(videoType: Video.Type): List<Video.Server> {
        return try {
            val ret = when(videoType) { is Video.Type.Movie -> {
                                            service.getMovieLinks( videoType.id)
                                        }
                                        is Video.Type.Episode -> {
                                            service.getTvShowLinks(videoType.tvShow.id, videoType.season.number, videoType.number)
                                        } }
            ret.toServers()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun server(videoType: Video.Type): Video.Server {
        return this.servers(videoType).first()
    }
}