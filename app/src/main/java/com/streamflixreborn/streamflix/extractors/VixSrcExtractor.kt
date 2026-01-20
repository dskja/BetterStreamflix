package com.streamflixreborn.streamflix.extractors

import android.util.Base64
import android.util.Log
import androidx.media3.common.MimeTypes
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import com.streamflixreborn.streamflix.models.Video
import com.streamflixreborn.streamflix.utils.DnsResolver
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Url
import okhttp3.Request
import com.streamflixreborn.streamflix.utils.UserPreferences
import java.util.concurrent.TimeUnit

class VixSrcExtractor : Extractor() {

    override val name = "VixSrc"
    override val mainUrl = "https://vixsrc.to"

    fun server(videoType: Video.Type): Video.Server {
        return Video.Server(
            id = name,
            name = name,
            src = when (videoType) {
                is Video.Type.Episode -> "$mainUrl/tv/${videoType.tvShow.id}/${videoType.season.number}/${videoType.number}"
                is Video.Type.Movie -> "$mainUrl/movie/${videoType.id}"
            },
        )
    }

    override suspend fun extract(link: String): Video {
        val service = VixSrcExtractorService.build(mainUrl)
        val source = service.getSource(link.replace(mainUrl, ""))

        val scriptText = source.body().selectFirst("script")?.data() ?: ""
        
        val videoId = scriptText
            .substringAfter("window.video = {", "")
            .substringAfter("id: '", "")
            .substringBefore("',", "")
            .trim()

        val token = scriptText
            .substringAfter("window.masterPlaylist", "")
            .substringAfter("'token': '", "")
            .substringBefore("',", "")
            .trim()

        val expires = scriptText
            .substringAfter("window.masterPlaylist", "")
            .substringAfter("'expires': '", "")
            .substringBefore("',", "")
            .trim()

        val hasBParam = scriptText
            .substringAfter("url:", "")
            .substringBefore(",", "")
            .contains("b=1")

        val canPlayFHD = scriptText.contains("window.canPlayFHD = true")

        val masterParams = mutableMapOf<String, String>()
        masterParams["token"] = token
        masterParams["expires"] = expires

        if (hasBParam) masterParams["b"] = "1"
        if (canPlayFHD) masterParams["h"] = "1"

        val providerLang = UserPreferences.currentProvider?.language ?: "en"
        masterParams["lang"] = providerLang

        val baseUrl = "https://vixsrc.to/playlist/${videoId}"
        val httpUrlBuilder = baseUrl.toHttpUrlOrNull()?.newBuilder()
            ?: throw IllegalArgumentException("Invalid base URL")
        masterParams.forEach { (key, value) -> httpUrlBuilder.addQueryParameter(key, value) }
        val finalUrl = httpUrlBuilder.build().toString()

        val finalHeaders = mutableMapOf("Referer" to mainUrl, "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
        
        var videoSource = finalUrl

        try {
            val client = OkHttpClient.Builder()
                .dns(DnsResolver.doh)
                .readTimeout(15, TimeUnit.SECONDS)
                .build()
            val headersBuilder = okhttp3.Headers.Builder()
            finalHeaders.forEach { (k, v) -> headersBuilder.add(k, v) }
            val request = Request.Builder().url(finalUrl).headers(headersBuilder.build()).build()
            
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful && response.body != null) {
                    var playlistContent = response.body!!.string()
                    val langCode = providerLang
                    val baseUri = response.request.url
                    
                    Log.d("SmartSubtitleLog", "--- VixSrc Subtitle Processing START ($langCode) ---")

                    val lines = playlistContent.lines()
                    val finalLines = mutableListOf<String>()
                    val uriRegex = """URI=["']([^"']+)["']""".toRegex()

                    for (line in lines) {
                        var patchedLine = line
                        
                        if (line.startsWith("#")) {
                            patchedLine = uriRegex.replace(line) { matchResult ->
                                val relative = matchResult.groupValues[1]
                                if (relative.startsWith("http") || relative.startsWith("data:")) matchResult.value
                                else "URI=\"${baseUri.resolve(relative) ?: relative}\""
                            }
                        } else if (line.isNotBlank() && !line.startsWith("#")) {
                            patchedLine = baseUri.resolve(line)?.toString() ?: line
                        }

                        if (patchedLine.startsWith("#EXT-X-MEDIA:TYPE=AUDIO")) {
                            patchedLine = patchedLine.replace(Regex("DEFAULT=YES", RegexOption.IGNORE_CASE), "DEFAULT=NO")
                                                     .replace(Regex("AUTOSELECT=YES", RegexOption.IGNORE_CASE), "AUTOSELECT=NO")
                            
                            val isTargetAudio = patchedLine.contains("LANGUAGE=\"$langCode\"", ignoreCase = true) || 
                                                patchedLine.contains("NAME=\"$langCode\"", ignoreCase = true) ||
                                                (langCode == "it" && patchedLine.contains("Italian", ignoreCase = true)) ||
                                                (langCode == "en" && patchedLine.contains("English", ignoreCase = true))
                            
                            if (isTargetAudio) {
                                patchedLine = patchedLine.replace("DEFAULT=NO", "DEFAULT=YES")
                                                         .replace("AUTOSELECT=NO", "AUTOSELECT=YES")
                            }
                            finalLines.add(patchedLine)
                        } else if (patchedLine.startsWith("#EXT-X-MEDIA:TYPE=SUBTITLES")) {
                            val trackName = patchedLine.substringAfter("NAME=\"", "Unknown").substringBefore("\"")
                            val trackLang = patchedLine.substringAfter("LANGUAGE=\"", "").substringBefore("\"")
                            
                            // RESET SEMPRE
                            patchedLine = patchedLine.replace(Regex("DEFAULT=YES", RegexOption.IGNORE_CASE), "DEFAULT=NO")
                                                     .replace(Regex("AUTOSELECT=YES", RegexOption.IGNORE_CASE), "AUTOSELECT=NO")
                            
                            // LOGICA: Se il nome contiene "forced" E la lingua è quella giusta, ATTIVA.
                            val isForced = trackName.contains("forced", ignoreCase = true) || trackLang.contains("forced", ignoreCase = true) || patchedLine.contains("FORCED=YES", ignoreCase = true)
                            val isRightLanguage = trackLang.contains(langCode, ignoreCase = true) || 
                                                  trackName.contains(langCode, ignoreCase = true) ||
                                                  (langCode == "it" && trackName.contains("Italian", ignoreCase = true)) ||
                                                  (langCode == "en" && trackName.contains("English", ignoreCase = true))

                            if (isForced && isRightLanguage) {
                                patchedLine = patchedLine.replace("DEFAULT=NO", "DEFAULT=YES")
                                                         .replace("AUTOSELECT=NO", "AUTOSELECT=YES")
                                Log.i("SmartSubtitleLog", "[VixSrc] ENABLED Forced: $trackName")
                            } else {
                                Log.d("SmartSubtitleLog", "[VixSrc] Disabled: $trackName")
                            }
                            finalLines.add(patchedLine)
                        } else {
                            finalLines.add(patchedLine)
                        }
                    }
                    Log.d("SmartSubtitleLog", "--- VixSrc Subtitle Processing END ---")
                    
                    val base64Manifest = Base64.encodeToString(finalLines.joinToString("\n").toByteArray(), Base64.NO_WRAP)
                    videoSource = "data:application/vnd.apple.mpegurl;base64,$base64Manifest"
                }
            }
        } catch (e: Exception) {
            Log.e("VixSrcDebug", "Error in patching: ${e.message}")
        }

        return Video(
            source = videoSource,
            subtitles = listOf(),
            type = MimeTypes.APPLICATION_M3U8,
            headers = finalHeaders
        )
    }

    private interface VixSrcExtractorService {
        companion object {
            val client = OkHttpClient.Builder()
                .dns(DnsResolver.doh)
                .build()
            fun build(baseUrl: String): VixSrcExtractorService {
                return Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(client)
                    .addConverterFactory(JsoupConverterFactory.create())
                    .build()
                    .create(VixSrcExtractorService::class.java)
            }
        }

        @GET
        @Headers("User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
        suspend fun getSource(@Url url: String): Document

        data class WindowVideo(
            @SerializedName("id") val id: Int,
            @SerializedName("filename") val filename: String
        )

        data class WindowParams(
            @SerializedName("token") val token: String?,
            @SerializedName("expires") val expires: String?
        )
    }
}
