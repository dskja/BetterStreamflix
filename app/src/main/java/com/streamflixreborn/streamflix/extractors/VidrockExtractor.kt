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

    fun server(videoType: Video.Type): Video.Server {
        val encoded = when (videoType) {
            is Video.Type.Movie -> encryptAndEncode(videoType.id)
            is Video.Type.Episode -> encryptAndEncode("${videoType.tvShow.id}_${videoType.season.number}_${videoType.number}")
        }

        return Video.Server(
            id = name,
            name = name,
            src = when (videoType) {
                is Video.Type.Episode -> "$mainUrl/api/tv/$encoded"
                is Video.Type.Movie -> "$mainUrl/api/movie/$encoded"
            }
        )
    }

    override suspend fun extract(link: String): Video {
        val service = Service.build(mainUrl)

        val response = service.getStreams(link)

        val sources = response.values.mapNotNull { it["url"] }.filter { it.isNotEmpty() }
        val videoUrl = sources.randomOrNull() ?: error("No video sources found")

        return Video(
            source = videoUrl,
            headers = mapOf(
                "Referer" to mainUrl
            ),
            type = MimeTypes.APPLICATION_M3U8
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
    }
}
