package com.streamflixreborn.streamflix.providers

import android.content.Context
import android.util.Base64
import android.util.Log
import com.streamflixreborn.streamflix.adapters.AppAdapter
import com.streamflixreborn.streamflix.extractors.Extractor
import com.streamflixreborn.streamflix.models.*
import com.streamflixreborn.streamflix.utils.NetworkClient
import com.streamflixreborn.streamflix.utils.WebViewResolver
import com.streamflixreborn.streamflix.StreamFlixApp
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object Cine24hProvider : Provider {

    override val name = "Cine24h"
    override val baseUrl = "https://cine24h.online"
    override val language = "es"
    override val logo = "https://i.ibb.co/kgjcsFmj/Image-1.png"

    private var webViewResolver: WebViewResolver? = null
    private val providerMutex = Mutex()
    private const val TAG = "Cine24hBypass"

    fun init(context: Context) {
        webViewResolver = WebViewResolver(context)
    }

    private suspend fun getDocument(url: String): Document {
        try {
            // Tentativo ultra-veloce (3s) per rilevare se serve la WebView
            val client = NetworkClient.default.newBuilder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(3, TimeUnit.SECONDS)
                .writeTimeout(3, TimeUnit.SECONDS)
                .build()

            val request = Request.Builder()
                .url(url)
                .header("Referer", baseUrl)
                .build()
            
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val html = response.body?.string() ?: ""
                // Se non c'è traccia di Cloudflare, procediamo con OkHttp (veloce)
                if (!html.contains("cf-browser-verification") && !html.contains("Checking your browser") && !html.contains("Just a moment...")) {
                    return Jsoup.parse(html).apply { setBaseUri(baseUrl) }
                }
            }
        } catch (_: Exception) { }

        // Se OkHttp fallisce o rileva blocco, passiamo SUBITO alla WebView
        Log.d(TAG, "[Provider] Launching WebView Bypass for $url")
        val resolver = webViewResolver ?: WebViewResolver(StreamFlixApp.instance)
        val html = resolver.get(url)
        return Jsoup.parse(html).apply { setBaseUri(baseUrl) }
    }

    override suspend fun getHome(): List<Category> = providerMutex.withLock {
        val categories = mutableListOf<Category>()
        coroutineScope {
            try {
                // Caricamento parallelo istantaneo delle sezioni
                val bannerAsync = async { getDocument("$baseUrl/release/2025/") }
                val moviesAsync = async { getDocument("$baseUrl/estrenos/?type=movies") }
                val tvAsync = async { getDocument("$baseUrl/estrenos/?type=series") }

                val bannerDoc = bannerAsync.await()
                val bannerItems = parseShows(bannerDoc)
                val featured = bannerItems.mapNotNull { 
                    if (it is Movie) it.copy(poster = null, banner = it.poster) 
                    else if (it is TvShow) it.copy(poster = null, banner = it.poster) 
                    else null 
                }.take(10)
                if (featured.isNotEmpty()) categories.add(Category(Category.FEATURED, featured))

                val movies = parseShows(moviesAsync.await()).filterIsInstance<Movie>()
                if (movies.isNotEmpty()) categories.add(Category("Estrenos de Películas", movies)) 
                
                val tvShows = parseShows(tvAsync.await()).filterIsInstance<TvShow>()
                if (tvShows.isNotEmpty()) categories.add(Category("Estrenos de Series", tvShows)) 
                
            } catch (e: Exception) {
                Log.e(TAG, "[Provider] Error loading home", e)
            }
        }
        return@withLock categories
    }

    override suspend fun search(query: String, page: Int): List<AppAdapter.Item> {
        if (query.isBlank()) return listOf("accion", "animacion", "anime", "aventura", "belica", "ciencia-ficcion", "comedia", "crimen", "documental", "drama", "familia", "fantasia", "historia", "misterio", "musica", "romance", "suspense", "terror", "western").map { Genre(id = "category/$it/", name = it.replace("-", " ").replaceFirstChar { c -> c.uppercase() }) }
        return try { parseShows(getDocument("$baseUrl/?s=${URLEncoder.encode(query, "UTF-8")}&paged=$page")) } catch (_: Exception) { emptyList() }
    }

    private fun parseShows(doc: Document): List<AppAdapter.Item> {
        val items = doc.select("article.TPost, li.TPostMv article, .TPost, .poster, .grid-item, .item, article[class*='post-']")
        return items.mapNotNull { el ->
            val anchor = el.selectFirst("a") ?: return@mapNotNull null
            val url = anchor.attr("href")
            var titleText = anchor.selectFirst("h2, h3, .Title, .text-md, .name, .poster__title")?.text()?.trim()
            if (titleText.isNullOrEmpty()) titleText = el.selectFirst("h2, h3, .Title, .name")?.text()?.trim()
            val finalTitle = titleText ?: return@mapNotNull null
            var processedTitle = finalTitle
            anchor.selectFirst(".language-box .lang-item span")?.text()?.trim()?.let { if (it.isNotEmpty()) processedTitle += " [$it]" }
            val img = anchor.selectFirst("img") ?: el.selectFirst("img")
            val poster = img?.let { it.attr("abs:src").ifEmpty { it.attr("abs:data-src") }.ifEmpty { it.attr("src") } }?.replace("/w185/", "/w300/")?.replace("/w92/", "/w300/") ?: ""
            if (url.contains("/peliculas/") || url.contains("/movies/")) {
                Movie(id = url.substringAfter("/peliculas/").substringAfter("/movies/").removeSuffix("/"), title = processedTitle, poster = poster)
            } else if (url.contains("/series/")) {
                TvShow(id = url.substringAfter("/series/").removeSuffix("/"), title = processedTitle, poster = poster)
            } else null
        }.distinctBy { if (it is Movie) it.id else if (it is TvShow) it.id else "" }
    }

    override suspend fun getMovies(page: Int): List<Movie> = try { parseShows(getDocument("$baseUrl/peliculas/page/$page")).filterIsInstance<Movie>() } catch (_: Exception) { emptyList() }
    override suspend fun getTvShows(page: Int): List<TvShow> = try { parseShows(getDocument("$baseUrl/series/page/$page")).filterIsInstance<TvShow>() } catch (_: Exception) { emptyList() }
    
    override suspend fun getGenre(id: String, page: Int): Genre = try { 
        val shows = parseShows(getDocument("$baseUrl/${id}page/$page")).filterIsInstance<Show>()
        Genre(id = id, name = id.removePrefix("category/").removeSuffix("/").replaceFirstChar { it.uppercase() }, shows = shows) 
    } catch (_: Exception) { Genre(id = id, name = "Error") }

    override suspend fun getMovie(id: String): Movie = getDocument("$baseUrl/peliculas/$id").let { doc ->
        val info = doc.selectFirst(".TPost footer .Info, .Info")
        Movie(id = id, title = doc.selectFirst(".TPost header .Title, h1")?.text() ?: "", overview = doc.selectFirst(".TPost .Description, .Description, .page__text")?.text(), poster = doc.selectFirst(".TPost .Image img, .pmovie__poster img")?.attr("abs:src")?.replace("/w185/", "/w500/"), rating = info?.selectFirst(".Rank")?.text()?.toDoubleOrNull(), released = info?.selectFirst(".Date")?.text(),
            runtime = info?.selectFirst(".Time")?.text()?.replace("h", "")?.replace("m", "")?.trim()?.split(" ")?.let { (it.getOrNull(0)?.toIntOrNull() ?: 0) * 60 + (it.getOrNull(1)?.toIntOrNull() ?: 0) },
            genres = doc.select(".TPost .Description .Genre a, a[href*='/category/']").map { Genre(id = it.attr("href"), name = it.text()) })
    }

    override suspend fun getTvShow(id: String): TvShow = getDocument("$baseUrl/series/$id").let { doc ->
        val info = doc.selectFirst(".TPost footer .Info, .Info")
        val seasons = doc.select(".AABox").mapNotNull { el -> 
            el.selectFirst(".Title")?.text()?.let { t -> 
                Regex("""\d+$""").find(t)?.value?.toIntOrNull()?.let { n -> Season(id = "$id/$n", number = n, title = t) } 
            } 
        }.sortedByDescending { it.number }
        TvShow(id = id, title = doc.selectFirst(".TPost header .Title, h1")?.text() ?: "", overview = doc.selectFirst(".TPost .Description, .Description, .page__text")?.text(), poster = doc.selectFirst(".TPost .Image img, .pmovie__poster img")?.attr("abs:src")?.replace("/w185/", "/w500/"), rating = info?.selectFirst(".Rank")?.text()?.toDoubleOrNull(), released = info?.selectFirst(".Date")?.text(),
            genres = doc.select(".TPost .Description .Genre a, a[href*='/category/']").map { Genre(id = it.attr("href"), name = it.text()) },
            seasons = seasons)
    }

    override suspend fun getEpisodesBySeason(seasonId: String): List<Episode> = try {
        val (showId, sNum) = seasonId.split("/"); val doc = getDocument("$baseUrl/series/$showId")
        doc.select(".AABox").find { (it.selectFirst(".Title")?.text() ?: "").trim().endsWith(sNum) }?.select(".TPTblCn tr, .TPTblCn li")?.mapNotNull { row ->
            val a = row.selectFirst(".MvTbTtl a, a") ?: return@mapNotNull null
            Episode(id = a.attr("abs:href"), number = row.selectFirst(".Num")?.text()?.toIntOrNull() ?: 0, title = a.text().trim(), poster = row.selectFirst(".MvTbImg img, img")?.attr("abs:src")?.replace("/w154/", "/w300/"), released = row.selectFirst(".MvTbTtl span")?.text())
        }?.sortedBy { it.number } ?: emptyList()
    } catch (_: Exception) { emptyList() }

    override suspend fun getServers(id: String, videoType: Video.Type): List<Video.Server> = try {
        val fullUrl = if (id.startsWith("http")) id else if (videoType is Video.Type.Movie) "$baseUrl/peliculas/$id" else "$baseUrl/series/$id"
        val doc = getDocument(fullUrl)
        val serverElements = doc.select("ul.optnslst li[data-src], .optnslst li")
        
        coroutineScope {
            serverElements.map { el ->
                async {
                    val info = el.selectFirst("button")?.text()?.replace(el.selectFirst(".nmopt")?.text() ?: "", "")?.trim() ?: ""
                    val dataSrc = el.attr("data-src")
                    val decoded = if (dataSrc.isNotEmpty()) {
                        try { String(Base64.decode(dataSrc, Base64.DEFAULT)) } catch(_:Exception) { "" }
                    } else ""
                    if (decoded.isBlank()) return@async null
                    
                    try {
                        val finalUrl = getIframeOptimized(decoded) ?: return@async null
                        Video.Server(
                            id = finalUrl, 
                            name = "${finalUrl.toHttpUrl().host.replace("www.", "").substringBefore(".")} ($info)", 
                            src = finalUrl
                        )
                    } catch (_: Exception) { null }
                }
            }.mapNotNull { it.await() }
        }
    } catch (_: Exception) { emptyList() }

    private suspend fun getIframeOptimized(url: String): String? {
        try {
            val response = NetworkClient.default.newCall(Request.Builder().url(url).build()).execute()
            val body = response.body?.string() ?: ""
            if (body.contains("iframe")) {
                val iframeUrl = Jsoup.parse(body).selectFirst("iframe")?.attr("abs:src")
                if (!iframeUrl.isNullOrEmpty()) {
                    return iframeUrl
                }
            }
        } catch (_: Exception) { }
        return getDocument(url).selectFirst("iframe")?.attr("abs:src")
    }

    override suspend fun getVideo(server: Video.Server): Video = Extractor.extract(server.src, server)
    override suspend fun getPeople(id: String, page: Int): People = throw Exception("Not yet implemented")
}
