package com.streamflixreborn.streamflix.extractors

import com.streamflixreborn.streamflix.models.Video
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Url

class PrimeSrcExtractor : Extractor() {

    override val name = "PrimeSrc"
    override val mainUrl = "https://primesrc.me"

    suspend fun servers(videoType: Video.Type): List<Video.Server> {
        val apiUrl = when (videoType) {
            is Video.Type.Episode -> "$mainUrl/api/v1/s?tmdb=${videoType.tvShow.id}&season=${videoType.season.number}&episode=${videoType.number}&type=tv"
            is Video.Type.Movie -> "$mainUrl/api/v1/s?tmdb=${videoType.id}&type=movie"
        }

        return try {
            val service = Service.build(mainUrl)
            val serversResponse = service.getServers(apiUrl)

            // Track server name counts to number duplicates
            val nameCount = mutableMapOf<String, Int>()

            serversResponse.servers.map { server ->
                val count = nameCount.getOrDefault(server.name, 0) + 1
                nameCount[server.name] = count

                val suffix = if (count > 1) " $count" else ""
                val displayName = "${server.name}$suffix (PrimeSrc)"

                Video.Server(
                    id = "${server.name}-${server.key} (PrimeSrc)",
                    name = displayName,
                    src = "$mainUrl/api/v1/l?key=${server.key}"
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun server(videoType: Video.Type): Video.Server {
        return servers(videoType).first()
    }

    override suspend fun extract(link: String): Video {
        val service = Service.build(mainUrl)

        val videoLink = service.getLink(link).link

        return Extractor.extract(videoLink)
    }

    private interface Service {
        companion object {
            fun build(baseUrl: String): Service {
                val client = OkHttpClient.Builder().build()
                val retrofit = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                return retrofit.create(Service::class.java)
            }
        }

        @GET
        suspend fun getServers(@Url url: String): ServersResponse

        @GET
        suspend fun getLink(@Url url: String): LinkResponse
    }

    data class ServersResponse(
        val servers: List<Server>
    )

    data class Server(
        val name: String,
        val key: String
    )

    data class LinkResponse(
        val link: String
    )
}
