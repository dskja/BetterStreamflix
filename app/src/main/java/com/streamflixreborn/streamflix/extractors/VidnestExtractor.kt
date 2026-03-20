package com.streamflixreborn.streamflix.extractors

import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import com.streamflixreborn.streamflix.models.Video
import com.streamflixreborn.streamflix.utils.UserPreferences
import org.jsoup.nodes.Document
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Url

class VidnestExtractor : Extractor() {

    override val name = "Vidnest"
    override val mainUrl = "https://vidnest.io"

    fun extractSubtitles(text: String): List<Video.Subtitle> {
        val tracksBlock = Regex("""tracks\s*:\s*\[(.*?)]""", RegexOption.DOT_MATCHES_ALL)
            .find(text)?.groupValues?.get(1) ?: return emptyList()

        val objectRegex = Regex("""\{(.*?)\}""", RegexOption.DOT_MATCHES_ALL)

        return objectRegex.findAll(tracksBlock).mapNotNull { match ->
            val obj = match.groupValues[1]

            val kind = Regex("""kind\s*:\s*"([^"]+)"""").find(obj)?.groupValues?.get(1)
            if (kind != "captions") return@mapNotNull null

            val rawFile = Regex("""file\s*:\s*"([^"]+)"""").find(obj)?.groupValues?.get(1)
            val label = Regex("""label\s*:\s*"([^"]+)"""").find(obj)?.groupValues?.get(1)
            val default = Regex(""""default"\s*:\s*(true|false)""")
                .find(obj)?.groupValues?.get(1)?.toBoolean() ?: false

            if (rawFile == null || label == null) return@mapNotNull null

            val file = Regex("""https://[^\s"']+""")
                .find(rawFile)?.value ?: return@mapNotNull null

            Video.Subtitle(
                file = file,
                label = label,
                initialDefault = default,
                default = if (UserPreferences.serverAutoSubtitlesDisabled) false else default
            )
        }.toList()
    }

    override suspend fun extract(link: String): Video {
        val service = Service.build(mainUrl)
        val doc = service.get(link)

        val scriptTags = doc.select("script[type=text/javascript]")

        var m3u8: String? = null

        var subtitles : List<Video.Subtitle> = emptyList();

        for (script in scriptTags) {
            val scriptData = script.data()
            if ("jwplayer" in scriptData && "sources" in scriptData && "file" in scriptData) {
                val fileRegex = Regex("""file\s*:\s*["']([^"']+)["']""")
                val match = fileRegex.find(scriptData)
                if (match != null) {
                    m3u8 = match.groupValues[1]
                    subtitles = extractSubtitles(scriptData)
                    break
                }
            }
        }

        if (m3u8 == null) {
            throw Exception("Stream URL not found in script tags")
        }

        return Video(
            source = m3u8,
            subtitles = subtitles,
            useServerSubtitleSetting = true
        )
    }

    private interface Service {
        companion object {
            fun build(baseUrl: String): Service {
                val retrofit = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .addConverterFactory(JsoupConverterFactory.create())
                    .build()
                return retrofit.create(Service::class.java)
            }
        }

        @GET
        suspend fun get(@Url url: String): Document
    }
}
