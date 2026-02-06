package com.streamflixreborn.streamflix.extractors

import android.util.Base64
import com.streamflixreborn.streamflix.models.Video
import com.streamflixreborn.streamflix.utils.DnsResolver
import okhttp3.OkHttpClient
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.HeaderMap
import retrofit2.http.Url
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

open class FilemoonExtractor : Extractor() {

    override val name = "Filemoon"
    override val mainUrl = "https://filemoon.site"
    override val aliasUrls = listOf("https://bf0skv.org","https://bysejikuar.com","https://moflix-stream.link","https://bysezoxexe.com")

    override suspend fun extract(link: String): Video {
        val service = Service.build(mainUrl)
        // Regex to match /e/ or /d/ and ID
        val matcher = Regex("""/(e|d)/([a-zA-Z0-9]+)""").find(link) 
            ?: throw Exception("Could not extract video ID or type")
        
        val linkType = matcher.groupValues[1]
        val videoId = matcher.groupValues[2]
        
        val currentDomain = Regex("""(https?://[^/]+)""").find(link)?.groupValues?.get(1)
            ?: throw Exception("Could not extract Base URL")

        val detailsUrl = "$currentDomain/api/videos/$videoId/embed/details"
        val details = service.getDetails(detailsUrl)
        val embedFrameUrl = details.embed_frame_url 
            ?: throw Exception("embed_frame_url not found")

        var playbackDomain = ""
        val headers = mutableMapOf<String, String>()
        headers["User-Agent"] = Service.DEFAULT_USER_AGENT
        headers["Accept"] = "application/json"

        if (linkType == "d") {
            playbackDomain = currentDomain
            headers["Referer"] = link
        } else {
            playbackDomain = Regex("""(https?://[^/]+)""").find(embedFrameUrl)?.groupValues?.get(1)
                ?: throw Exception("Could not extract domain from embed_frame_url")
            headers["Referer"] = embedFrameUrl
            headers["X-Embed-Parent"] = link
        }

        val playbackUrl = "$playbackDomain/api/videos/$videoId/embed/playback"
        val playbackResponse = service.getPlayback(playbackUrl, headers)
        val playbackData = playbackResponse.playback
            ?: throw Exception("No playback data")


        val decryptedJson = decryptPlayback(playbackData)
        
        val jsonObject = JSONObject(decryptedJson)
        val sources = jsonObject.optJSONArray("sources")
            ?: throw Exception("No sources found in decrypted data")
            
        if (sources.length() == 0) throw Exception("Empty sources list")
        
        val sourceUrl = sources.getJSONObject(0).getString("url")

        return Video(
            source = sourceUrl,
            headers = mapOf(
                "Referer" to "$playbackDomain/",
                "User-Agent" to Service.DEFAULT_USER_AGENT,
                "Origin" to playbackDomain
            )
        )
    }

    private fun decryptPlayback(data: PlaybackData): String {
        val iv = Base64.decode(data.iv, Base64.URL_SAFE)
        val payload = Base64.decode(data.payload, Base64.URL_SAFE)
        val p1 = Base64.decode(data.key_parts[0], Base64.URL_SAFE)
        val p2 = Base64.decode(data.key_parts[1], Base64.URL_SAFE)
        
        val key = ByteArray(p1.size + p2.size)
        System.arraycopy(p1, 0, key, 0, p1.size)
        System.arraycopy(p2, 0, key, p1.size, p2.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        val secretKey = SecretKeySpec(key, "AES")
        
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        
        val decryptedBytes = cipher.doFinal(payload)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    class Any(hostUrl: String) : FilemoonExtractor() {
        override val mainUrl = hostUrl
    }

    private interface Service {
        @GET
        suspend fun getDetails(@Url url: String): DetailsResponse

        @GET
        suspend fun getPlayback(@Url url: String, @HeaderMap headers: Map<String, String>): PlaybackResponse

        companion object {
            const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36"

            fun build(baseUrl: String): Service {
                val client = OkHttpClient.Builder()
                    .dns(DnsResolver.doh).build()
                return Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()
                    .create(Service::class.java)
            }
        }
    }

    data class DetailsResponse(val embed_frame_url: String?)
    data class PlaybackResponse(val playback: PlaybackData?)
    data class PlaybackData(
        val iv: String,
        val payload: String,
        val key_parts: List<String>
    )
}
