package com.dskja.betterstreamflix.extractors

import com.dskja.betterstreamflix.models.Video
import com.dskja.betterstreamflix.utils.DnsResolver
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query
import retrofit2.http.Url
import java.util.concurrent.TimeUnit
import kotlin.text.replaceFirstChar
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import android.util.Log
import com.dskja.betterstreamflix.providers.FrembedProvider
import com.dskja.betterstreamflix.utils.UserPreferences

class FrembedExtractor (var newUrl: String = "") : Extractor() {

    override val name = "Frembed"
    val defaultUrl = "https://frembed.surf"
    override var mainUrl = newUrl.ifBlank { defaultUrl }

    data class StreamLinkItem(
        val id: Int? = null,
        val lang: String? = null,
        val position: Int? = null,
        val label: String? = null,
        val quality: String? = null,
        val url: String? = null,
        val host: HostInfo? = null,
    ) {
        data class HostInfo(
            val slug: String? = null,
            val name: String? = null,
            val icon: String? = null,
        )
    }

    data class listLinks (
        val link1: String?=null,
        val link2: String?=null,
        val link3: String?=null,
        val link4: String?=null,
        val link5: String?=null,
        val link6: String?=null,
        val link7: String?=null,
        val link1vostfr: String?=null,
        val link2vostfr: String?=null,
        val link3vostfr: String?=null,
        val link4vostfr: String?=null,
        val link5vostfr: String?=null,
        val link6vostfr: String?=null,
        val link7vostfr: String?=null,
        val link1vo: String?=null,
        val link2vo: String?=null,
        val link3vo: String?=null,
        val link4vo: String?=null,
        val link5vo: String?=null,
        val link6vo: String?=null,
        val link7vo: String?=null,
        // Newer frembed.surf series payload also exposes a singular "link" field.
        val link: String?=null,
        val link_vostfr: String?=null,
        val links: List<StreamLinkItem>? = null,
    )

    private fun getExtractorName(url: String): String {
        return url.substringAfter("://")
            .substringBefore("/")
            .substringBefore(".")
            .replace("crystaltreatmenteast", "voe")
            .replace("lauradaydo", "voe")
            .replace("lancewhosedifficult", "voe")
            .replace("dianaavoidthey", "voe")
            .replace("jefferycontrolmodel", "voe")
            .replace("richardquestionbuilding", "voe")
            .replace("juliewomanwish", "voe")
            .replace("myvidplay", "dood")
            .replace("playmogo", "dood")
            .replaceFirstChar { it.uppercase() }
    }

    fun listLinks.toServers(): List<Video.Server> {
        val fromLinksArray = links.orEmpty().mapIndexedNotNull { index, item ->
            val data = item.url?.takeIf { it.isNotBlank() } ?: return@mapIndexedNotNull null
            val lang = when (item.lang?.lowercase()) {
                "vf", "truefrench", "french" -> "French"
                "vostfr" -> "VOSTFR"
                "vo", "en", "vost" -> "VO"
                else -> item.lang?.uppercase() ?: "French"
            }
            val hostName = item.host?.name
                ?: item.label
                ?: getExtractorName(if (data.startsWith("/")) mainUrl.removeSuffix("/") + data else data)
            (if (data.startsWith("/")) mainUrl.removeSuffix("/") + data else data).let {
                Video.Server(
                    id = "links$index",
                    name = "$hostName ($lang)",
                    src = it,
                )
            }
        }
        if (fromLinksArray.isNotEmpty()) return fromLinksArray.distinctBy { it.src }

        return listOf(
            link1, link2, link3, link4, link5, link6, link7,
            link1vostfr, link2vostfr, link3vostfr, link4vostfr, link5vostfr, link6vostfr, link7vostfr,
            link1vo, link2vo, link3vo, link4vo, link5vo, link6vo, link7vo,
            link, link_vostfr,
        )
            .mapIndexedNotNull { index, data ->
                if (data.isNullOrEmpty()) return@mapIndexedNotNull null
                val lang = when {
                    index < 7 -> "French"
                    index < 14 -> "VOSTFR"
                    index < 21 -> "VO"
                    index == 21 -> "French"
                    else -> "VOSTFR"
                }
                (if (data.startsWith("/")) mainUrl.removeSuffix("/") + data else data).let {
                    Video.Server(id = "link$index", name = "${getExtractorName(it)} ($lang)", src = it)
                }
            }
            .distinctBy { it.src }
    }

    private interface Service {
        companion object {
            private const val USER_AGENT =
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

            fun build(baseUrl: String): Service {
                val normalizedBase = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
                val clientBuilder = OkHttpClient.Builder()
                    .readTimeout(30, TimeUnit.SECONDS)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .addNetworkInterceptor { chain ->
                        val request = chain.request()
                        val url = request.url
                        val referer = "${url.scheme}://${url.host}/"
                        val newRequest = request.newBuilder()
                            .header("User-Agent", USER_AGENT)
                            .header("Accept", "application/json,text/plain,*/*")
                            .header("Accept-Language", "fr-FR,fr;q=0.9,en-US;q=0.8,en;q=0.7")
                            .header("Origin", referer.trimEnd('/'))
                            .header("Referer", referer)
                            .build()
                        chain.proceed(newRequest)
                    }

                return Retrofit.Builder()
                    .baseUrl(normalizedBase)
                    .addConverterFactory( GsonConverterFactory.create())
                    .client(clientBuilder.dns(DnsResolver.doh).build())
                    .build()
                    .create(Service::class.java)
            }
        }

        @GET("api/films")
        suspend fun getMovieLinks(
            @Query("id") id: String,
            @Query("idType") idType: String = "tmdb",
            @Header("User-Agent") userAgent: String = USER_AGENT,
            @Header("Content-Type") contentType: String = "application/json"
        ): listLinks

        @GET("api/series")
        suspend fun getTvShowLinks(
            @Query("id") id: String,
            @Query("sa") sa: Int,
            @Query("epi") epi: Int,
            @Query("idType") idType: String = "tmdb",
            @Header("User-Agent") userAgent: String = USER_AGENT,
            @Header("Content-Type") contentType: String = "application/json"
        ): listLinks

        @GET
        suspend fun getStreamLinks(
            @Url url: String,
            @Header("User-Agent") userAgent: String = USER_AGENT
        ): Response<listLinks>
    }

    private val service = Service.build(mainUrl)

    override suspend fun extract(link: String): Video {
        throw Exception("None")
    }

    suspend fun servers(videoType: Video.Type): List<Video.Server> {
        Log.d("FrembedExtractor", "Fetching servers for videoType: $videoType using mainUrl: $mainUrl")
        return try {
            val ret = when (videoType) {
                is Video.Type.Movie -> {
                    val primary = runCatching { service.getMovieLinks(videoType.id, "tmdb") }.getOrNull()
                    if (primary != null && primary.toServers().isNotEmpty()) {
                        primary
                    } else {
                        val imdb = videoType.imdbId?.takeIf { it.isNotBlank() }?.let { id ->
                            if (id.startsWith("tt")) id else "tt$id"
                        }
                        if (imdb != null) {
                            service.getMovieLinks(imdb, "imdb")
                        } else {
                            primary ?: service.getMovieLinks(videoType.id, "tmdb")
                        }
                    }
                }
                is Video.Type.Episode -> service.getTvShowLinks(
                    videoType.tvShow.id,
                    videoType.season.number,
                    videoType.number,
                )
            }
            val initialServers = ret.toServers()
            Log.d("FrembedExtractor", "Initial servers found: ${initialServers.size}")
            if (initialServers.isEmpty()) {
                throw Exception("Frembed returned no stream links for this title")
            }

            val resolved = coroutineScope {
                initialServers.map { server ->
                    async(Dispatchers.IO) {
                        try {
                            Log.d("FrembedExtractor", "Resolving redirect for server: ${server.name} - src: ${server.src}")
                            val response = service.getStreamLinks(server.src)
                            val redirect = response.headers()["Location"]
                                ?: response.raw().header("Location")
                            if (!redirect.isNullOrEmpty()) {
                                val fullRedirect = when {
                                    redirect.startsWith("//") -> "https:$redirect"
                                    redirect.startsWith("/") -> mainUrl.trimEnd('/') + redirect
                                    else -> redirect
                                }
                                val lang = server.name.substringAfter(" (", "").substringBefore(")")
                                    .ifBlank { "French" }
                                val resolvedName = "${getExtractorName(fullRedirect)} ($lang)"
                                Log.d("FrembedExtractor", "Resolved server ${server.name} to: $resolvedName - redirect: $fullRedirect")
                                server.copy(
                                    name = resolvedName,
                                    src = fullRedirect
                                )
                            } else {
                                Log.d("FrembedExtractor", "No redirect for server: ${server.name}")
                                server
                            }
                        } catch (e: Exception) {
                            Log.w("FrembedExtractor", "Failed to resolve redirect for server ${server.name}: ${e.message}")
                            server
                        }
                    }
                }.awaitAll()
            }
            resolved.filter { it.src.isNotBlank() && !it.src.contains("/api/stream", ignoreCase = true) }
                .ifEmpty { initialServers }
        } catch (e: Exception) {
            if (e is retrofit2.HttpException) {
                val redirect = e.response()?.headers()?.get("Location")
                if (!redirect.isNullOrEmpty()) {
                    val fullRedirect = if (redirect.startsWith("//")) "https:$redirect" else redirect
                    if (fullRedirect.startsWith("http")) {
                        try {
                            val uri = java.net.URI(fullRedirect)
                            val newBaseUrl = "${uri.scheme}://${uri.host}/"
                            if (uri.host?.contains("frembed", ignoreCase = true) == true) {
                                Log.i("FrembedExtractor", "API redirected to a new domain. Updating cache to: $newBaseUrl")
                                UserPreferences.setProviderCache(FrembedProvider, UserPreferences.PROVIDER_URL, newBaseUrl)
                                UserPreferences.setProviderCache(FrembedProvider, UserPreferences.PROVIDER_LOGO, newBaseUrl + "favicon-32x32.png")
                                FrembedProvider.rebuildService()
                                return FrembedExtractor(newBaseUrl).servers(videoType)
                            } else {
                                Log.w("FrembedExtractor", "Ignoring non-Frembed redirect host: ${uri.host}")
                            }
                        } catch (ex: Exception) {
                            Log.e("FrembedExtractor", "Failed to parse URI from redirect Location: $fullRedirect", ex)
                        }
                    }
                }
            }
            Log.e("FrembedExtractor", "Error fetching servers: ${e.message}", e)
            throw e
        }
    }

    suspend fun server(videoType: Video.Type): Video.Server {
        return this.servers(videoType).first()
    }
}