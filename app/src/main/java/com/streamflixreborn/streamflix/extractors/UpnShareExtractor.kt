package com.streamflixreborn.streamflix.extractors

import com.streamflixreborn.streamflix.models.Video
import com.streamflixreborn.streamflix.utils.DnsResolver
import androidx.media3.common.MimeTypes
import android.util.Base64
import android.util.Log
import com.google.gson.JsonParser
import okhttp3.Dns
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query
import retrofit2.http.Url
import java.net.URL
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class UpnShareExtractor : Extractor() {

    override val name = "UPNShare"
    override val mainUrl = "https://animeav1.uns.bio"
    override val aliasUrls = emptyList<String>()

    companion object {
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"

        // Claves definitivas extraídas del análisis correcto de v() y _()
        private val KEY = "kiemtienmua911ca".toByteArray()
        private val IV = "1234567890oiuytr".toByteArray()
    }

    private val client = OkHttpClient.Builder()
        .dns(DnsResolver.doh)
        .addInterceptor(object : Interceptor {
            override fun intercept(chain: Interceptor.Chain): Response {
                val request = chain.request().newBuilder()
                    .header("User-Agent", DEFAULT_USER_AGENT)
                    .header("Accept", "*/*")
                    .build()
                return chain.proceed(request)
            }
        })
        .build()

    private interface Service {
        @GET
        suspend fun get(
            @Url url: String,
            @Header("Referer") referer: String,
            @Query("id") id: String,
            @Query("w") w: String,
            @Query("h") h: String,
            @Query("r") r: String,
        ): String

        companion object {
            fun build(baseUrl: String, client: OkHttpClient): Service {
                val retrofit = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .addConverterFactory(ScalarsConverterFactory.create())
                    .client(client)
                    .build()
                return retrofit.create(Service::class.java)
            }
        }
    }

    override suspend fun extract(link: String): Video {
        val id = extractId(link) ?: throw Exception("Invalid link: missing id after #")
        Log.d("UPNShare", "Extracted ID: $id from $link")

        val uri = URL(link)
        val mainLink = "${uri.protocol}://${uri.host}"
        val service = Service.build(mainLink, client)
        val apiUrl = "$mainLink/api/v1/video"

        // Solicitud con parámetros exactos para asegurar la respuesta correcta
        // La respuesta es una cadena HEX directa (ej: "c931f1...")
        val hexResponse = service.get(
            url = apiUrl,
            referer = "$mainLink/",
            id = id,
            w = "1536",
            h = "1024",
            r = "animeav1.com"
        )
        Log.d("UPNShare", "API Response (first 50 chars): ${hexResponse.take(50)}...")

        // 1. Convertir HEX a Bytes reales (omitimos Base64.decode porque no es base64)
        val encryptedBytes = hexToBytes(hexResponse.trim())
        Log.d("UPNShare", "Encrypted Bytes size: ${encryptedBytes.size}")

        // 2. Descifrar bytes con AES-CBC
        val decryptedJson = decryptBytes(encryptedBytes)
        Log.d("UPNShare", "Decrypted JSON: $decryptedJson")

        val json = JsonParser.parseString(decryptedJson).asJsonObject

        val hlsPath = json.get("hls")?.asString?.takeIf { it.isNotEmpty() }
        val hlsTiktok = json.get("hlsVideoTiktok")?.asString?.takeIf { it.isNotEmpty() }
        var cfPath = json.get("cf")?.asString?.takeIf { it.isNotEmpty() }
        val cfExpire = json.get("cfExpire")?.asString?.takeIf { it.isNotEmpty() }

        val (finalUrl, headers) = when {
            !hlsPath.isNullOrEmpty() -> {
                "$mainLink$hlsPath" to mapOf("Referer" to mainLink)
            }
            !hlsTiktok.isNullOrEmpty() -> {
                var v = ""
                try {
                    val configStr = json.get("streamingConfig")?.asString
                    if (!configStr.isNullOrEmpty()) {
                        val config = JsonParser.parseString(configStr).asJsonObject
                        v = config.getAsJsonObject("adjust")
                            ?.getAsJsonObject("Tiktok")
                            ?.getAsJsonObject("params")
                            ?.get("v")?.asString ?: ""
                    }
                } catch (e: Exception) { }
                val query = if (v.isNotEmpty()) "?v=$v" else ""
                "$mainLink$hlsTiktok$query" to mapOf("Referer" to mainLink)
            }
            !cfPath.isNullOrEmpty() -> {
                var t: String? = null
                var e: String? = null
                try {
                    val configStr = json.get("streamingConfig")?.asString
                    if (configStr != null) {
                        val streamingConfig = JsonParser.parseString(configStr).asJsonObject
                        val cloudflare = streamingConfig.getAsJsonObject("adjust")?.getAsJsonObject("Cloudflare")
                        val disabled = cloudflare?.get("disabled")?.asBoolean ?: true
                        if (!disabled) {
                            val params = cloudflare.getAsJsonObject("params")
                            t = params?.get("t")?.asString
                            e = params?.get("e")?.asString
                        }
                    }
                } catch (ex: Exception) { }

                if (!e.isNullOrEmpty() && !t.isNullOrEmpty()) {
                    cfPath = "$cfPath?t=${t}&e=${e}"
                } else if (!cfExpire.isNullOrEmpty()) {
                    val parts = cfExpire.split("::")
                    if (parts.size >= 2) cfPath = "$cfPath?t=${parts[0]}&e=${parts[1]}"
                }
                cfPath!! to mapOf("Referer" to mainLink)
            }
            else -> throw Exception("No video source found in response")
        }

        val subtitles = json.getAsJsonObject("subtitle")?.entrySet()?.map { (label, file) ->
            Video.Subtitle(label = label, file = file.asString ?: "")
        } ?: emptyList()

        return Video(
            source = finalUrl,
            subtitles = subtitles,
            headers = headers,
            type = MimeTypes.APPLICATION_M3U8
        )
    }

    private fun extractId(link: String): String? {
        val idx = link.indexOf('#')
        if (idx == -1 || idx == link.lastIndex) return null
        return link.substring(idx + 1).substringBefore("&")
    }

    private fun decryptBytes(encrypted: ByteArray): String {
        Log.d("UPNShare", "Decrypting with KEY: ${String(KEY)} and IV: ${String(IV)}")
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(KEY, "AES"), IvParameterSpec(IV))
        val decrypted = cipher.doFinal(encrypted)
        return decrypted.toString(Charsets.UTF_8)
    }

    private fun hexToBytes(input: String): ByteArray {
        val cleaned = input.lowercase(Locale.US).replace(Regex("[^0-9a-f]"), "")
        val even = if (cleaned.length % 2 == 0) cleaned else "0$cleaned"
        val out = ByteArray(even.length / 2)
        for (i in 0 until even.length step 2) {
            out[i / 2] = ((even[i].digitToInt(16) shl 4) or even[i + 1].digitToInt(16)).toByte()
        }
        return out
    }
}
