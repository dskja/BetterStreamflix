package com.streamflixreborn.streamflix.extractors

import com.streamflixreborn.streamflix.models.Video
import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import org.jsoup.nodes.Document
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Url
import java.net.URL

class VidsonicExtractor : Extractor() {
    override val name = "Vidsonic"
    override val mainUrl = "https://vidsonic.net"

    companion object {
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
    }

    override suspend fun extract(link: String): Video {
        val service = Service.build(mainUrl)

        val source = service.getSource(
            url = link,
            userAgent = DEFAULT_USER_AGENT
        )

        val encodedRegex = Regex("""const\s+\w+\s*=\s*'([a-fA-F0-9|]{50,})';""")
        val encodedMatch = encodedRegex.find(source.toString())
            ?: throw Exception("Could not find the encoded m3u8 string in Vidsonic HTML")

        val encodedStr = encodedMatch.groupValues[1]

        val cleaned = encodedStr.replace("|", "")
        
        val asciiBuilder = StringBuilder()
        for (i in cleaned.indices step 2) {
            val hexPair = cleaned.substring(i, i + 2)
            asciiBuilder.append(hexPair.toInt(16).toChar())
        }
        
        val sourceUrl = asciiBuilder.toString().reversed()

        return Video(
            source = sourceUrl,
            headers = mapOf(
                "Referer" to mainUrl,
                "Origin" to mainUrl
            )
        )
    }

    private interface Service {
        @GET
        suspend fun getSource(
            @Url url: String,
            @retrofit2.http.Header("User-Agent") userAgent: String = DEFAULT_USER_AGENT
        ): Document

        companion object {
            fun build(baseUrl: String): Service {
                val retrofit = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .addConverterFactory(JsoupConverterFactory.create())
                    .build()
                return retrofit.create(Service::class.java)
            }
        }
    }
}
