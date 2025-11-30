package com.streamflixreborn.streamflix.extractors

import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import com.streamflixreborn.streamflix.models.Video
import com.streamflixreborn.streamflix.utils.JsUnpacker
import org.jsoup.nodes.Document
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Url
import com.streamflixreborn.streamflix.utils.DnsResolver
import com.streamflixreborn.streamflix.utils.UserPreferences
import okhttp3.OkHttpClient

class VidzyExtractor : Extractor() {

    override val name = "Vidzy"
    override val mainUrl = "https://vidzy.org"

    override suspend fun extract(link: String): Video {
        val service = Service.build(mainUrl)

        val document = service.get(link)

        val packedJS = Regex("""(\}\s*\('.*?'\.split\('\|'\))""")
            .find(document.html().substringAfter("function(p,a,c,k,e,d){"))
            ?.groupValues?.get(1)
            ?: throw Exception("Packed JS not found")

        val unPacked = JsUnpacker(packedJS).unpack()
            ?: throw Exception("Unpacked is null")

        val fileMatch = Regex("""src\s*:\s*["']([^"']+)["']""").find(unPacked)
        val streamUrl = fileMatch?.groupValues?.get(1)
            ?: throw Exception("No src found")

        return Video(
            source = streamUrl ?: throw Exception("Can't retrieve source")
        )
    }

    private interface Service {
        companion object {
            private const val DEFAULT_USER_AGENT =
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"

            val client = OkHttpClient.Builder()
                .dns(DnsResolver.doh)
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("User-Agent", DEFAULT_USER_AGENT)
                        .apply {
                            UserPreferences.currentProvider?.baseUrl?.let { header("Referer", it) }
                        }
                        .build()
                    chain.proceed(request)
                }
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
