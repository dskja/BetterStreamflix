package com.dskja.betterstreamflix.extractors

import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import com.dskja.betterstreamflix.models.Video
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.nodes.Document
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Url

class OkruExtractor : Extractor() {

    override val name = "Okru"
    override val mainUrl = "https://ok.ru"
    override val aliasUrls = listOf(
        "https://www.ok.ru",
        "https://m.ok.ru",
        "https://ok.ru/videoembed",
        "https://www.ok.ru/videoembed",
    )

    private val service = Service.build(mainUrl)

    override suspend fun extract(link: String): Video {
        val document = service.get(link)

        val videoString = document.selectFirst("div[data-options], [data-options]")
            ?.attr("data-options")
            ?.takeIf { it.isNotBlank() }
            ?: throw Exception("No se encontró 'data-options' en la página de Ok.ru")

        val headers = mapOf(
            "Referer" to mainUrl,
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        )

        parseModernMetadata(videoString)?.let { return Video(source = it, headers = headers) }
        parseLegacyEscapedVideos(videoString)?.let { return Video(source = it, headers = headers) }

        throw Exception("No se encontraron videos válidos en el JSON de Ok.ru")
    }

    private fun parseModernMetadata(videoString: String): String? {
        return try {
            val root = JSONObject(videoString)
            val flashvars = root.optJSONObject("flashvars") ?: root
            val metadataRaw = flashvars.opt("metadata")
            val metadata = when (metadataRaw) {
                is JSONObject -> metadataRaw
                is String -> if (metadataRaw.isNotBlank()) JSONObject(metadataRaw) else null
                else -> null
            } ?: return null

            val hls = metadata.optString("hlsManifestUrl").orEmpty()
            if (hls.startsWith("http")) return hls

            val videos = metadata.optJSONArray("videos") ?: return null
            pickBestVideoUrl(videos)
        } catch (_: Exception) {
            null
        }
    }

    private fun pickBestVideoUrl(videos: JSONArray): String? {
        val preferred = listOf("full", "hd", "sd", "low", "lowest", "mobile", "quad", "ultra")
        val byName = mutableMapOf<String, String>()
        for (i in 0 until videos.length()) {
            val item = videos.optJSONObject(i) ?: continue
            val name = item.optString("name")
            val url = item.optString("url").replace("\\u0026", "&")
            if (name.isNotBlank() && url.startsWith("http")) {
                byName[name] = url
            }
        }
        preferred.forEach { key ->
            byName[key]?.let { return it }
        }
        return byName.values.firstOrNull()
    }

    private fun parseLegacyEscapedVideos(videoString: String): String? {
        val arrayData = videoString.substringAfterLast("\\\"videos\\\":[{\\\"name\\\":\\\"", missingDelimiterValue = "")
            .ifBlank {
                videoString.substringAfterLast("\"videos\":[{\"name\":\"", missingDelimiterValue = "")
            }
            .substringBefore("]")
        if (arrayData.isBlank()) return null

        val videos = arrayData.split("{\\\"name\\\":\\\"", "\"name\":\"")
            .reversed()
            .mapNotNull {
                val videoUrl = it.substringAfter("url\\\":\\\"", missingDelimiterValue = "")
                    .ifBlank { it.substringAfter("url\":\"", missingDelimiterValue = "") }
                    .substringBefore("\\\"", missingDelimiterValue = "")
                    .ifBlank { it.substringAfter("url\":\"").substringBefore("\"") }
                    .replace("\\\\u0026", "&")
                    .replace("\\u0026", "&")
                if (videoUrl.startsWith("https://") || videoUrl.startsWith("http://")) videoUrl else null
            }

        return videos.firstOrNull()
    }

    private interface Service {
        companion object {
            fun build(baseUrl: String): Service {
                val client = OkHttpClient.Builder()
                    .followRedirects(true)
                    .addInterceptor { chain ->
                        val request = chain.request().newBuilder()
                            .header(
                                "User-Agent",
                                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
                            )
                            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                            .header("Accept-Language", "es-ES,es;q=0.9,en-US;q=0.8,en;q=0.7")
                            .header("Referer", "https://ok.ru/")
                            .build()
                        chain.proceed(request)
                    }
                    .build()

                val retrofit = Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .addConverterFactory(JsoupConverterFactory.create())
                    .client(client)
                    .build()

                return retrofit.create(Service::class.java)
            }
        }

        @GET
        suspend fun get(@Url url: String): Document
    }
}
