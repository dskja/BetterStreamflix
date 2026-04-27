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
import org.jsoup.nodes.Element
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

object TvLibrefutbolProvider : Provider {

    override val name = "Tv Libre Futbol"
    override val baseUrl = "https://www.librefutbol2.com"
    override val logo = "https://i.ibb.co/q3v6R9qQ/librefutbol.jpg"
    override val language = "es"

    private const val TAG = "TvLibrefutbol"
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
        @GET
        suspend fun getPage(
            @Url url: String,
            @Header("Referer") referer: String = "https://www.tvporinternet2.com/"
        ): Document
    }

    // --- BÚSQUEDA DEL TESORO: PARSER DE CARGA DINÁMICA MEJORADO ---
    private fun parseChannels(doc: Document): List<TvShow> {
        val results = mutableListOf<TvShow>()

        // 1. MÉTODO QUIRÚRGICO: Extraer canales del Script (homeChannels)
        doc.select("script").forEach { script ->
            val data = script.data()
            if (data.contains("homeChannels") || data.contains("const channels")) {
                try {
                    val htmlInsideScript = data.substringAfter("`").substringBeforeLast("`")
                    if (htmlInsideScript.length > 100) {
                        val scriptDoc = Jsoup.parse(htmlInsideScript)
                        scriptDoc.select("a").forEach { a ->
                            val link = a.attr("href")
                            val title = a.text().trim().ifEmpty { a.selectFirst("img")?.attr("alt") ?: "" }

                            // --- CORRECCIÓN DE LOGOS (FILTRADO DE PAYPAL) ---
                            val imgElement = a.select("img").firstOrNull { img ->
                                val src = img.attr("src").lowercase()
                                !src.contains("paypal") && !src.contains("pago") &&
                                        !src.contains("donar") && !src.contains("pay.png") &&
                                        !src.contains("cafecito") && !src.contains("qr") &&
                                        src.isNotEmpty()
                            } ?: a.selectFirst("img")

                            val rawImg = imgElement?.attr("src") ?: ""
                            val img = if (rawImg.startsWith("http")) rawImg else "$baseUrl/${rawImg.removePrefix("/")}"

                            if (isValidChannel(link, title)) {
                                val finalUrl = if (link.startsWith("http")) link else "$baseUrl/${link.removePrefix("/")}"
                                results.add(TvShow(id = finalUrl, title = title, poster = img, banner = img, providerName = name))
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error procesando script de canales: ${e.message}")
                }
            }
        }

        // 2. FALLBACK: Si el script falló, barremos los enlaces estáticos con el mismo filtro
        if (results.isEmpty()) {
            doc.select("a:has(img)").forEach { a ->
                val link = a.attr("abs:href").ifEmpty { a.attr("href") }

                val imgElement = a.select("img").firstOrNull { img ->
                    val src = img.attr("src").lowercase()
                    !src.contains("paypal") && !src.contains("donar") && !src.contains("pago") &&
                            !src.contains("qr") && src.isNotEmpty()
                } ?: a.selectFirst("img")

                val title = imgElement?.attr("alt")?.trim() ?: a.text().trim()
                val poster = imgElement?.attr("abs:src") ?: imgElement?.attr("src") ?: ""

                if (isValidChannel(link, title)) {
                    results.add(TvShow(id = link, title = title, poster = poster, banner = poster, providerName = name))
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
            val doc = service.getPage(baseUrl)
            val all = parseChannels(doc)

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
        val doc = service.getPage("$baseUrl/?s=$query")
        parseChannels(doc)
    } catch (_: Exception) { emptyList() }

    override suspend fun getMovies(page: Int): List<Movie> = emptyList()

    override suspend fun getTvShows(page: Int): List<TvShow> = try {
        parseChannels(service.getPage(baseUrl))
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

        // 1. Prioridad: Imagen destacada oficial de WordPress (Evita logos de pago)
        var imgElement = doc.select("img.wp-post-image, img.attachment-post-thumbnail").firstOrNull { img ->
            val src = img.attr("src").lowercase()
            forbidden.none { it in src }
        }

        // 2. Segunda opción: Imagen en el contenido principal que coincida con el título
        if (imgElement == null) {
            val titleKeywords = t.lowercase().split(" ").filter { it.length > 3 }
            imgElement = doc.select(".entry-content img, .post-content img, article img").firstOrNull { img ->
                val alt = img.attr("alt").lowercase()
                val src = img.attr("src").lowercase()
                titleKeywords.any { it in alt || it in src } && forbidden.none { it in src }
            }
        }

        // 3. Tercera opción: Cualquier imagen en el contenido que no sea prohibida
        if (imgElement == null) {
            imgElement = doc.select(".entry-content img, .card-body img").firstOrNull { img ->
                val src = img.attr("src").lowercase()
                forbidden.none { it in src } && src.isNotEmpty()
            }
        }

        val rawImg = imgElement?.attr("abs:src")?.ifEmpty { imgElement?.attr("src") } ?: ""
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