package com.dskja.betterstreamflix.extractors

import com.dskja.betterstreamflix.models.Video
import androidx.media3.common.MimeTypes
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import com.google.gson.JsonParser
import com.dskja.betterstreamflix.utils.DnsResolver
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query
import retrofit2.http.Url
import java.util.Locale
import java.net.URL
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class RpmvidExtractor : Extractor() {
    override val name = "Rpmvid"
    override val mainUrl = "https://rpmvid.com"
    override val aliasUrls = listOf("https://cubeembed.rpmvid.com", "https://bummi.upns.xyz", "https://loadm.cam", "https://anibum.playerp2p.online", "https://pelisplus.upns.pro", "https://pelisplus.rpmstream.live", "https://pelisplus.strp2p.com", "https://flemmix.upns.pro", "https://moflix.rpmplay.xyz", "https://moflix.upns.xyz", "https://flix2day.xyz", "https://primevid.click",
        "https://totocoutouno.rpmlive.online", "https://dismoiceline.uns.bio", "https://doremifasol.ezplayer.me", "https://marcus.p2pstream.vip","https://animeav1.uns.bio")

    companion object {
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"
        private val KEY = "kiemtienmua911ca".toByteArray()
        private val IV = "1234567890oiuytr".toByteArray()
    }

    private data class StreamCandidate(
        val url: String,
        val headers: Map<String, String>,
        val maintainToken: Boolean = false,
    )

    private val client = OkHttpClient.Builder()
        .dns(DnsResolver.doh)
        .addInterceptor(object : Interceptor {
            override fun intercept(chain: Interceptor.Chain): Response {
                val request = chain.request().newBuilder()
                    .header("User-Agent", DEFAULT_USER_AGENT)
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
            @Query("r") r: String = "",
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
        val mainLink = URL(link).protocol + "://" + URL(link).host
        val service = Service.build(mainLink, client)
        val apiUrl = "$mainLink/api/v1/video"

        val hexResponse = service.get(
            url = apiUrl,
            referer = mainLink,
            id = id,
            w = "1920",
            h = "1080",
        )

        val decryptedJson = decryptHexPayload(hexResponse)
        val json = JsonParser.parseString(decryptedJson).asJsonObject
        val hlsPath = json.get("hls")?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotEmpty() }
        val hlsTiktok = json.get("hlsVideoTiktok")?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotEmpty() }
        val sourcePath = json.get("source")?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotEmpty() }
        val cfNativePath = json.get("cfNative")?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotEmpty() }
        var cfPath = json.get("cf")?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotEmpty() }
        val cfExpire = json.get("cfExpire")?.takeIf { !it.isJsonNull }?.asString?.takeIf { it.isNotEmpty() }

        val refererHeaders = mapOf("Referer" to mainLink)
        val candidates = mutableListOf<StreamCandidate>()

        // Prefer in-house / Cloudflare-native mirrors. TikTok CDN playlists often
        // advertise H.264 but serve placeholder PNG "segments" to app clients,
        // which then fall through to broken hosts (e.g. AV1 MP4Upload → black screen).
        if (!sourcePath.isNullOrEmpty()) {
            candidates += StreamCandidate(sourcePath, refererHeaders)
        }
        if (!cfNativePath.isNullOrEmpty()) {
            candidates += StreamCandidate(cfNativePath, refererHeaders)
        }
        if (!hlsPath.isNullOrEmpty()) {
            candidates += StreamCandidate("$mainLink$hlsPath", refererHeaders)
        }

        if (!cfPath.isNullOrEmpty() && !cfPath.contains("skyforgeconcepts.shop")) {
            val pk = json.getAsJsonObject("pk")
            val k = pk?.get("k")?.takeIf { !it.isJsonNull }?.asString
            val kx = pk?.get("kx")?.takeIf { !it.isJsonNull }?.asString

            if (!k.isNullOrEmpty() && !kx.isNullOrEmpty()) {
                cfPath = "$cfPath?k=$k&kx=$kx"
            } else if (!cfExpire.isNullOrEmpty()) {
                val parts = cfExpire.split("::")
                if (parts.size >= 2) {
                    cfPath = "$cfPath?t=${parts[0]}&e=${parts[1]}"
                }
            }

            var maintainToken = false
            if (cfPath.contains("?")) {
                val uri = android.net.Uri.parse(cfPath)
                TokenManager.latestQuery = uri.encodedQuery
                maintainToken = true
            }

            candidates += StreamCandidate(
                url = cfPath,
                headers = mapOf("Referer" to mainLink, "Origin" to mainLink),
                maintainToken = maintainToken,
            )
        }

        if (!hlsTiktok.isNullOrEmpty()) {
            var v = ""
            var domain = ""
            try {
                val configStr = json.get("streamingConfig")?.asString
                if (!configStr.isNullOrEmpty()) {
                    val config = JsonParser.parseString(configStr).asJsonObject
                    val tiktok = config.getAsJsonObject("adjust")?.getAsJsonObject("Tiktok")
                    v = tiktok?.getAsJsonObject("params")?.get("v")?.asString ?: ""
                    domain = tiktok?.get("domain")?.asString ?: ""
                }
            } catch (_: Exception) {
            }
            val tiktokPath = if (domain.isNotEmpty() && hlsTiktok.startsWith("/hls/")) {
                hlsTiktok.replaceFirst("/hls/", "/hlsmod/$domain/")
            } else {
                hlsTiktok
            }
            val query = if (v.isNotEmpty()) "?v=$v" else ""
            candidates += StreamCandidate("$mainLink$tiktokPath$query", refererHeaders)
        }

        val selected = candidates.firstOrNull()
            ?: throw Exception("Missing source, cfNative, hls, cf or hlsVideoTiktok in response")

        val defaultSub = json.getAsJsonObject("defaultSubtitle")
                                ?.get("defaultSubtitle")?.asString?:""
        var alreadySelect = false
        val subtitles = json.getAsJsonObject("subtitle")
            ?.entrySet()
            ?.map { (label, file ) ->
                Video.Subtitle(
                    label = label,
                    file = file.asString?:"",
                    default = if (alreadySelect == false && defaultSub.isNotEmpty() && label.contains(
                            defaultSub
                        )
                    ) {
                        alreadySelect = true
                        true
                    } else {
                        false
                    }
                )
            } ?: emptyList()

        val mimeType = when {
            selected.url.contains(".m3u8", ignoreCase = true) -> MimeTypes.APPLICATION_M3U8
            selected.url.contains(".mp4", ignoreCase = true) -> MimeTypes.VIDEO_MP4
            else -> MimeTypes.APPLICATION_M3U8
        }

        return Video(
            source = selected.url,
            subtitles,
            headers = selected.headers,
            type = mimeType,
            maintainToken = selected.maintainToken
        )
    }

    private fun extractId(link: String): String? {
        val idx = link.indexOf('#')
        if (idx == -1 || idx == link.lastIndex) return null
        return link.substring(idx + 1).substringBefore("&")
    }

    private fun decryptHexPayload(hex: String): String {
        val bytes = hexToBytes(hex)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(KEY, "AES"), IvParameterSpec(IV))
        val decrypted = cipher.doFinal(bytes)
        return decrypted.toString(Charsets.UTF_8)
    }

    private fun hexToBytes(input: String): ByteArray {
        val cleaned = input.lowercase(Locale.US).replace(Regex("[^0-9a-f]"), "")
        val even = if (cleaned.length % 2 == 0) cleaned else "0$cleaned"
        val out = ByteArray(even.length / 2)
        var i = 0
        var j = 0
        while (i < even.length) {
            out[j++] = ((even[i].digitToInt(16) shl 4) or even[i + 1].digitToInt(16)).toByte()
            i += 2
        }
        return out
    }
}
