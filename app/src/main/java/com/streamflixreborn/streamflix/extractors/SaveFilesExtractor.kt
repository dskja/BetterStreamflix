package com.streamflixreborn.streamflix.extractors

import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import com.streamflixreborn.streamflix.models.Video
import org.jsoup.nodes.Document
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Query
import java.net.URL

class SaveFilesExtractor: Extractor() {

    override val name = "Savefiles"
    override val mainUrl = "https://savefiles.com/"
    override val aliasUrls = listOf("https://streamhls.to")

    override suspend fun extract(link: String): Video {
        val parsedUrl = URL(link)
        val pathParts = parsedUrl.path.split("/").filter { it.isNotEmpty() }
        if (pathParts.isEmpty()) {
            throw Exception("File code not found in URL")
        }
        
        val fileCode = pathParts.last().split("?")[0].trim()
        if (fileCode.isEmpty()) {
            throw Exception("File code not found in URL")
        }

        val baseUrl = parsedUrl.protocol + "://" + parsedUrl.host
        val service = SaveFilesExtractorService.build(baseUrl)
        val source = service.getDl(
            op = "embed",
            fileCode = fileCode,
            auto = "0",
            referer = ""
        )
        
        val scriptTags = source.select("script[type=text/javascript]")

        var m3u8: String? = null

        for (script in scriptTags) {
            val scriptData = script.data()
            if ("jwplayer" in scriptData && "sources" in scriptData && "file" in scriptData) {
                val fileRegex = Regex("""file\s*:\s*["']([^"']+)["']""")
                val match = fileRegex.find(scriptData)
                if (match != null) {
                    m3u8 = match.groupValues[1]
                    break
                }
            }
        }

        if (m3u8 == null) {
            throw Exception("Stream URL not found in script tags")
        }

        return Video(
            source = m3u8,
            subtitles = listOf()
        )
    }

    private interface SaveFilesExtractorService {
        companion object {
            fun build(baseUrl: String): SaveFilesExtractorService {
                val retrofitRedirected = Retrofit.Builder()
                    .baseUrl("$baseUrl/")
                    .addConverterFactory(JsoupConverterFactory.create())
                    .build()
                return retrofitRedirected.create(SaveFilesExtractorService::class.java)
            }
        }
        @GET("dl")
        suspend fun getDl(
            @Query("op") op: String,
            @Query("file_code") fileCode: String,
            @Query("auto") auto: String,
            @Query("referer") referer: String
        ): Document
    }
}