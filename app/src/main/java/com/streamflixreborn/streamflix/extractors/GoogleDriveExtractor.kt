package com.streamflixreborn.streamflix.extractors

import androidx.media3.common.MimeTypes
import com.google.gson.JsonParser
import com.streamflixreborn.streamflix.models.Video
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

class GoogleDriveExtractor : Extractor() {

    override val name = "GoogleDrive"
    override val mainUrl = "https://drive.google.com"

    companion object {
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36"
    }

    override suspend fun extract(link: String): Video {
        val fileIdMatch = Regex("""/file/d/([^/]+)""").find(link)
        val fileId = fileIdMatch?.groupValues?.get(1)
            ?: throw Exception("File ID not found in URL")

        val service = Service.build(mainUrl)
        
        val responseBody = service.getPlayback(
            fileId = fileId,
            key = "AIzaSyDVQw45DwoYh632gvsP5vPDqEKvb-Ywnb8",
            unique = "gc999"
        )

        val responseString = responseBody.string()
        val jsonObject = JsonParser.parseString(responseString).asJsonObject
        
        val mediaStreamingData = jsonObject.getAsJsonObject("mediaStreamingData")
        val hlsManifestUrl = mediaStreamingData.get("hlsManifestUrl")?.asString
            ?: throw Exception("HLS manifest URL not found")

        val headers = mapOf(
            "Accept" to "*/*",
            "Accept-Language" to "it-IT,it;q=0.9,en-US;q=0.8,en;q=0.7",
            "Cache-Control" to "no-cache",
            "Connection" to "keep-alive",
            "Origin" to "https://youtube.googleapis.com",
            "Pragma" to "no-cache",
            "Referer" to "https://youtube.googleapis.com/",
            "Sec-Fetch-Dest" to "empty",
            "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "cross-site",
            "Sec-Fetch-Storage-Access" to "active",
            "User-Agent" to DEFAULT_USER_AGENT,
            "X-Browser-Channel" to "stable",
            "X-Browser-Copyright" to "Copyright 2025 Google LLC. All Rights reserved.",
            "X-Browser-Validation" to "Aj9fzfu+SaGLBY9Oqr3S7RokOtM=",
            "X-Browser-Year" to "2025",
            "sec-ch-ua" to "\"Chromium\";v=\"142\", \"Google Chrome\";v=\"142\", \"Not_A Brand\";v=\"99\"",
            "sec-ch-ua-mobile" to "?0",
            "sec-ch-ua-platform" to "\"Windows\"",
            "Content-Type" to "application/x-www-form-urlencoded"
        )

        return Video(
            source = hlsManifestUrl,
            headers = headers,
            type = MimeTypes.APPLICATION_M3U8
        )
    }

    private interface Service {

        companion object {
            fun build(baseUrl: String): Service {
                val client = OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        val original = chain.request()
                        val request = original.newBuilder()
                            .header("accept", "*/*")
                            .header("accept-language", "it-IT,it;q=0.9,en-US;q=0.8,en;q=0.7")
                            .header("cache-control", "no-cache")
                            .header("origin", baseUrl)
                            .header("pragma", "no-cache")
                            .header("priority", "u=1, i")
                            .header("referer", "$baseUrl/")
                            .header("sec-ch-ua", "\"Chromium\";v=\"142\", \"Google Chrome\";v=\"142\", \"Not_A Brand\";v=\"99\"")
                            .header("sec-ch-ua-mobile", "?0")
                            .header("sec-ch-ua-platform", "\"Windows\"")
                            .header("sec-fetch-dest", "empty")
                            .header("sec-fetch-mode", "cors")
                            .header("sec-fetch-site", "cross-site")
                            .header("user-agent", DEFAULT_USER_AGENT)
                            .header("x-browser-channel", "stable")
                            .header("x-browser-copyright", "Copyright 2025 Google LLC. All Rights reserved.")
                            .header("x-browser-validation", "Aj9fzfu+SaGLBY9Oqr3S7RokOtM=")
                            .header("x-browser-year", "2025")
                            .header("x-clientdetails", "appVersion=5.0%20(Windows%20NT%2010.0%3B%20Win64%3B%20x64)%20AppleWebKit%2F537.36%20(KHTML%2C%20like%20Gecko)%20Chrome%2F142.0.0.0%20Safari%2F537.36&platform=Win32&userAgent=Mozilla%2F5.0%20(Windows%20NT%2010.0%3B%20Win64%3B%20x64)%20AppleWebKit%2F537.36%20(KHTML%2C%20like%20Gecko)%20Chrome%2F142.0.0.0%20Safari%2F537.36")
                            .header("x-goog-encode-response-if-executable", "base64")
                            .header("x-javascript-user-agent", "google-api-javascript-client/1.1.0")
                            .header("x-requested-with", "XMLHttpRequest")
                            .build()
                        chain.proceed(request)
                    }
                    .build()

                val retrofit = Retrofit.Builder()
                    .baseUrl("https://content-workspacevideo-pa.googleapis.com/")
                    .client(client)
                    .build()

                return retrofit.create(Service::class.java)
            }
        }

        @GET("v1/drive/media/{fileId}/playback")
        suspend fun getPlayback(
            @Path("fileId") fileId: String,
            @Query("key") key: String,
            @Query("\$unique") unique: String
        ): ResponseBody
    }
}

