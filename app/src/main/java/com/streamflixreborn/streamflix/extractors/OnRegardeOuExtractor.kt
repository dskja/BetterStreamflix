package com.streamflixreborn.streamflix.extractors

import com.streamflixreborn.streamflix.models.Video
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.jsoup.nodes.Document
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Url
import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory

class OnRegardeOuExtractor : Extractor() {

    override val name = "OnRegardeOu"
    override val mainUrl = "https://onregardeou.site"

    private val service = Service.build(mainUrl)

    suspend fun expand(link: String, referer: String = mainUrl, suffix: String = ""): List<Video.Server> {
        val doc = service.get(link, referer)

        val script = doc.select("script")
            .firstOrNull { it.html().contains("videoData") }
            ?.html() ?: return emptyList()

        val jsonRaw = script.substringAfter("const videoData=")
            .substringBefore("};") + "}"

        val json = try {
            JSONObject(jsonRaw)
        } catch (e: Exception) {
            return emptyList()
        }
        val servers = json.getJSONArray("servers")

        val list = mutableListOf<Video.Server>()

        for (i in 0 until servers.length()) {
            val server = servers.getJSONObject(i) ?: continue

            val name = suffix+server.optString("name", "Server$i")
            val url = server.optString("url")

            if (url.isEmpty()) continue

            list.add(
                Video.Server(
                    "${this.name}_$i",
                    name = name,
                    src = url.replace("\\/", "/") // nettoie les URLs
                )
            )
        }

        return list
    }

    override suspend fun extract(link: String): Video {
        throw Exception("Use expand() instead")
    }

    private interface Service {
        @GET
        suspend fun get(
            @Url url: String,
            @Header("Referer") referer: String,
            @Header("User-agent") useragent: String = "Mozilla/5.0"
        ): Document

        companion object {
            fun build(baseUrl: String): Service {
                val client = OkHttpClient.Builder().build()

                return Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .client(client)
                    .addConverterFactory(JsoupConverterFactory.create())
                    .build()
                    .create(Service::class.java)
            }
        }
    }
}