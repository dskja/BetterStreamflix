package com.streamflixreborn.streamflix.extractors

import com.streamflixreborn.streamflix.models.Video
import com.streamflixreborn.streamflix.utils.JsUnpacker
import com.streamflixreborn.streamflix.utils.UserPreferences
import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import org.jsoup.nodes.Document
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Url
import java.net.URL

class StreamrubyExtractor: Extractor() {
    override val name = "Streamruby"
    override val mainUrl = "https://streamruby.com"
    override val aliasUrls = listOf("https://stmruby.com", "https://rubystm.com", "https://rubyvid.com", "https://moflix-stream.fans")

    companion object {
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
    }

    override suspend fun extract(link: String): Video {
        val baseUrl = URL(link).protocol + "://" + URL(link).host
        val service = Service.build(baseUrl)

        val source = service.getSource(
            url = link,
            userAgent = DEFAULT_USER_AGENT
        )

        val packedJS = Regex("(eval\\(function\\(p,a,c,k,e,d\\)(.|\\n)*?)</script>")
            .find(source.toString())?.let { it.groupValues[1] }
            ?: throw Exception("Packed JS not found")

        val unPacked = JsUnpacker(packedJS).unpack()
            ?: throw Exception("Unpacked is null")

        val finalUrl = Regex("""file\s*:\s*["']([^"']+)["']""")
            .find(unPacked)?.groupValues?.get(1)
            ?: throw Exception("No file link found in unpacked JS")

        return Video(source = finalUrl)
    }

    private interface Service {
        @GET
        suspend fun getSource(
            @Url url: String,
            @Header("User-Agent") userAgent: String = DEFAULT_USER_AGENT
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
