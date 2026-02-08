package com.streamflixreborn.streamflix.utils

import android.net.Uri
import androidx.core.net.toUri
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.zip.ZipInputStream
import com.streamflixreborn.streamflix.BuildConfig

object SubDL {

    private const val URL = "https://api.subdl.com/api/v1/"
    private const val DOWNLOAD_BASE_URL = "https://dl.subdl.com"

    private val service = Service.build()

    suspend fun download(
        subtitle: Subtitle,
    ): Uri = withContext(Dispatchers.IO) {
        val downloadUrl = "$DOWNLOAD_BASE_URL${subtitle.url}"
        
        val zip = File.createTempFile(
            "subdl-${subtitle.releaseName ?: "subtitle"}-",
            ".zip"
        )

        URL(downloadUrl).openStream().use { input ->
            FileOutputStream(zip).use { output -> input.copyTo(output) }
        }

        var subtitleFile: File? = null

        ZipInputStream(zip.inputStream()).use { zipInputStream ->
            var entry = zipInputStream.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val name = File(entry.name).name
                    val file = File("${zip.parent}${File.separator}$name")
                    
                    if (file.exists()) {
                        file.delete()
                    }

                    FileOutputStream(file).use { fileOutputStream ->
                        zipInputStream.copyTo(fileOutputStream)
                    }
                    subtitleFile = file
                    break
                }
                entry = zipInputStream.nextEntry
            }
        }

        zip.delete()

        subtitleFile?.toUri() ?: throw Exception("No subtitle found in zip")
    }

    suspend fun search(
        filmName: String? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
        type: String? = null,
        subsPerPage: Int = 30,
    ): List<Subtitle> {
        
        // If no API key is configured, do not search
        if (UserPreferences.subdlApiKey.isEmpty()) {
            return emptyList()
        }
        
        return try {
            val response = service.search(
                apiKey = UserPreferences.subdlApiKey,
                filmName = filmName,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                type = type,
                subsPerPage = subsPerPage
            )
            val subtitles = response.subtitles ?: emptyList()
            subtitles
        } catch (e: Exception) {
            emptyList()
        }
    }

    private interface Service {

        companion object {
            fun build(): Service {
                val client = OkHttpClient.Builder()
                    .addInterceptor { chain ->
                        val requestBuilder = chain.request().newBuilder()
                            .addHeader("Accept", "application/json")

                        chain.proceed(requestBuilder.build())
                    }
                    .build()

                val retrofit = Retrofit.Builder()
                    .baseUrl(URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()

                return retrofit.create(Service::class.java)
            }
        }

        @GET("subtitles")
        suspend fun search(
            @Query("api_key") apiKey: String,
            @Query("film_name") filmName: String? = null,
            @Query("season_number") seasonNumber: Int? = null,
            @Query("episode_number") episodeNumber: Int? = null,
            @Query("type") type: String? = null,
            @Query("subs_per_page") subsPerPage: Int? = null,
        ): SearchResponse
    }

    data class SearchResponse(
        @SerializedName("status") val status: Boolean = false,
        @SerializedName("subtitles") val subtitles: List<Subtitle>? = null,
        @SerializedName("error") val error: String? = null
    )

    data class Subtitle(
        @SerializedName("release_name") val releaseName: String? = null,
        @SerializedName("name") val name: String? = null,
        @SerializedName("lang") val lang: String? = null,
        @SerializedName("language") val language: String? = null,
        @SerializedName("url") val url: String? = null,
    )
}
