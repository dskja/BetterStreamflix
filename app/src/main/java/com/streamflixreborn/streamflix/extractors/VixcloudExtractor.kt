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
import java.util.concurrent.TimeUnit

class VixcloudExtractor(private val preferredLanguage: String? = null) : Extractor() {

    override val name = "vixcloud"
    override val mainUrl = "https://vixcloud.co/"

    private fun sanitizeJsonKeysAndQuotes(jsonLikeString: String): String {
        var temp = jsonLikeString
        temp = temp.replace("'", "\"")
        temp = Regex("""(\b(?:id|filename|token|expires|asn)\b)\s*:""").replace(temp) { matchResult ->
            "\"${matchResult.groupValues[1]}\":"
        }
        return temp
    }

    private fun removeTrailingCommaFromJsonObjectString(jsonString: String): String {
        val temp = jsonString.trim()
        val lastBraceIndex = temp.lastIndexOf('}')
        if (lastBraceIndex > 0 && temp.startsWith("{")) {
            var charIndexBeforeBrace = lastBraceIndex - 1
            while (charIndexBeforeBrace >= 0 && temp[charIndexBeforeBrace].isWhitespace()) {
                charIndexBeforeBrace--
            }
            if (charIndexBeforeBrace >= 0 && temp[charIndexBeforeBrace] == ',') {
                return temp.take(charIndexBeforeBrace) + temp.substring(charIndexBeforeBrace + 1)
            }
        }
        return jsonString
    }

    override suspend fun extract(link: String): Video {
        Log.d("VixcloudDebug", "Extracting with preferredLanguage: $preferredLanguage")
        val service = VixcloudExtractorService.build(mainUrl)
        val source = service.getSource(link.replace(mainUrl, ""))

        val scriptText = source.body().selectFirst("script")?.data() ?: ""
        
        var videoJson = scriptText
            .substringAfter("window.video = ", "")
            .substringBefore(";", "")
            .trim()
        if (videoJson.isNotEmpty()) {
            videoJson = sanitizeJsonKeysAndQuotes(videoJson)
            videoJson = removeTrailingCommaFromJsonObjectString(videoJson)
            if (!videoJson.startsWith("{") && videoJson.contains(":")) videoJson = "{$videoJson"
            if (!videoJson.endsWith("}") && videoJson.contains(":")) videoJson = "$videoJson}"
        }

        val paramsObjectContent = scriptText
            .substringAfter("window.masterPlaylist", "")
            .substringAfter("params: {", "")
            .substringBefore("},", "")
            .trim()

        var masterPlaylistJson: String
        if (paramsObjectContent.isNotEmpty()) {
            var processedParams = sanitizeJsonKeysAndQuotes(paramsObjectContent)
            processedParams = processedParams.trim()
            if (processedParams.endsWith(",")) {
                processedParams = processedParams.dropLast(1).trim()
            }
            masterPlaylistJson = "{${processedParams}}"
        } else {
            masterPlaylistJson = "{}"
        }

        val hasBParam = scriptText
            .substringAfter("url:", "")
            .substringBefore(",", "")
            .contains("b=1")

        val gson = Gson()
        val windowVideo = gson.fromJson(videoJson, VixcloudExtractorService.WindowVideo::class.java)
        val masterPlaylist = gson.fromJson(masterPlaylistJson, VixcloudExtractorService.WindowParams::class.java)

        val masterParams = mutableMapOf<String, String>()
        if (masterPlaylist?.token != null) {
            masterParams["token"] = masterPlaylist.token
        }
        if (masterPlaylist?.expires != null) {
            masterParams["expires"] = masterPlaylist.expires
        }

        val currentParams = link.split("&")
            .map { param -> param.split("=") }
            .filter { it.size == 2 }
            .associate { it[0] to it[1] }

        if (hasBParam) masterParams["b"] = "1"
        if (currentParams.containsKey("canPlayFHD")) masterParams["h"] = "1"
        
        preferredLanguage?.let { masterParams["language"] = it }

        val baseUrl = "https://vixcloud.co/playlist/${windowVideo.id}"
        val httpUrlBuilder = baseUrl.toHttpUrlOrNull()?.newBuilder()
            ?: throw IllegalArgumentException("Invalid base URL")
        masterParams.forEach { (key, value) -> httpUrlBuilder.addQueryParameter(key, value) }
        val finalUrl = httpUrlBuilder.build().toString()

        val finalHeaders = mutableMapOf("Referer" to mainUrl, "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
        
        preferredLanguage?.let { lang ->
            finalHeaders["Accept-Language"] = if (lang == "en") "en-US,en;q=0.9" else "it-IT,it;q=0.9"
            finalHeaders["Cookie"] = "language=$lang"
        }

        var videoSource = finalUrl

        if (preferredLanguage != null) {
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
                        val langCode = if (preferredLanguage == "en") "en" else "it"
                        val altLangCode = if (langCode == "en") "eng" else "ita"
                        
                        Log.d("VixcloudDebug", "Aggressive Patching M3U8 for $langCode (Requested: $preferredLanguage)...")

                        // 1. Resolve relative URLs to absolute URLs
                        val baseUri = response.request.url
                        playlistContent = playlistContent.lines().joinToString("\n") { line ->
                            if (line.startsWith("#") || line.isBlank()) line
                            else baseUri.resolve(line)?.toString() ?: line
                        }

                        // 2. Filter audio tracks: KEEP ONLY the target language and set it as default
                        val lines = playlistContent.lines().toMutableList()
                        val finalLines = mutableListOf<String>()
                        var targetTrackFound = false

                        for (line in lines) {
                            if (line.startsWith("#EXT-X-MEDIA:TYPE=AUDIO")) {
                                // Check if this is the track we want to keep/set as default
                                val isTarget = line.contains("LANGUAGE=\"$langCode\"", ignoreCase = true) || 
                                               line.contains("LANGUAGE=\"$altLangCode\"", ignoreCase = true) ||
                                               line.contains("NAME=\"$langCode\"", ignoreCase = true) ||
                                               line.contains("NAME=\"$altLangCode\"", ignoreCase = true) ||
                                               (langCode == "en" && line.contains("English", ignoreCase = true)) ||
                                               (langCode == "it" && line.contains("Italian", ignoreCase = true))
                                
                                if (isTarget) {
                                    // Keep this track and force it as default, removing any conflicting flags
                                    var patchedLine = line.replace("AUTOSELECT=YES", "")
                                                    .replace("DEFAULT=YES", "")
                                                    .replace(",,", ",") // Clean up commas
                                    
                                    if (!patchedLine.contains("DEFAULT=YES")) {
                                        // Add DEFAULT=YES ensuring it's appended correctly (often before the closing quote if present)
                                        patchedLine = if (patchedLine.contains("\"")) {
                                            patchedLine.substringBeforeLast("\"") + "\",DEFAULT=YES"
                                        } else {
                                            patchedLine + ",DEFAULT=YES"
                                        }
                                    }
                                    finalLines.add(patchedLine)
                                    targetTrackFound = true
                                    Log.d("VixcloudDebug", "Keeping target audio track: ${line}")
                                } else {
                                    // Skip/Remove other languages
                                    Log.d("VixcloudDebug", "Removing non-target audio track: ${line.substringAfter("NAME=\"").substringBefore("\"")}")
                                }
                            } else {
                                finalLines.add(line)
                            }
                        }
                        
                        if (targetTrackFound) {
                            playlistContent = finalLines.joinToString("\n")
                            Log.d("VixcloudDebug", "Successfully created minimalist M3U8. Creating data URI.")
                            val base64Manifest = Base64.encodeToString(playlistContent.toByteArray(), Base64.NO_WRAP)
                            videoSource = "data:application/vnd.apple.mpegurl;base64,$base64Manifest"
                        } else {
                            Log.w("VixcloudDebug", "Target language $langCode track not found in manifest. Reverting to original URL.")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("VixcloudDebug", "Error in aggressive patching: ${e.message}", e)
            }
        }

        return Video(
            source = videoSource,
            subtitles = listOf(),
            type = MimeTypes.APPLICATION_M3U8,
            headers = finalHeaders
        )
    }

    private interface VixcloudExtractorService {
        companion object {
            fun build(baseUrl: String): VixcloudExtractorService {
                val client = OkHttpClient.Builder()
                    .dns(DnsResolver.doh)
                    .build()
                return Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .addConverterFactory(JsoupConverterFactory.create())
                    .client(client)
                    .build()
                    .create(VixcloudExtractorService::class.java)
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