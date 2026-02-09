package com.streamflixreborn.streamflix.extractors

import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import com.streamflixreborn.streamflix.models.Video
import org.jsoup.nodes.Document
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Url
import com.streamflixreborn.streamflix.utils.DnsResolver
import okhttp3.OkHttpClient

class UpZurExtractor : Extractor() {

    override val name = "UpZur"
    override val mainUrl = "https://upzur.com"

    override suspend fun extract(link: String): Video {
        val service = Service.build(mainUrl)
        val document = service.get(link)
        val html = document.html()

        val arrayMatch = Regex("""var uHo4sc = \[(.*?)\]""").find(html)
        val arrayData = arrayMatch?.groupValues?.get(1) ?: throw Exception("Array not found")
        
        val elements = arrayData.split(",").map { 
            it.trim().removeSurrounding("\"").removeSurrounding("'")
        }

        val decodedString = elements.reversed().joinToString("") { part ->
            val builder = StringBuilder()
            var i = 0
            while (i < part.length) {
                if (part.startsWith("\\x", i) && i + 3 < part.length) {
                    val hex = part.substring(i + 2, i + 4)
                    builder.append(hex.toInt(16).toChar())
                    i += 4
                } else if (part.startsWith("\\u", i) && i + 5 < part.length) {
                    val hex = part.substring(i + 2, i + 6)
                    builder.append(hex.toInt(16).toChar())
                    i += 6
                } else {
                    builder.append(part[i])
                    i++
                }
            }
            builder.toString()
        }

        val streamUrl = Regex("""src\s*=\s*["']([^"']+)["']""").find(decodedString)?.groupValues?.get(1)
            ?: throw Exception("Stream URL not found in decoded script")

        return Video(
            source = streamUrl
        )
    }

    private interface Service {
        companion object {
            val client = OkHttpClient.Builder()
                .dns(DnsResolver.doh)
                .build()

            fun build(baseUrl: String): Service {
                val retrofit = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(client)
                    .addConverterFactory(JsoupConverterFactory.create())
                    .build()

                return retrofit.create(Service::class.java)
            }
        }

        @GET
        suspend fun get(@Url url: String): Document
    }
}
