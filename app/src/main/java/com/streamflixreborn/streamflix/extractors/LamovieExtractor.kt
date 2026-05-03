package com.streamflixreborn.streamflix.extractors

import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import com.streamflixreborn.streamflix.models.Video
import com.streamflixreborn.streamflix.utils.JsUnpacker
import org.jsoup.nodes.Document
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Url

class LamovieExtractor : Extractor() {

    override val name = "Lamovie"
    override val mainUrl = "https://lamovie.link"
    override val aliasUrls = listOf("https://vimeos.net")

    override suspend fun extract(link: String): Video {
        val service = Service.build(mainUrl)

        val document = service.get(link)
        val packedJS = Regex("(eval\\(function\\(p,a,c,k,e,d\\)(.|\\n)*?)</script>")
            .find(document.toString())?.let { it.groupValues[1] }
            ?: throw Exception("Packed JS not found")
        
        val unPacked = JsUnpacker(packedJS).unpack()
            ?: throw Exception("Unpacked is null")
        
        val fileMatch = Regex("""file\s*:\s*["']([^"']+)["']""").find(unPacked)
        val streamUrl = fileMatch?.groupValues?.get(1)
            ?: throw Exception("No file found")

        val subtitles = Regex("""file\s*:\s*["']([^"']+)["']\s*,\s*label\s*:\s*["']([^"']+)["']""").findAll(
            Regex("""tracks\s*:\s*\[(.*?)]""", RegexOption.DOT_MATCHES_ALL).find(unPacked)
                ?.groupValues?.get(1)
                ?: ""
        ).map {
            Video.Subtitle(
                label = it.groupValues[2],
                file = it.groupValues[1],
            )
        }.filter { it.label != "Upload captions" }
        .toList()

        return Video(
            source = streamUrl ?: throw Exception("Can't retrieve source"),
            subtitles = subtitles,
            headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36",
                "Accept-Language" to "en-US,en;q=0.9"
            )
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
        @Headers(
            "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36",
            "Accept-Language: en-US,en;q=0.9"
        )
        suspend fun get(@Url url: String): Document
    }
}