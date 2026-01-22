package com.streamflixreborn.streamflix.extractors

import android.util.Base64
import androidx.media3.common.MimeTypes
import com.streamflixreborn.streamflix.models.Video
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Url
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

// https://github.com/yogesh-hacker/MediaVanced/blob/main/sites/vidrock.py
class VidrockExtractor : Extractor() {

    override val name = "Vidrock"
    override val mainUrl = "https://vidrock.net"

    private val passphrase = "x7k9mPqT2rWvY8zA5bC3nF6hJ2lK4mN9"

    suspend fun servers(videoType: Video.Type): List<Video.Server> {
        val encoded = when (videoType) {
            is Video.Type.Movie -> encryptAndEncode(videoType.id)
            is Video.Type.Episode -> encryptAndEncode("${videoType.tvShow.id}_${videoType.season.number}_${videoType.number}")
        }

        val apiUrl = when (videoType) {
            is Video.Type.Episode -> "$mainUrl/api/tv/$encoded"
            is Video.Type.Movie -> "$mainUrl/api/movie/$encoded"
        }

        return try {
            val service = Service.build(mainUrl)
            val response = service.getStreams(apiUrl)

            response.mapNotNull { (serverName, data) ->
                val videoUrl = data["url"] ?: return@mapNotNull null
                if (videoUrl.isEmpty()) return@mapNotNull null

                Video.Server(
                    id = "$serverName-$videoUrl (Vidrock)",
                    name = "$serverName (Vidrock)",
                    src = "$apiUrl#$serverName"
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun server(videoType: Video.Type): Video.Server? {
        return servers(videoType).firstOrNull()
    }

    override suspend fun extract(link: String): Video {
        val serverName = link.substringAfter("#", "").takeIf { it != link }
        val apiLink = link.substringBefore("#")

        val service = Service.build(mainUrl)
        val response = service.getStreams(apiLink)

        val serverEntry = if (!serverName.isNullOrEmpty()) {
            response.entries.find { it.key.equals(serverName, ignoreCase = true) }
        } else {
            response.entries.find { it.value["url"]?.isNotEmpty() == true }
        } ?: error("No video sources found")

        val actualServerName = serverEntry.key
        var videoUrl = serverEntry.value["url"]!!
        var type = MimeTypes.APPLICATION_M3U8

        if (actualServerName.equals("Atlas", ignoreCase = true)) {
            val qualities = try {
                service.getAtlasQualities(videoUrl)
            } catch (e: Exception) {
                emptyList()
            }
            val highest = qualities.maxByOrNull { it.resolution }
            if (highest != null) {
                videoUrl = highest.url
                type = MimeTypes.VIDEO_MP4
            }
        }

        return Video(
            source = videoUrl,
            headers = mapOf(
                "Referer" to "$mainUrl/",
                "Origin" to mainUrl
            ),
            type = type
        )
    }

    private fun encryptAndEncode(data: String): String {
        val key = passphrase.toByteArray(Charsets.UTF_8)
        val iv = key.copyOfRange(0, 16)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val secretKey = SecretKeySpec(key, "AES")
        val ivSpec = IvParameterSpec(iv)

        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)
        val encrypted = cipher.doFinal(data.toByteArray(Charsets.UTF_8))

        return Base64.encodeToString(encrypted, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private interface Service {
        companion object {
            fun build(baseUrl: String): Service {
                val client = OkHttpClient.Builder().build()
                val retrofit = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(client)
                    .build()
                return retrofit.create(Service::class.java)
            }
        }

        @GET
        suspend fun getStreams(@Url url: String): Map<String, Map<String, String>>

        @GET
        suspend fun getAtlasQualities(@Url url: String): List<AtlasQuality>
    }

    data class AtlasQuality(
        val resolution: Int,
        val url: String
    )
}
