package com.streamflixreborn.streamflix.extractors

import android.util.Base64
import androidx.media3.common.MimeTypes
import com.streamflixreborn.streamflix.models.Video
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Url

class MoflixExtractor : Extractor() {

    override val name = "Moflix"
    override val mainUrl = "https://moflix-stream.xyz"

    suspend fun servers(videoType: Video.Type): List<Video.Server> {
        val service = Service.build(mainUrl)

        val url = when (videoType) {
            is Video.Type.Episode -> {
                val id = Base64.encode("tmdb|series|${videoType.tvShow.id}".toByteArray(), Base64.NO_WRAP).toString(Charsets.UTF_8)
                val mediaId = try {
                    service.getResponse(
                        "$mainUrl/api/v1/titles/$id?loader=titlePage",
                        referer = mainUrl
                    ).title?.id ?: id
                } catch (_: Exception) {
                    id
                }
                "$mainUrl/api/v1/titles/$mediaId/seasons/${videoType.season.number}/episodes/${videoType.number}?loader=episodePage"
            }
            is Video.Type.Movie -> {
                val id = Base64.encode("tmdb|movie|${videoType.id}".toByteArray(), Base64.NO_WRAP).toString(Charsets.UTF_8)
                "$mainUrl/api/v1/titles/$id?loader=titlePage"
            }
        }

        return try {
            val response = service.getResponse(url, referer = mainUrl)
            val videos = response.videos ?: response.title?.videos ?: response.episode?.videos ?: emptyList()
            
            videos.mapNotNull { video ->
                val src = video.src ?: ""
                val resolveUrl = video.playback_resolve_url ?: ""
                if (src.isBlank() && resolveUrl.isBlank()) return@mapNotNull null

                val finalSrc = if (resolveUrl.isNotBlank()) "$mainUrl/api/v1/$resolveUrl" else src
                
                Video.Server(
                    id = "Moflix-${video.id}",
                    name = "Moflix - ${video.name ?: "Mirror"}",
                    src = finalSrc
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun extract(link: String): Video {
        if (link.contains("/playback") || link.contains(".m3u8")) {
            val source = if (link.contains("/playback")) {
                val videoId = link.substringAfter("videos/").substringBefore("/playback")
                try {
                    Service.build(mainUrl).getPlayback(link, referer = "$mainUrl/watch/$videoId").src ?: ""
                } catch (e: Exception) {
                    ""
                }
            } else {
                link
            }

            return Video(
                source = source,
                type = MimeTypes.APPLICATION_M3U8
            )
        }

        return Extractor.extract(link)
    }



    private interface Service {

        companion object {
            private const val DEFAULT_USER_AGENT =
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:109.0) Gecko/20100101 Firefox/115.0"
            fun build(baseUrl: String): Service {
                val retrofit = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()

                return retrofit.create(Service::class.java)
            }
        }

        @GET
        suspend fun getResponse(
            @Url url: String,
            @Header("Referer") referer: String,
            @Header("User-Agent") userAgent: String = DEFAULT_USER_AGENT,
            @Header("Accept") accept: String = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            @Header("Accept-Language") acceptLanguage: String = "en-US,en;q=0.5",
            @Header("Connection") connection: String = "keep-alive"
        ): MoflixResponse

        @GET
        suspend fun getPlayback(
            @Url url: String,
            @Header("Referer") referer: String,
            @Header("User-Agent") userAgent: String = DEFAULT_USER_AGENT
        ): MoflixPlaybackResponse
    }


    data class MoflixResponse(
        val title: Title? = null,
        val episode: Episode? = null,
        val videos: List<VideoItem>? = null,
    ) {
        data class Title(
            val id: String? = null,
            val videos: List<VideoItem>? = null
        )
        data class Episode(
            val id: String? = null,
            val videos: List<VideoItem>? = null
        )
        data class VideoItem(
            val id: Int? = null,
            val name: String? = null,
            val src: String? = null,
            val type: String? = null,
            val playback_resolve_url: String? = null,
        )
    }

    data class MoflixPlaybackResponse(
        val src: String? = null,
        val type: String? = null,
        val status: String? = null,
    )
}