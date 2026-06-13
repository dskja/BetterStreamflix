package com.streamflixreborn.streamflix.providers

import android.util.Base64
import android.util.Log
import com.streamflixreborn.streamflix.adapters.AppAdapter
import com.streamflixreborn.streamflix.models.*
import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import com.streamflixreborn.streamflix.utils.JsUnpacker
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

object TvporinternetHDProvider : IptvProvider {

    override val name = "TvporinternetHD"
    override val baseUrl = "https://www.tvporinternet2.com"
    override val logo = "https://i.ibb.co/xtCZS8fR/tvporinternet.png"
    override val language = "es"

    private const val TAG = "TvporinternetHD"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.0.0 Safari/537.36"

    // --- SISTEMA DE CACHÉ EN MEMORIA ---
    private var channelsCache: List<TvShow> = emptyList()
    private var lastFetchTime: Long = 0
    private const val CACHE_VALIDITY = 2 * 60 * 60 * 1000L // 2 horas de caché

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
        @GET
        suspend fun getPage(
            @Url url: String,
            @Header("Referer") referer: String = "https://www.tvporinternet2.com/"
        ): Document
    }

    // --- FUNCIÓN DE CACHÉ INTERNA ---
    private suspend fun getCachedOrFetchChannels(): List<TvShow> {
        val currentTime = System.currentTimeMillis()
        if (channelsCache.isNotEmpty() && (currentTime - lastFetchTime) < CACHE_VALIDITY) {
            Log.d(TAG, "⚡ Usando lista de canales desde la memoria caché (${channelsCache.size} canales)")
            return channelsCache
        }

        Log.d(TAG, "🌐 Obteniendo canales desde la web (Caché vacía o expirada)")
        return try {
            val doc = service.getPage(baseUrl)
            val parsed = parseChannels(doc)
            if (parsed.isNotEmpty()) {
                channelsCache = parsed
                lastFetchTime = currentTime
            }
            parsed
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo canales web: ${e.message}")
            channelsCache // Retorna la caché antigua si la petición falla
        }
    }

    // --- BÚSQUEDA DEL TESORO: PARSER DE CARGA DINÁMICA MEJORADO ---
    private fun parseChannels(doc: Document): List<TvShow> {
        val results = mutableListOf<TvShow>()
        val forbidden = listOf("paypal", "donar", "pago", "qr", "cafecito", "pay.png", "donate")

        // MÉTODO UNIVERSAL RESILIENTE (Ignora Scripts, escanea el DOM directo)
        doc.select("a").forEach { a ->
            val link = a.attr("abs:href").ifEmpty { a.attr("href") }
            val imgElement = a.selectFirst("img")

            if (imgElement != null) {
                // LA CLAVE: Buscar atributos de Lazy Load
                val rawImg = imgElement.attr("data-src")
                    .ifEmpty { imgElement.attr("data-lazy-src") }
                    .ifEmpty { imgElement.attr("abs:src") }
                    .ifEmpty { imgElement.attr("src") }

                val title = a.attr("title")
                    .ifEmpty { imgElement.attr("alt") }
                    .ifEmpty { a.text() }
                    .trim()

                // Filtrar imágenes basura
                val isForbiddenImg = forbidden.any { it in rawImg.lowercase() }

                if (!isForbiddenImg && isValidChannel(link, title)) {
                    val finalUrl = if (link.startsWith("http")) link else "$baseUrl/${link.removePrefix("/")}"
                    val finalImg = if (rawImg.startsWith("http")) rawImg else "$baseUrl/${rawImg.removePrefix("/")}"

                    results.add(TvShow(id = finalUrl, title = title, poster = finalImg, banner = finalImg, providerName = name))
                }
            }
        }

        return results.distinctBy { it.id }.also {
            Log.d(TAG, "✅ Canales rescatados: ${it.size}")
        }
    }

    private fun isValidChannel(link: String, title: String): Boolean {
        val cleanLink = link.trim().removeSuffix("/")
        val cleanBase = baseUrl.removeSuffix("/")

        return link.isNotEmpty() &&
                title.isNotEmpty() &&
                (link.startsWith(baseUrl) || !link.startsWith("http")) &&
                cleanLink != cleanBase &&
                !link.contains("linktre.online") &&
                !link.contains("paypal.com") &&
                !link.contains("/category/") &&
                !link.contains("/tag/") &&
                !title.contains("Telegram", ignoreCase = true) &&
                !title.contains("Soporte", ignoreCase = true)
    }

    override suspend fun getHome(): List<Category> = coroutineScope {
        try {
            // Obtenemos de la caché instantánea
            val all = getCachedOrFetchChannels()
            val categories = mutableListOf<Category>()

            if (all.isNotEmpty()) {
                val contentCategories = listOf(
                    async { Category(name = "Todos los Canales", list = all) },
                    async { Category(name = "Deportes", list = all.filter { it.title.contains("sport", true) || it.title.contains("espn", true) || it.title.contains("fox", true) || it.title.contains("tyc", true) || it.title.contains("direct", true) }) },
                    async { Category(name = "Noticias", list = all.filter { it.title.contains("news", true) || it.title.contains("noticia", true) || it.title.contains("cnn", true) || it.title.contains("24h", true) }) },
                    async { Category(name = "Cine y Series", list = all.filter { listOf("hbo", "max", "cine", "warner", "star", "tnt", "film", "movie").any { s -> it.title.contains(s, true) } }) }
                ).awaitAll().filter { it.list.isNotEmpty() }

                categories.addAll(contentCategories)
            }

            categories.add(
                Category(
                    name = "Soporte y Ayuda",
                    list = listOf(getInfoItem("creador-info"), getInfoItem("apoyo-info"))
                )
            )

            categories
        } catch (e: Exception) {
            Log.e(TAG, "❌ ERROR CRÍTICO: ${e.message}")
            listOf(Category(name = "Soporte y Ayuda", list = listOf(getInfoItem("creador-info"), getInfoItem("apoyo-info"))))
        }
    }

    override suspend fun search(query: String, page: Int): List<AppAdapter.Item> = try {
        val allChannels = getTvShows(1)
        allChannels.filter { it.title.contains(query, ignoreCase = true) }
    } catch (_: Exception) { emptyList() }

    override suspend fun getMovies(page: Int): List<Movie> = emptyList()

    override suspend fun getTvShows(page: Int): List<TvShow> = try {
        // También usamos caché aquí para la pestaña de TV
        getCachedOrFetchChannels()
    } catch (_: Exception) { emptyList() }

    override suspend fun getMovie(id: String): Movie = throw Exception("Not supported")

    override suspend fun getTvShow(id: String): TvShow = if (id == "creador-info" || id == "apoyo-info") getInfoItem(id) else try {
        val doc = service.getPage(id)

        val t = doc.selectFirst("h1, h2, .title, .entry-title")?.text() ?: "Canal en Vivo"

        // --- FILTRADO DE ÉLITE EN DETALLES (RECUPERA LOGO REAL) ---
        val forbidden = listOf(
            "paypal", "pago", "donar", "pay.png", "qr", "cafecito", "mercado", "donate",
            "buy", "telegram", "whatsapp", "facebook", "twitter", "instagram",
            "share", "ads", "banner", "pixel", "button", "btn", "favicon"
        )

        var imgElement = doc.select("img.wp-post-image, img.attachment-post-thumbnail, .entry-content img, .post-content img").firstOrNull { img ->
            val src = img.attr("data-src").ifEmpty { img.attr("src") }.lowercase()
            forbidden.none { it in src } && src.isNotEmpty()
        }

        val rawImg = imgElement?.let { it.attr("data-src").ifEmpty { it.attr("abs:src") }.ifEmpty { it.attr("src") } } ?: ""
        val p = if (rawImg.startsWith("http")) rawImg else if (rawImg.isNotEmpty()) "$baseUrl/${rawImg.removePrefix("/")}" else ""

        TvShow(
            id = id,
            title = t,
            overview = doc.selectFirst(".entry-content p, .card-body p, p")?.text() ?: "Canal de TV por Internet en vivo.",
            poster = p,
            banner = p,
            seasons = listOf(Season(id, 1, "En Vivo", episodes = listOf(Episode(id, 1, "Directo", p)))),
            providerName = name
        )
    } catch (_: Exception) { TvShow(id, "Error al cargar señal", providerName = name) }

    override suspend fun getEpisodesBySeason(seasonId: String): List<Episode> = listOf(Episode(seasonId, 1, "Señal en Directo"))
    override suspend fun getGenre(id: String, page: Int): Genre = throw Exception("Not supported")
    override suspend fun getPeople(id: String, page: Int): People = throw Exception("Not supported")

    override suspend fun getServers(id: String, videoType: Video.Type): List<Video.Server> = try {
        val doc = service.getPage(id)
        val servers = mutableListOf<Video.Server>()

        doc.select("a").forEach { link ->
            val text = link.text().trim()
            val href = link.attr("abs:href").ifEmpty { link.attr("href") }
            if (href.isNotEmpty() && (text.contains("Opción", true) || text.contains("Servidor", true) || text.contains("FHD", true))) {
                val finalUrl = if (href.startsWith("http")) href else "$baseUrl/${href.removePrefix("/")}"
                servers.add(Video.Server(finalUrl, text))
            }
        }

        if (servers.isEmpty() && doc.select("iframe").isNotEmpty()) {
            servers.add(Video.Server(id, "Reproductor Automático"))
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
            } catch (e: Exception) { break }
        }
        return Video("", emptyList())
    }

    private fun getInfoItem(id: String): TvShow {
        val t = if(id == "creador-info") "Reportar problemas" else "Apoya al Proveedor"
        val p = if(id == "creador-info") "https://i.ibb.co/dsknGBHT/Imagen-de-Whats-App-2025-09-06-a-las-19-00-50-e8e5bcaa.jpg" else "https://i.ibb.co/B5gKLkqS/nuevo-formato-2-K-202604112205.jpg"
        return TvShow(
            id = id,
            title = t,
            poster = p,
            banner = p,
            overview = if(id == "creador-info") "@NandoGT" else "Apoya el proyecto.",
            providerName = name
        )
    }
}