package com.streamflixreborn.streamflix.extractors

import com.streamflixreborn.streamflix.models.Video
import com.streamflixreborn.streamflix.utils.JsUnpacker
import com.streamflixreborn.streamflix.utils.UserPreferences
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.GET
import retrofit2.http.Url

class FsvidExtractor : Extractor() {
    override val name = "FSVid"
    override val mainUrl = "https://fsvid.lol"

    companion object {
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36"
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val newRequest = chain.request()
                .newBuilder()
                .header("User-Agent", DEFAULT_USER_AGENT)
                .apply {
                    UserPreferences.currentProvider?.baseUrl?.let { header("Referer", it) }
                }
                .build()
            chain.proceed(newRequest)
        }
        .build()

    private val service = Retrofit.Builder()
        .baseUrl(mainUrl)
        .addConverterFactory(ScalarsConverterFactory.create())
        .client(client)
        .build()
        .create(FsvidService::class.java)

    private interface FsvidService {
        @GET
        suspend fun get(@Url url: String): String
    }

    override suspend fun extract(link: String): Video {
        val html = service.get(link)

        val scriptData = html
            .substringAfter("eval(function(p,a,c,k,e,d)")
            .substringBefore("</script>")
            .let { "eval(function(p,a,c,k,e,d)$it" }

        if (!scriptData.startsWith("eval")) throw Exception("Packed JS not found")

        val unpacked = JsUnpacker(scriptData).unpack() ?: throw Exception("Unpack failed")

        val fileRegex = Regex("""file\s*:\s*["']([^"']+)["']""")
        val m3u8 = fileRegex.find(unpacked)?.groupValues?.get(1)
            ?: throw Exception("Stream URL not found in file field")

        return Video(
            source = m3u8
        )
    }
}

