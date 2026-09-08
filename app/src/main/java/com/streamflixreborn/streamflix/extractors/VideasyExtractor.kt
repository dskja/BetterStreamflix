package com.streamflixreborn.streamflix.extractors

import androidx.media3.common.MimeTypes
import com.streamflixreborn.streamflix.models.Video
import com.streamflixreborn.streamflix.utils.DnsResolver
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class VideasyExtractor : Extractor() {
    override val name = "Videasy"
    override val mainUrl = "https://api.speedracelight.com"

    data class ServerConfig(
        val name: String,
        val endpoint: String,
        val movieOnly: Boolean = false
    )

    private val englishServers = listOf(
        ServerConfig("Yoru", "cdn"),
        ServerConfig("Cypher", "downloader2"),
        ServerConfig("Breach", "m4uhd"),
        ServerConfig("Neon", "vsrc"),
        ServerConfig("Vyse", "hdmovie")
    )

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(DnsResolver.doh)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    fun servers(videoType: Video.Type, language: String): List<Video.Server> {
        return when (language) {
            "en" -> {
                englishServers.mapNotNull { config ->
                    if (config.movieOnly && videoType !is Video.Type.Movie) return@mapNotNull null

                    Video.Server(
                        id = "${config.name} (Videasy)",
                        name = "${config.name} (Videasy)",
                        src = buildSourcesUrl(config.endpoint, videoType)
                    )
                }
            }
            else -> {
                val serverName = when (language) {
                    "de" -> "Killjoy (Videasy)"
                    else -> return emptyList()
                }

                val videasyLang = when (language) {
                    "de" -> "german"
                    else -> return emptyList()
                }

                listOf(
                    Video.Server(
                        id = serverName,
                        name = serverName,
                        src = buildSourcesUrl(
                            endpoint = "meine",
                            videoType = videoType,
                            language = videasyLang,
                        )
                    )
                )
            }
        }
    }

    fun server(videoType: Video.Type, language: String): Video.Server? {
        return servers(videoType, language).firstOrNull()
    }

    private fun buildSourcesUrl(
        endpoint: String,
        videoType: Video.Type,
        language: String? = null,
    ): String {
        val builder = mainUrl.toHttpUrl().newBuilder()
            .addPathSegment(endpoint)
            .addPathSegment("sources-with-title")

        when (videoType) {
            is Video.Type.Movie -> {
                val year = videoType.releaseDate.split("-").firstOrNull().orEmpty()
                builder
                    .addQueryParameter("title", videoType.title)
                    .addQueryParameter("mediaType", "movie")
                    .addQueryParameter("year", year)
                    .addQueryParameter("tmdbId", videoType.id)
                    .addQueryParameter("imdbId", videoType.imdbId.orEmpty())
            }
            is Video.Type.Episode -> {
                val year = videoType.tvShow.releaseDate?.split("-")?.firstOrNull().orEmpty()
                builder
                    .addQueryParameter("title", videoType.tvShow.title)
                    .addQueryParameter("mediaType", "tv")
                    .addQueryParameter("year", year)
                    .addQueryParameter("tmdbId", videoType.tvShow.id)
                    .addQueryParameter("imdbId", videoType.tvShow.imdbId.orEmpty())
                    .addQueryParameter("episodeId", videoType.number.toString())
                    .addQueryParameter("seasonId", videoType.season.number.toString())
            }
        }

        if (!language.isNullOrBlank()) {
            builder.addQueryParameter("language", language)
        }

        return builder.build().toString()
    }

    override suspend fun extract(link: String): Video {
        val request = Request.Builder()
            .url(link)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36"
            )
            .build()

        val response = client.newCall(request).execute()
        val encData = response.body?.string() ?: throw Exception("Failed to get encrypted data")

        val tmdbId = link.split("tmdbId=").getOrNull(1)?.split("&")?.getOrNull(0).orEmpty()

        val json = JSONObject()
        json.put("text", encData)
        json.put("id", tmdbId)

        val body = json.toString().toRequestBody("application/json".toMediaType())
        val decRequest = Request.Builder()
            .url("https://enc-dec.app/api/dec-videasy")
            .post(body)
            .build()

        val decResponse = client.newCall(decRequest).execute()
        val decBody = decResponse.body?.string() ?: "{}"
        val decJson = JSONObject(decBody)
        val result = decJson.optString("result")

        val resultJson = JSONObject(result)
        val sources = resultJson.optJSONArray("sources")
        val subtitles = mutableListOf<Video.Subtitle>()

        val tracks = resultJson.optJSONArray("subtitles")
        if (tracks != null) {
            for (i in 0 until tracks.length()) {
                val track = tracks.getJSONObject(i)
                val label = track.optString("lang", "Unknown")
                val url = track.optString("url")
                if (url.isNotEmpty()) {
                    subtitles.add(
                        Video.Subtitle(
                            label = label,
                            file = url
                        )
                    )
                }
            }
        }

        if (sources != null && sources.length() > 0) {
            val source = sources.getJSONObject(0)

            val config = englishServers.find { link.contains("/${it.endpoint}/") }
            // Cypher returns MP4; other Videasy endpoints are HLS.
            val isMp4Server = config?.name == "Cypher"
            val mimeType = if (isMp4Server) MimeTypes.VIDEO_MP4 else MimeTypes.APPLICATION_M3U8

            return Video(
                source = source.optString("url"),
                type = mimeType,
                subtitles = subtitles,
                headers = mapOf("Referer" to "https://player.videasy.net/")
            )
        }

        throw Exception("No video source found")
    }
}
