package com.streamflixreborn.streamflix.extractors

import android.util.Base64
import androidx.media3.common.MimeTypes
import com.streamflixreborn.streamflix.models.Video
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class VidzeeExtractor : Extractor() {
    override val name = "Vidzee"
    override val mainUrl = "https://player.vidzee.wtf"
    private val coreApi = "https://core.vidzee.wtf"
    private val staticPass = "b3f2a9d4c6e1f8a7b"

    private val client = OkHttpClient.Builder().build()

    data class ServerConfig(
        val name: String,
        val index: Int
    )

    private val servers = listOf(
        ServerConfig("Nflix", 0),
        ServerConfig("Glory", 1),
        ServerConfig("Nazy", 2),
        ServerConfig("Atlas", 3),
        ServerConfig("Drag", 4),
        ServerConfig("Achilles", 5),
        ServerConfig("Viet", 6),
        ServerConfig("Hindi", 7),
        ServerConfig("Bengali", 8),
        ServerConfig("Tamil", 9),
        ServerConfig("Tamil", 10),
        ServerConfig("Telugu", 11),
        ServerConfig("Malayalam", 12)
    )

    fun servers(videoType: Video.Type): List<Video.Server> {
        val baseUrl = when (videoType) {
            is Video.Type.Movie -> "$mainUrl/api/server?id=${videoType.id}"
            is Video.Type.Episode -> "$mainUrl/api/server?id=${videoType.tvShow.id}&ss=${videoType.season.number}&ep=${videoType.number}"
        }

        return servers.map { config ->
            Video.Server(
                id = "${config.name} (Vidzee)",
                name = "${config.name} (Vidzee)",
                src = "$baseUrl&sr=${config.index}"
            )
        }
    }

    fun server(videoType: Video.Type): Video.Server {
        return servers(videoType).first()
    }

    override suspend fun extract(link: String): Video = coroutineScope {
        val masterKey = getMasterKey() ?: throw Exception("Failed to get Vidzee master key")
        
        try {
            val request = Request.Builder()
                .url(link)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36")
                .header("Origin", mainUrl)
                .header("Referer", "$mainUrl/")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) throw Exception("Network error")

            val body = response.body?.string() ?: throw Exception("Empty body")
            val json = JSONObject(body)
            val urlArray = json.optJSONArray("url") ?: throw Exception("No URLs found")
            if (urlArray.length() == 0) throw Exception("Empty URL array")

            val content = urlArray.getJSONObject(0)
            val encryptedLink = content.optString("link")
            if (encryptedLink.isEmpty()) throw Exception("Empty encrypted link")

            val decryptedUrl = decryptLink(encryptedLink, masterKey)
                ?: throw Exception("Failed to decrypt link")

            val tracks = json.optJSONArray("tracks")
            val subtitles = mutableListOf<Video.Subtitle>()
            if (tracks != null) {
                for (i in 0 until tracks.length()) {
                    val track = tracks.getJSONObject(i)
                    val subUrl = track.optString("url")
                    if (subUrl.isNotEmpty()) {
                        subtitles.add(Video.Subtitle(
                            label = track.optString("lang", "Unknown"),
                            file = subUrl
                        ))
                    }
                }
            }

            return@coroutineScope Video(
                source = decryptedUrl,
                subtitles = subtitles,
                headers = mapOf(
                    "Referer" to mainUrl,
                    "Origin" to mainUrl,
                    "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36"
                ),
                type = MimeTypes.APPLICATION_M3U8
            )
        } catch (e: Exception) {
            throw Exception("Failed to extract video: ${e.message}")
        }
    }

    private fun getMasterKey(): String? {
        return try {
            val request = Request.Builder()
                .url("$coreApi/api-key")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
                .header("Origin", mainUrl)
                .header("Referer", "$mainUrl/")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return null

            val b64Data = response.body?.string() ?: return null
            val data = Base64.decode(b64Data, Base64.DEFAULT)

            val iv = data.sliceArray(0 until 12)
            val tag = data.sliceArray(12 until 28)
            val ciphertext = data.sliceArray(28 until data.size)

            val key = MessageDigest.getInstance("SHA-256")
                .digest(staticPass.toByteArray(Charsets.UTF_8))

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val spec = GCMParameterSpec(128, iv)
            val secretKey = SecretKeySpec(key, "AES")
            
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            
            // In Java/Android, the tag is expected to be at the end of the ciphertext
            val combined = ciphertext + tag
            val decrypted = cipher.doFinal(combined)
            
            String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    private fun decryptLink(encLink: String, masterKey: String): String? {
        return try {
            val decodedRaw = String(Base64.decode(encLink, Base64.DEFAULT), Charsets.UTF_8)
            val parts = decodedRaw.split(":")
            val iv = Base64.decode(parts[0], Base64.DEFAULT)
            val ciphertext = Base64.decode(parts[1], Base64.DEFAULT)

            val keyBytes = masterKey.toByteArray(Charsets.UTF_8)
            val paddedKey = ByteArray(32) { i -> if (i < keyBytes.size) keyBytes[i] else 0 }

            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            val secretKey = SecretKeySpec(paddedKey, "AES")
            val ivSpec = IvParameterSpec(iv)

            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
            val decrypted = cipher.doFinal(ciphertext)

            String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }
}
