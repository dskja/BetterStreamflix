package com.streamflixreborn.streamflix.providers

import android.util.Base64
import android.util.Log
import com.streamflixreborn.streamflix.adapters.AppAdapter
import com.streamflixreborn.streamflix.models.*
import com.streamflixreborn.streamflix.models.cablevisionhd.toTvShows
import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import com.streamflixreborn.streamflix.utils.JsUnpacker
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.*
import org.jsoup.nodes.Document
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

object CableVisionHDProvider : Provider {

    override val name = "CableVisionHD"
    override val baseUrl = "https://www.cablevisionhd.com"
    override val logo = "https://i.ibb.co/4gMQkN2b/imagen-2025-09-05-212536248.png"
    override val language = "es"

    private const val TAG = "CableVisionHDProvider"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.0.0 Safari/537.36"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .cookieJar(object : CookieJar {
            private val store = HashMap<String, List<Cookie>>()
            override fun saveFromResponse(u: HttpUrl, c: List<Cookie>) { store[u.host] = c }
            override fun loadForRequest(u: HttpUrl): List<Cookie> = store[u.host] ?: emptyList()
        })
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
        }
        .build()

    private val service = Retrofit.Builder()
        .baseUrl(baseUrl)
        .addConverterFactory(JsoupConverterFactory.create())
        .client(client)
        .build()
        .create(Service::class.java)

    interface Service {
        @GET suspend fun getPage(@Url url: String, @Header("Referer") referer: String = "https://www.cablevisionhd.com"): Document
    }

    override suspend fun getHome(): List<Category> = coroutineScope {
        try {
            val doc = service.getPage(baseUrl); val all = doc.toTvShows(name)
            listOf(
                async { Category(name = "Todos los Canales", list = all) },
                async { Category(name = "Deportes", list = all.filter { it.title.contains("sport", true) || it.title.contains("espn", true) || it.title.contains("fox", true) }) },
                async { Category(name = "Noticias", list = all.filter { it.title.contains("news", true) || it.title.contains("noticias", true) || it.title.contains("cnn", true) }) },
                async { Category(name = "Cine y Series", list = all.filter { listOf("hbo", "max", "cine", "warner", "star").any { s -> it.title.contains(s, true) } }) },
                async { Category(name = "Información", list = listOf(getInfoItem("creador-info"), getInfoItem("apoyo-info"))) }
            ).awaitAll().filter { it.list.isNotEmpty() }
        } catch (e: Exception) { emptyList() }
    }

    override suspend fun search(query: String, page: Int): List<AppAdapter.Item> = try {
        service.getPage(baseUrl).toTvShows(name).filter { it.title.contains(query, true) }
    } catch (_: Exception) { emptyList() }

    override suspend fun getMovies(page: Int): List<Movie> = emptyList()
    override suspend fun getTvShows(page: Int): List<TvShow> = if (page > 1) emptyList() else try { service.getPage(baseUrl).toTvShows(name) } catch (_: Exception) { emptyList() }
    override suspend fun getMovie(id: String): Movie = throw Exception("Not supported")

    override suspend fun getTvShow(id: String): TvShow = if (id == "creador-info" || id == "apoyo-info") getInfoItem(id) else try {
        val doc = service.getPage(if (id.startsWith("http")) id else "$baseUrl/$id")
        val t = doc.selectFirst("div.card-body h2")?.text() ?: doc.selectFirst("h1")?.text() ?: "Canal en Vivo"
        val p = doc.selectFirst("div.card-body img")?.attr("src")?.let { if (!it.startsWith("http")) "$baseUrl/$it" else it }
        TvShow(id = id, title = t, overview = doc.selectFirst("div.card-body p")?.text() ?: "En directo", poster = p, banner = p, seasons = listOf(Season(id, 1, "En Vivo", episodes = listOf(Episode(id, 1, "Directo", p)))), providerName = name)
    } catch (_: Exception) { TvShow(id, "Error", providerName = name) }

    override suspend fun getEpisodesBySeason(seasonId: String): List<Episode> = listOf(Episode(seasonId, 1, "Señal en Directo"))
    override suspend fun getGenre(id: String, page: Int): Genre = throw Exception("Not supported")
    override suspend fun getPeople(id: String, page: Int): People = throw Exception("Not supported")

    override suspend fun getServers(id: String, videoType: Video.Type): List<Video.Server> = try {
        val url = if (id.startsWith("http")) id else "$baseUrl/$id"
        val doc = service.getPage(url)
        val servers = mutableListOf<Video.Server>()

        doc.select("a").forEach { link ->
            val text = link.text().trim()
            val href = link.attr("abs:href").ifEmpty { link.attr("href") }

            if (href.isNotEmpty() && (text.contains("Opción", true) || text.contains("Servidor", true))) {
                val finalUrl = if (href.startsWith("http")) href else "$baseUrl/${href.removePrefix("/")}"
                servers.add(Video.Server(finalUrl, text))
            }
        }

        if (servers.isEmpty() && doc.select("iframe").isNotEmpty()) {
            servers.add(Video.Server(url, "Opción 1"))
        }

        servers.distinctBy { it.id }
    } catch (e: Exception) { emptyList() }

    override suspend fun getVideo(server: Video.Server): Video {
        var currentUrl = server.id
        var currentReferer = baseUrl
        var depth = 0

        val patterns = listOf(
            Regex("""["'](https?://[^"']+\.m3u8[^"']*)["']"""),
            Regex("""source\s*:\s*["']([^"']+)["']"""),
            Regex("""file\s*:\s*["']([^"']+)["']"""),
            Regex("""var\s+src\s*=\s*["']([^"']+)["']"""),
            Regex("""["'](https?://[^"']+\.mp4[^"']*)["']"""),
            Regex("""src\s*:\s*["']([^"']+)["']""")
        )

        while (depth < 6) {
            depth++
            try {
                val doc = service.getPage(currentUrl, currentReferer)
                val html = doc.html()

                for (pattern in patterns) {
                    pattern.find(html)?.let { match ->
                        val foundUrl = match.groupValues[1].replace("\\/", "/")
                        if (foundUrl.startsWith("http")) {
                            return Video(foundUrl, headers = mapOf("Referer" to currentUrl, "User-Agent" to USER_AGENT))
                        }
                    }
                }

                doc.select("script").forEach { script ->
                    val scriptData = script.data()
                    if (scriptData.contains("eval(function")) {
                        val unpacked = JsUnpacker(scriptData).unpack() ?: ""
                        for (pattern in patterns) {
                            pattern.find(unpacked)?.let { match ->
                                val foundUrl = match.groupValues[1].replace("\\/", "/")
                                if (foundUrl.startsWith("http")) {
                                    return Video(foundUrl, headers = mapOf("Referer" to currentUrl, "User-Agent" to USER_AGENT))
                                }
                            }
                        }
                    }
                }

                if (html.contains("const decodedURL") || html.contains("atob(")) {
                    doc.select("script").forEach { s ->
                        val data = s.data()
                        if (data.contains("atob(")) {
                            try {
                                val enc = data.substringAfter("atob(\"").substringBefore("\")")
                                var dec = String(Base64.decode(enc, Base64.DEFAULT))
                                repeat(2) {
                                    if (dec.contains("atob(")) {
                                        val innerEnc = dec.substringAfter("atob(\"").substringBefore("\")")
                                        dec = String(Base64.decode(innerEnc, Base64.DEFAULT))
                                    } else if (!dec.startsWith("http")) {
                                        try { dec = String(Base64.decode(dec, Base64.DEFAULT)) } catch (_: Exception) {}
                                    }
                                }
                                if (dec.startsWith("http")) {
                                    return Video(dec, headers = mapOf("Referer" to currentUrl, "User-Agent" to USER_AGENT))
                                }
                            } catch (_: Exception) {}
                        }
                    }
                }

                val iframes = doc.select("iframe")
                val nextIframe = iframes.firstOrNull { it.attr("src").isNotEmpty() }?.attr("src")
                    ?: iframes.firstOrNull { it.attr("data-src").isNotEmpty() }?.attr("data-src") ?: ""

                if (nextIframe.isNotEmpty() && nextIframe != currentUrl) {
                    currentReferer = currentUrl
                    currentUrl = if (nextIframe.startsWith("http")) nextIframe else "$baseUrl/${nextIframe.removePrefix("/")}"
                } else {
                    break
                }

            } catch (e: Exception) {
                Log.e(TAG, "Fallo en nivel $depth: ${e.message}")
                break
            }
        }
        return Video("", emptyList())
    }

    private fun getInfoItem(id: String): TvShow {
        val t = if(id == "creador-info") "Reportar problemas" else "Apoya al Proveedor"
        val p = if(id == "creador-info") "https://i.ibb.co/dsknGBHT/Imagen-de-Whats-App-2025-09-06-a-las-19-00-50-e8e5bcaa.jpg" else "https://i.ibb.co/B5gKLkqS/nuevo-formato-2-K-202604112205.jpg"
        return TvShow(id, t, poster = p, banner = p, overview = if(id == "creador-info") "@NandoGT" else "Apoya el proyecto.", providerName = name)
    }
}