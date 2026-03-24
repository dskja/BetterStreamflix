package com.streamflixreborn.streamflix.extractors

import android.util.Base64
import android.util.Log
import androidx.media3.common.MimeTypes
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import com.streamflixreborn.streamflix.models.Video
import com.streamflixreborn.streamflix.utils.NetworkClient
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Url
import retrofit2.http.Header
import okhttp3.Request
import java.util.concurrent.TimeUnit

class VixcloudExtractor(
    private val preferredLanguage: String? = null,
    private var customReferer: String? = null
) : Extractor() {

    override val name = "vixcloud"
    override val mainUrl = "https://vixcloud.co/"

    companion object {
        private val client = NetworkClient.default.newBuilder()
            .readTimeout(15, TimeUnit.SECONDS)
            .connectTimeout(10, TimeUnit.SECONDS)
            .build()

        private val retrofitCache = mutableMapOf<String, VixcloudExtractorService>()

        private fun getService(baseUrl: String): VixcloudExtractorService {
            return retrofitCache.getOrPut(baseUrl) {
                Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .addConverterFactory(JsoupConverterFactory.create())
                    .client(client)
                    .build()
                    .create(VixcloudExtractorService::class.java)
            }
        }
    }

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
        Log.d("VixcloudDebug", "Extracting link: $link with preferredLanguage: $preferredLanguage")
        
        val uri = link.toHttpUrlOrNull() ?: throw Exception("Invalid Vixcloud link")
        val currentMainUrl = "${uri.scheme}://${uri.host}/"
        val referer = customReferer ?: currentMainUrl
        
        val service = getService(currentMainUrl)
        val source = try {
            service.getSource(uri.encodedPath + if (uri.encodedQuery != null) "?" + uri.encodedQuery else "", referer = referer)
        } catch (e: Exception) {
            Log.e("VixcloudDebug", "Failed to get source from $link: ${e.message}")
            throw e
        }

        val scriptText = source.body().selectFirst("script")?.data() ?: ""
        
        var videoJson = scriptText
            .substringAfter("window.video = ", "")
            .substringBefore(";", "")
            .trim()
        
        // Fallback: Prova a cercare window.video senza spazi o con altre varianti
        if (videoJson.isEmpty()) {
            videoJson = scriptText
                .substringAfter("window.video=", "")
                .substringBefore(";", "")
                .trim()
        }

        if (videoJson.isNotEmpty()) {
            videoJson = sanitizeJsonKeysAndQuotes(videoJson)
            videoJson = removeTrailingCommaFromJsonObjectString(videoJson)
            if (!videoJson.startsWith("{") && videoJson.contains(":")) videoJson = "{$videoJson"
            if (!videoJson.endsWith("}") && videoJson.contains(":")) videoJson = "$videoJson}"
        } else {
            Log.e("VixcloudDebug", "Could not find window.video in script")
        }

        val paramsObjectContent = scriptText
            .substringAfter("window.masterPlaylist", "")
            .substringAfter("params: {", "")
            .substringBefore("},", "")
            .trim()
        
        // Altro fallback per i parametri
        val tokenFallback = scriptText.substringAfter("token: \"", "").substringBefore("\"")
        val expiresFallback = scriptText.substringAfter("expires: \"", "").substringBefore("\"")

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
        } else if (tokenFallback.isNotEmpty()) {
            masterParams["token"] = tokenFallback
        }
        
        if (masterPlaylist?.expires != null) {
            masterParams["expires"] = masterPlaylist.expires
        } else if (expiresFallback.isNotEmpty()) {
            masterParams["expires"] = expiresFallback
        }

        val currentParams = link.split("&")
            .map { param -> param.split("=") }
            .filter { it.size == 2 }
            .associate { it[0] to it[1] }

        if (hasBParam) masterParams["b"] = "1"
        if (currentParams.containsKey("canPlayFHD")) masterParams["h"] = "1"
        
        preferredLanguage?.let { masterParams["language"] = it }

        val baseUrl = "https://${uri.host}/playlist/${windowVideo.id}"
        val httpUrlBuilder = baseUrl.toHttpUrlOrNull()?.newBuilder()
            ?: throw IllegalArgumentException("Invalid base URL")
        masterParams.forEach { (key, value) -> httpUrlBuilder.addQueryParameter(key, value) }
        val finalUrl = httpUrlBuilder.build().toString()

        val finalHeaders = mutableMapOf("Referer" to currentMainUrl, "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        
        preferredLanguage?.let { lang ->
            finalHeaders["Accept-Language"] = if (lang == "en") "en-US,en;q=0.9" else "it-IT,it;q=0.9"
            finalHeaders["Cookie"] = "language=$lang"
        }

        var videoSource = finalUrl

        if (preferredLanguage != null) {
            try {
                val headersBuilder = okhttp3.Headers.Builder()
                finalHeaders.forEach { (k, v) -> headersBuilder.add(k, v) }
                val request = Request.Builder().url(finalUrl).headers(headersBuilder.build()).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful && response.body != null) {
                        var playlistContent = response.body!!.string()
                        val langCode = preferredLanguage
                        val altLangCode = if (langCode == "en") "eng" else if (langCode == "it") "ita" else langCode
                        val baseUri = response.request.url
                        
                        Log.d("SmartSubtitleLog", "--- Vixcloud Subtitle Processing START ($langCode) ---")

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
                                    Log.i("SmartSubtitleLog", "[Vixcloud] ENABLED Forced: $trackName")
                                } else {
                                    Log.d("SmartSubtitleLog", "[Vixcloud] Disabled: $trackName")
                                }
                                finalLines.add(patchedLine)
                            } else {
                                finalLines.add(patchedLine)
                            }
                        }
                        Log.d("SmartSubtitleLog", "--- Vixcloud Subtitle Processing END ---")
                        
                        val base64Manifest = Base64.encodeToString(finalLines.joinToString("\n").toByteArray(), Base64.NO_WRAP)
                        videoSource = "data:application/vnd.apple.mpegurl;base64,$base64Manifest"
                    }
                }
            } catch (e: Exception) {
                Log.e("VixcloudDebug", "Error in patching: ${e.message}")
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

        @GET
        @Headers(
            "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
            "Accept: text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
            "Accept-Language: it-IT,it;q=0.9,en-US;q=0.8,en;q=0.7"
        )
        suspend fun getSource(@Url url: String, @Header("Referer") referer: String): Document

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