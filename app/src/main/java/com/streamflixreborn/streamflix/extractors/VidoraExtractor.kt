package com.streamflixreborn.streamflix.extractors

import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import com.streamflixreborn.streamflix.models.Video
import com.streamflixreborn.streamflix.utils.JsUnpacker
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Url

open class VidoraExtractor : Extractor() {

    override val name = "Vidora"
    override val mainUrl = "https://vidora.stream"

    companion object {
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36"
    }

    override suspend fun extract(link: String): Video {
        val service = Service.build(mainUrl)
        
        val response = service.get(link, referer = mainUrl)

        val packedJS = Regex("(eval\\(function\\(p,a,c,k,e,d\\)(.|\\n)*?)</script>")
            .find(response.toString())?.let { it.groupValues[1] }
            ?: throw Exception("Packed JS not found")

        val unpacked = JsUnpacker(packedJS).unpack()
            ?: throw Exception("Unpacked is null")



        var source: String? = null
        if ("jwplayer" in unpacked && "sources" in unpacked && "file" in unpacked) {
            val fileRegex = Regex("""file\s*:\s*["']([^"']+)["']""")
            source = fileRegex.find(unpacked)?.groupValues?.get(1)
        }

        if (source == null) throw Exception("No source found")

        return Video(
            source = source,
            headers = mapOf(
                "Referer" to mainUrl,
                "User-Agent" to DEFAULT_USER_AGENT
            )
        )
    }

    private interface Service {
        companion object {
            fun build(baseUrl: String): Service {
                val client = OkHttpClient.Builder().build()
                val retrofit = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .addConverterFactory(JsoupConverterFactory.create())
                    .client(client)
                    .build()

                return retrofit.create(Service::class.java)
            }
        }

        @GET
        suspend fun get(
            @Url url: String,
            @Header("Referer") referer: String,
            @Header("User-Agent") userAgent: String = DEFAULT_USER_AGENT
        ): Document
    }
}
