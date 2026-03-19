package com.streamflixreborn.streamflix.extractors

import android.net.Uri
import androidx.media3.common.MimeTypes
import com.streamflixreborn.streamflix.models.Video
import com.streamflixreborn.streamflix.utils.DnsResolver
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class AfterDarkExtractor( var newUrl: String = "" ) : Extractor() {
    val defaultUrl = "https://afterdark.best"
    override var mainUrl = newUrl.ifBlank { defaultUrl }
    override val name = "AfterDark"

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
        
        private val PROVIDER_HASHES = listOf(
            Pair("Premium", "aa86800c3ec95e610210f8378c316734ee92a09ee00f8c708c1a06c616651e8f"),
            Pair("Raven (fdla)", "63e997074c73a7b57239e53ac7618f3e1ef81bda3f0ab47ee0ecc82bf0493904"),
            Pair("Willow (zekd)", "ffe22be1dcd9d941bd4d09121338c70500fc067dcd94b1168079ba789e7c46c4"),
            Pair("Alpha (lkua)", "d7ae23a39378ba1864d998d52c010e969f8344ebaebf97436d9c7bf3b592667d"),
            Pair("Yuna (msfu)", "24758778992d2473ae2618adf856f8902a675718eef18169c854d07d1fcad298"),
            Pair("Ive (iodv)", "70b726570a3111d2c6d51ae57139e4af4b69392ebbf32293c5d7f7ec53922cd5"),
            Pair("Lumi (redu)", "e818c6028fbd6b8c58ce3cdb1d8be2972ffa0a486361fc07b7e9d2bd0c2d95f2"),
            Pair("Beta (zele)", "e89c6cdf5d5296dd5f0e864a030efdfcaa1896773fb9f0d7e6926acaed7f4a86"),
            Pair("Bunny (ofsa)", "dc4cc6245be6fec3d7ea391bfac09cb5d4090e5135629b0e6e81bacd3d10e8dc"),
            Pair("Gamma (offi)", "c3ce337885c3aae80534c9fa298aae6a4b37fa0188c09238610beeacd553caf1")
        )

        private val client = OkHttpClient.Builder()
            .readTimeout(15, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .dns(DnsResolver.doh)
            .build()
    }

    private fun buildPayload(title: String, type: String, tmdbId: String, imdbId: String, year: String, season: Int = 1, episode: Int = 1): String {
        return """{"t":{"t":10,"i":0,"p":{"k":["data"],"v":[{"t":10,"i":1,"p":{"k":["title","type","tmdbId","imdbId","releaseYear","season","episode"],"v":[{"t":1,"s":"$title"},{"t":1,"s":"$type"},{"t":1,"s":"$tmdbId"},{"t":1,"s":"$imdbId"},{"t":1,"s":"$year"},{"t":2,"s":$season},{"t":2,"s":$episode}]},"o":0}]},"o":0},"f":63,"m":[]}"""
    }

    override suspend fun extract(link: String): Video {
        throw Exception("Direct extraction not supported. Use servers(videoType).")
    }

    suspend fun servers(videoType: Video.Type): List<Video.Server> {
        val allServers = mutableListOf<Video.Server>()

        val encodedPayload = Uri.encode(when (videoType) {
            is Video.Type.Movie -> buildPayload(videoType.title, "movie", videoType.id, videoType.imdbId ?: "0", videoType.releaseDate.split("-").firstOrNull() ?: "")
            is Video.Type.Episode -> buildPayload(videoType.tvShow.title, "tv", videoType.tvShow.id, videoType.tvShow.imdbId ?: "0", videoType.tvShow.releaseDate?.split("-")?.firstOrNull() ?: "", videoType.season.number, videoType.number)
        })

        for ((pName, pHash) in PROVIDER_HASHES) {
            val apiUrl = "$mainUrl/_serverFn/$pHash?payload=$encodedPayload"
            
            try {
                val request = Request.Builder()
                    .url(apiUrl)
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", "$mainUrl/")
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("x-tsr-serverfn", "true")
                    .build()

                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: continue
                
                if (body.contains("\"error\":") && body.contains("\"details\":")) continue

                val blockPattern = Pattern.compile("\"k\":\\[([^\\]]+)\\],\"v\":\\[([^\\]]+)\\]")
                val matcher = blockPattern.matcher(body)
                
                while (matcher.find()) {
                    val keysStr = matcher.group(1) ?: ""
                    val valsStr = matcher.group(2) ?: ""
                    
                    val keys = keysStr.split(",").map { it.trim().trim('\"') }
                    val vals = mutableListOf<String>()
                    val valMatcher = Pattern.compile("\"s\":\"([^\"]*)\"").matcher(valsStr)
                    while (valMatcher.find()) {
                        vals.add(valMatcher.group(1) ?: "")
                    }
                    
                    if (keys.size != vals.size) continue
                    
                    val data = keys.zip(vals).toMap()
                    val service = data["service"]?.lowercase() ?: ""
                    
                    // UI Sources Logic:
                    // 1. 'Sources' = Premium provider OR proxied entries OR 'unknown' services.
                    val prefixes = mapOf(
                        "voe" to "https://proxy.afterdark.baby/boom-clap?url=",
                        "vidmoly" to "https://proxy.afterdark.baby/elizabeth-taylor?url=",
                        "uqload" to "https://proxy.afterdark.baby/alejandro?url=",
                        "vidzy" to "https://proxy.afterdark.baby/rolly?url="
                    )
                    
                    val rawUrl = data["url"] ?: data["embedUrl"] ?: continue
                    var url = rawUrl
                    val proxyPrefix = prefixes[service]
                    
                    val isSource = when {
                        pName == "Premium" -> {
                            url = rawUrl
                            true
                        }
                        proxyPrefix != null -> {
                            // Official UI only shows the PROXIED version if a proxy exists.
                            url = "${proxyPrefix}${Uri.encode(rawUrl)}"
                            true
                        }
                        else -> {
                            // Official UI only shows the NON-PROXIED version if the service is 'unknown'.
                            service == "unknown" || service.isBlank()
                        }
                    }

                    if (!isSource) continue

                    val resolvedProv = data["provider"] ?: pName.split(" ")[0]
                    val quality = data["quality"] ?: "hd"
                    val language = data["language"] ?: "vf"

                    if (url.startsWith("http")) {
                        val serverName = "$resolvedProv • $quality • $language"
                        
                        // Avoid duplicates if both url and embedUrl were parsed as separate blocks
                        if (allServers.none { it.video?.source == url }) {
                            allServers.add(
                                Video.Server(
                                    id = "afd_${allServers.size}",
                                    name = serverName,
                                ).apply {
                                    video = Video(
                                        source = url,
                                        type = if (url.contains(".m3u8")) MimeTypes.APPLICATION_M3U8 else MimeTypes.APPLICATION_MP4,
                                        headers = mapOf(
                                            "Referer" to "$mainUrl/",
                                            "User-Agent" to USER_AGENT
                                        )
                                    )
                                }
                            )
                        }
                    }
                }
            } catch (e: Exception) { }
        }
        
        return allServers
    }
}