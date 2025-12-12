package com.streamflixreborn.streamflix.extractors

import com.streamflixreborn.streamflix.models.Video
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.GET
import retrofit2.http.Url

class GuploadExtractor : Extractor() {
    override val name = "Gupload"
    override val mainUrl = "https://gupload.xyz"

    private val client = OkHttpClient.Builder().build()

    private val service = Retrofit.Builder()
        .baseUrl(mainUrl)
        .addConverterFactory(ScalarsConverterFactory.create())
        .client(client)
        .build()
        .create(GuploadService::class.java)

    private interface GuploadService {
        @GET
        suspend fun get(@Url url: String): String
    }

    override suspend fun extract(link: String): Video {
        val html = service.get(link)

        val videoUrlRegex = Regex("""const\s+videoUrl\s*=\s*['"]([^'"]+)['"]""")
        val videoUrl = videoUrlRegex.find(html)?.groupValues?.get(1)
            ?: throw Exception("Video URL not found in script")

        return Video(
            source = videoUrl
        )
    }
}

