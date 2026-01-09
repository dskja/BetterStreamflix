package com.streamflixreborn.streamflix.extractors

import com.streamflixreborn.streamflix.models.Video
import androidx.media3.common.MimeTypes
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class VideasyExtractor : Extractor() {
    override val name = "Videasy"
    override val mainUrl = "https://api.videasy.net"

    data class ServerConfig(
        val name: String,
        val endpoint: String,
        val movieOnly: Boolean = false
    )

    private val englishServers = listOf(
        ServerConfig("Neon", "myflixerzupcloud"),
        ServerConfig("Yoru", "cdn", movieOnly = true),
        ServerConfig("Cypher", "moviebox"),
        ServerConfig("Sage", "1movies"),
        ServerConfig("Jett", "primesrcme"),
        ServerConfig("Reyna", "primewire"),
        ServerConfig("Breach", "m4uhd"),
        ServerConfig("Vyse", "hdmovie")
    )

    fun servers(videoType: Video.Type, language: String): List<Video.Server> {
        return when (language) {
            "en" -> {
                englishServers.mapNotNull { config ->
                    if (config.movieOnly && videoType !is Video.Type.Movie) return@mapNotNull null
                    
                    val url = when (videoType) {
                        is Video.Type.Movie -> {
                            val year = videoType.releaseDate.split("-").firstOrNull() ?: ""
                            "$mainUrl/${config.endpoint}/sources-with-title?title=${videoType.title}&mediaType=movie&year=$year&tmdbId=${videoType.id}&imdbId=${videoType.imdbId ?: ""}"
                        }
                        is Video.Type.Episode -> {
                            val year = videoType.tvShow.releaseDate?.split("-")?.firstOrNull() ?: ""
                            "$mainUrl/${config.endpoint}/sources-with-title?title=${videoType.tvShow.title}&mediaType=tv&year=$year&tmdbId=${videoType.tvShow.id}&imdbId=${videoType.tvShow.imdbId ?: ""}&episodeId=${videoType.number}&seasonId=${videoType.season.number}"
                        }
                    }
                    
                    Video.Server(
                        id = "${config.name} (Videasy)",
                        name = "${config.name} (Videasy)",
                        src = url
                    )
                }
            }
            else -> {
                val serverName = when (language) {
                    "de" -> "Killjoy (Videasy)"
                    "it" -> "Harbor (Videasy)"
                    "fr" -> if (videoType is Video.Type.Movie) "Chamber (Videasy)" else return emptyList()
                    "es" -> "Kayo (Videasy)"
                    else -> return emptyList()
                }
                
                val videasyLang = when (language) {
                    "de" -> "german"
                    "it" -> "italian"
                    "fr" -> "french"
                    "es" -> "spanish"
                    else -> return emptyList()
                }

                val endpoint = when (language) {
                    "es" -> "cuevana-spanish"
                    else -> "meine"
                }

                val url = when (videoType) {
                    is Video.Type.Movie -> {
                        val year = videoType.releaseDate.split("-").firstOrNull() ?: ""
                        "$mainUrl/$endpoint/sources-with-title?title=${videoType.title}&mediaType=movie&year=$year&tmdbId=${videoType.id}&imdbId=${videoType.imdbId ?: ""}&language=$videasyLang"
                    }
                    is Video.Type.Episode -> {
                        val year = videoType.tvShow.releaseDate?.split("-")?.firstOrNull() ?: ""
                        "$mainUrl/$endpoint/sources-with-title?title=${videoType.tvShow.title}&mediaType=tv&year=$year&tmdbId=${videoType.tvShow.id}&imdbId=${videoType.tvShow.imdbId ?: ""}&episodeId=${videoType.number}&seasonId=${videoType.season.number}&language=$videasyLang"
                    }
                }

                listOf(Video.Server(
                    id = serverName,
                    name = serverName,
                    src = url
                ))
            }
        }
    }

    fun server(videoType: Video.Type, language: String): Video.Server? {
        return servers(videoType, language).firstOrNull()
    }

    override suspend fun extract(link: String): Video {
        val client = OkHttpClient()

        // 1. Get encrypted data from api.videasy.net
        val request = Request.Builder()
            .url(link)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/137.0.0.0 Safari/537.36")
            .build()
        
        val response = client.newCall(request).execute()
        val encData = response.body?.string() ?: throw Exception("Failed to get encrypted data")

        // 2. Extract tmdbId from link to use it for decryption
        val tmdbId = link.split("tmdbId=").getOrNull(1)?.split("&")?.getOrNull(0) ?: ""

        // 3. Post to decryption API
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

        // 4. Parse result (JSON string containing sources)
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
                    subtitles.add(Video.Subtitle(
                        label = label,
                        file = url
                    ))
                }
            }
        }

        if (sources != null && sources.length() > 0) {
            val source = sources.getJSONObject(0)
            return Video(
                source = source.optString("url"),
                type = MimeTypes.APPLICATION_M3U8,
                subtitles = subtitles,
                headers = mapOf("Referer" to "https://videasy.net")
            )
        }

        throw Exception("No video source found")
    }
}
