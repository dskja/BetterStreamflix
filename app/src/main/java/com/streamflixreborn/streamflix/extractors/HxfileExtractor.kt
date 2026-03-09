package com.streamflixreborn.streamflix.extractors

import android.util.Base64
import com.streamflixreborn.streamflix.models.Video
import com.streamflixreborn.streamflix.utils.DnsResolver
import com.streamflixreborn.streamflix.utils.JsUnpacker
import retrofit2.http.GET
import retrofit2.http.Url
import retrofit2.Retrofit
import org.jsoup.nodes.Document
import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory

class HxfileExtractor : Extractor() {
    override val name = "Hxfile"
    override val mainUrl = "https://hxfile.co"

    companion object {
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
    }

    override suspend fun extract(link: String): Video {
        val fileCode = link.substringAfterLast("/").substringBefore(".html")
        val embedUrl = if (link.contains("/embed-")) link else "$mainUrl/embed-$fileCode.html"

        val service = Service.build(mainUrl)
        val source = service.getSource(
            url = embedUrl,
            referer = link
        )

        val packedJS = Regex("(eval\\(function\\(p,a,c,k,e,d\\)(.|\\n)*?)</script>")
            .find(source.toString())?.groupValues?.get(1)
            ?: throw Exception("Packed JS not found")

        var unpacked = JsUnpacker(packedJS).unpack()
            ?: throw Exception("Unpacked is null")

        val xorRegex = """var\s+(_[0-9a-f]{6})\s*=\s*"([^"]+)".*?var\s+(_0x[0-9a-f]{6})\s*=\s*_[0-9a-z]{6}\(\)""".toRegex(RegexOption.DOT_MATCHES_ALL)
        xorRegex.find(unpacked)?.let { match ->
            val payload = match.groupValues[2]
            val key = match.groupValues[3]
            try {
                val data = Base64.decode(payload, Base64.DEFAULT)
                val decrypted = StringBuilder()
                for (i in data.indices) {
                    decrypted.append((data[i].toInt() xor key[i % key.length].code).toChar())
                }
                unpacked = decrypted.toString()
            } catch (e: Exception) {}
        }

        val finalUrl = Regex("""sources[\s\S]*?["']?file["']?\s*[:=]\s*["']([^"']+)["']""")
            .find(unpacked)?.groupValues?.get(1)
            ?: throw Exception("No file link found in unpacked JS")

        return Video(source = finalUrl)
    }

    private interface Service {
        @GET
        suspend fun getSource(
            @Url url: String,
            @retrofit2.http.Header("User-Agent") userAgent: String = DEFAULT_USER_AGENT,
            @retrofit2.http.Header("Referer") referer: String
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
