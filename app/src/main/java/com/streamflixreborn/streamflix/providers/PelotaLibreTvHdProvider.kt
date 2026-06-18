package com.streamflixreborn.streamflix.providers

import android.util.Base64
import android.util.Log
import com.streamflixreborn.streamflix.adapters.AppAdapter
import com.streamflixreborn.streamflix.extractors.Extractor
import com.streamflixreborn.streamflix.models.*
import com.streamflixreborn.streamflix.utils.JsUnpacker
import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

// IptvProvider con TvShow para aprovechar el "Direct Play" o la selección manual según el usuario
object PelotaLibreTvHdProvider : IptvProvider {
    override val name = "Pelota Libre TV"
    override val baseUrl = "https://pelotalibretvhd.live"
    override val logo = "https://i.ibb.co/qYgyrsYS/Pelota-Libre.jpg" // Logo oficial de la página
    override val language = "es"

    private const val TAG = "PelotaLibre"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .cookieJar(object : CookieJar {
            private val cookieStore = HashMap<String, MutableList<Cookie>>()
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                cookieStore[url.host] = cookies.toMutableList()
            }
            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                return cookieStore[url.host] ?: ArrayList()
            }
        })
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .header("Accept-Language", "es-ES,es;q=0.8,en-US;q=0.5,en;q=0.3")
                .build()
            chain.proceed(request)
        }
        .build()

    private interface ApiService {
        @GET
        suspend fun getHtml(@Url url: String): Document
    }

    private val api = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(JsoupConverterFactory.create())
        .build()
        .create(ApiService::class.java)

    // FUNCIÓN DE CAZA 1: Los Canales 24/7
    private suspend fun fetchChannels(): List<TvShow> {
        val channels = mutableListOf<TvShow>()
        try {
            val doc = api.getHtml(baseUrl)
            val channelElements = doc.select(".grid-container a, a#canal")

            for (aTag in channelElements) {
                val href = aTag.attr("href")
                val img = aTag.selectFirst("img") ?: continue

                val posterUrl = img.attr("src").ifEmpty { img.attr("data-src") }
                val title = img.attr("alt").takeIf { it.isNotEmpty() } ?: "Canal en Vivo"

                if (href.isNotEmpty() && posterUrl.isNotEmpty() && !href.contains("javascript")) {
                    val url = if (href.startsWith("http")) href else "$baseUrl/${href.removePrefix("/")}"
                    val posterFinal = if (posterUrl.startsWith("http")) posterUrl else "$baseUrl/${posterUrl.removePrefix("/")}"

                    channels.add(TvShow(
                        id = url,
                        title = title,
                        poster = posterFinal,
                        banner = posterFinal
                    ))
                }
            }
        } catch (e: Exception) { Log.e(TAG, "Error parseando Canales: ${e.message}") }
        return channels
    }

    // FUNCIÓN DE CAZA 2: La Agenda Aplanada (Un ítem por canal para soportar el Autoplay)
    private suspend fun fetchAgenda(): List<TvShow> {
        val matches = mutableListOf<TvShow>()
        try {
            val doc = api.getHtml(baseUrl)
            val iframeSrc = doc.selectFirst("iframe[src*='agenda']")?.attr("src")

            if (!iframeSrc.isNullOrEmpty()) {
                val agendaUrl = if (iframeSrc.startsWith("//")) "https:$iframeSrc"
                else if (iframeSrc.startsWith("http")) iframeSrc
                else "$baseUrl/${iframeSrc.removePrefix("/")}"

                val agendaDoc = api.getHtml(agendaUrl)

                // HACK DE CARÁTULAS: Escanear la etiqueta <style> para robar las banderas CSS
                val styleBlocks = agendaDoc.select("style").joinToString("\n") { it.html() }
                val cssRegex = """\.([a-zA-Z0-9_-]+)\s*>\s*a:before\s*\{\s*background-image:\s*url\(['"]?([^)'"]+)['"]?\)""".toRegex()
                val classToImage = mutableMapOf<String, String>()

                cssRegex.findAll(styleBlocks).forEach { matchResult ->
                    val className = matchResult.groupValues[1]
                    var imgUrl = matchResult.groupValues[2].trim('\'', '"')
                    if (imgUrl.startsWith("//")) imgUrl = "https:$imgUrl"
                    classToImage[className] = imgUrl
                }

                val matchElements = agendaDoc.select("ul.menu > li")
                for (element in matchElements) {
                    val mainLink = element.selectFirst("> a") ?: continue

                    // Limpieza del Título: Quitamos la liga y dejamos solo los equipos
                    val rawTitle = mainLink.ownText().trim()
                    val titleClean = if (rawTitle.contains(":")) rawTitle.substringAfter(":").trim() else rawTitle
                    val time = mainLink.selectFirst("span.t")?.text()?.trim() ?: ""

                    val liClass = element.className().split(" ").firstOrNull { it.isNotEmpty() } ?: ""
                    val matchPoster = classToImage[liClass] ?: logo

                    val subChannels = element.select("ul li a")
                    for (channelTag in subChannels) {
                        val href = channelTag.attr("href")
                        val channelName = channelTag.ownText().trim()
                        val quality = channelTag.selectFirst("span")?.text()?.trim() ?: ""

                        // Creador del Título Perfecto: "[15:00] Real Madrid vs Barcelona - ESPN"
                        val channelLabel = if (quality.isNotEmpty()) "$channelName ($quality)" else channelName
                        val displayTitle = if (time.isNotEmpty()) "[$time] $titleClean - $channelLabel" else "$titleClean - $channelLabel"

                        if (href.isNotEmpty() && !href.contains("javascript")) {
                            val url = if (href.startsWith("http")) href else "$baseUrl/${href.removePrefix("/")}"

                            matches.add(TvShow(
                                id = url,
                                title = displayTitle,
                                poster = matchPoster,
                                banner = matchPoster
                            ))
                        }
                    }
                }
            }
        } catch (e: Exception) { Log.e(TAG, "Error parseando Agenda: ${e.message}") }
        return matches
    }

    override suspend fun getHome(): List<Category> = coroutineScope {
        val categories = mutableListOf<Category>()

        try {
            // Paralelismo total: Agenda y Canales 24/7 cargan al mismo tiempo y sin bloquearse
            val channelsDeferred = async { try { fetchChannels() } catch(e:Exception) { emptyList<TvShow>() } }
            val agendaDeferred = async { try { fetchAgenda() } catch(e:Exception) { emptyList<TvShow>() } }

            val matches = agendaDeferred.await()
            val channels = channelsDeferred.await()

            if (matches.isNotEmpty()) {
                categories.add(Category(name = "Agenda Deportiva", list = matches))
            }
            if (channels.isNotEmpty()) {
                categories.add(Category(name = "Canales 24/7", list = channels))
            }

            // Añadiendo la firma del creador al final de la vista principal
            categories.add(
                Category(
                    name = "Soporte y Ayuda",
                    list = listOf(getInfoItem("creador-info"), getInfoItem("apoyo-info"))
                )
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error crítico al cargar getHome: ${e.message}")
            return@coroutineScope listOf(Category(name = "Soporte y Ayuda", list = listOf(getInfoItem("creador-info"), getInfoItem("apoyo-info"))))
        }

        return@coroutineScope categories
    }

    // DIRECTORIO: Fusionamos Canales y Agenda para que el Catálogo esté completo
    override suspend fun getTvShows(page: Int): List<TvShow> = coroutineScope {
        if (page == 1) {
            val agendaDeferred = async { try { fetchAgenda() } catch(e:Exception) { emptyList<TvShow>() } }
            val channelsDeferred = async { try { fetchChannels() } catch(e:Exception) { emptyList<TvShow>() } }

            // Los canales 24/7 arriba, y luego todos los partidos aplanados
            channelsDeferred.await() + agendaDeferred.await()
        } else {
            emptyList()
        }
    }

    override suspend fun getMovies(page: Int): List<Movie> = emptyList()

    override suspend fun search(query: String, page: Int): List<AppAdapter.Item> = emptyList()

    override suspend fun getGenre(id: String, page: Int): Genre = Genre(id = id, name = id, shows = emptyList())
    override suspend fun getPeople(
        id: String,
        page: Int
    ): People {
        TODO("Not yet implemented")
    }

    override suspend fun getMovie(id: String): Movie = throw NotImplementedError()

    override suspend fun getTvShow(id: String): TvShow {
        // Interceptamos la acción si el usuario hace clic en los elementos de Soporte y Ayuda
        if (id == "creador-info" || id == "apoyo-info") return getInfoItem(id)

        // Al aplanar todo, el ID ahora es la URL exacta del canal. Ya no necesitamos lógicas complejas.
        val nameGuess = try { id.toHttpUrl().pathSegments.last().removeSuffix(".html").replace("-", " ").uppercase() } catch(e:Exception) { "Canal 24/7" }
        return TvShow(
            id = id,
            title = nameGuess,
            overview = "Disfruta de la transmisión ininterrumpida. Si la reproducción falla, intenta con otra opción.",
            poster = logo,
            banner = logo,
            seasons = listOf(Season(id = id, title = "Transmisión", number = 1))
        )
    }

    override suspend fun getEpisodesBySeason(seasonId: String): List<Episode> {
        // Evitamos que intente buscar episodios para las tarjetas de información
        if (seasonId == "creador-info" || seasonId == "apoyo-info") return emptyList()

        // Como aplanamos la agenda, cada TvShow tiene exactamente 1 Episodio (compatible con AutoPlay)
        return listOf(
            Episode(
                id = seasonId,
                title = "Ver Transmisión",
                number = 1,
                poster = logo
            )
        )
    }

    override suspend fun getServers(id: String, videoType: Video.Type): List<Video.Server> {
        val servers = mutableListOf<Video.Server>()

        // Bloqueamos la búsqueda de servidores si hacen clic en las opciones de soporte
        if (id == "creador-info" || id == "apoyo-info") return emptyList()

        // Atajo directo de la Agenda
        if (id.contains("eventos.html?r=")) {
            try {
                val encodedParam = id.substringAfter("r=").substringBefore("&")
                val decodedUrl = String(Base64.decode(encodedParam, Base64.DEFAULT))
                servers.add(Video.Server(id = decodedUrl, name = "Reproductor Agenda"))
                return servers
            } catch(e: Exception) { Log.e(TAG, "Error decodificando atajo: ${e.message}") }
        }

        // Si el ID ya es un link de reproductor externo directo
        if (id.contains("latamplay") || id.contains("streamtpday") || id.contains("streamx741") || id.contains("zonalive.click")) {
            servers.add(Video.Server(id = id, name = "Reproductor Directo"))
            return servers
        }

        // Búsqueda para Canales 24/7
        try {
            val doc = api.getHtml(id)
            val iframeSrc = doc.selectFirst("iframe#embedIframe, .preframe iframe, .subiframe iframe")?.attr("src")

            if (!iframeSrc.isNullOrEmpty()) {
                val url = if (iframeSrc.startsWith("http")) iframeSrc
                else if (iframeSrc.startsWith("//")) "https:$iframeSrc"
                else "$baseUrl/${iframeSrc.removePrefix("/")}"

                servers.add(Video.Server(id = url, name = "Reproductor Principal"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al obtener servidor: ${e.message}")
        }

        return servers
    }

    override suspend fun getVideo(server: Video.Server): Video {
        var currentUrl = server.id
        var currentReferer = baseUrl
        val maxDepth = 10
        var depth = 0

        // Filtro Anti-Trampas
        val isDecoy: (String) -> Boolean = { link ->
            link.contains("amagi.tv") || link.contains("lovetvchannels") ||
                    link.contains("channel02secure") || link.contains("redirect=true") ||
                    link.contains("grupoz.cl") || link.contains("retroplus") ||
                    link.contains("frequency.stream")
        }

        while (depth < maxDepth) {
            depth++
            try {
                // Hack de redirección manual
                if (currentUrl.contains("latamplay") && currentUrl.contains("/channel/")) {
                    val streamName = currentUrl.substringAfter("channel/").substringBefore("?").removeSuffix(".php")
                    currentReferer = currentUrl
                    currentUrl = "https://streamtpday1.xyz/global1.php?stream=$streamName"
                    continue
                }

                val request = Request.Builder()
                    .url(currentUrl)
                    .header("Referer", currentReferer)
                    .build()

                val response = client.newCall(request).execute()
                val htmlCrudo = response.body?.string() ?: ""
                val cleanHtml = htmlCrudo.replace("\\/", "/")
                var htmlParaAnalizar = cleanHtml

                val currentUri = try { currentUrl.toHttpUrl() } catch (e: Exception) { null }
                val channelId = currentUri?.queryParameter("id") ?: currentUri?.queryParameter("channel") ?: currentUri?.queryParameter("stream") ?: ""
                val hostSeguro = currentUri?.host ?: "ontve.click"
                val origin = currentUri?.let { "https://${it.host}" } ?: baseUrl

                // 1. Enrutador Javascript
                if (channelId.isNotEmpty()) {
                    val iframeBlocks = """(?:id|channel|stream)\s*===\s*["']([^"']+)["']\)\s*\{[^}]*src=["']([^"']+)["']""".toRegex().findAll(cleanHtml)
                    val match = iframeBlocks.firstOrNull { it.groupValues[1] == channelId }
                    if (match != null) {
                        val decodedIframe = match.groupValues[2]
                        currentReferer = currentUrl
                        currentUrl = if (decodedIframe.startsWith("http")) decodedIframe
                        else if (decodedIframe.startsWith("//")) "https:$decodedIframe"
                        else "https://$hostSeguro/${decodedIframe.removePrefix("/")}"
                        continue
                    }

                    val configBlocks = """["']([^"']+)["']\s*:\s*\{[^}]*url:\s*["']([^"']+)["']""".toRegex().findAll(cleanHtml)
                    val configMatch = configBlocks.firstOrNull { it.groupValues[1] == channelId }
                    if (configMatch != null) {
                        val decodedUrl = configMatch.groupValues[2]
                        currentReferer = currentUrl
                        currentUrl = if (decodedUrl.startsWith("http")) decodedUrl
                        else if (decodedUrl.startsWith("//")) "https:$decodedUrl"
                        else "https://$hostSeguro/${decodedUrl.removePrefix("/")}"
                        continue
                    }
                }

                // 2. Desofuscador Matemático P2P
                val pairRegex = """\[\s*(\d+)\s*,\s*["']([^"']+)["']\s*\]""".toRegex()
                val pairsMatches = pairRegex.findAll(htmlParaAnalizar).toList()

                if (pairsMatches.size > 10) {
                    val pairs = pairsMatches.map { Pair(it.groupValues[1].toInt(), it.groupValues[2]) }.sortedBy { it.first }
                    val firstPair = pairs.firstOrNull { it.first == 0 }

                    if (firstPair != null) {
                        try {
                            val decodedB64 = String(Base64.decode(firstPair.second, Base64.DEFAULT))
                            val numberOnly = decodedB64.replace(Regex("\\D"), "").toLongOrNull()

                            if (numberOnly != null) {
                                val possibleKs = listOf(numberOnly - 104L, numberOnly - 115L, numberOnly - 47L)

                                for (k in possibleKs) {
                                    val playbackUrlBuilder = StringBuilder()
                                    for (pair in pairs) {
                                        val d64 = String(Base64.decode(pair.second, Base64.DEFAULT))
                                        val num = d64.replace(Regex("\\D"), "").toLongOrNull()
                                        if (num != null) {
                                            val charCode = num - k
                                            playbackUrlBuilder.append(charCode.toInt().toChar())
                                        }
                                    }

                                    val finalUrl = playbackUrlBuilder.toString()
                                    if (finalUrl.isNotEmpty() && (finalUrl.startsWith("http") || finalUrl.startsWith("//")) && (finalUrl.contains(".m3u8") || finalUrl.contains(".mpd"))) {
                                        val validUrl = if (finalUrl.startsWith("//")) "https:$finalUrl" else finalUrl
                                        Log.d(TAG, "M3U8 Decodificado exitosamente.")
                                        return Video(validUrl, emptyList(), mapOf("Referer" to currentUrl, "User-Agent" to USER_AGENT, "Origin" to origin))
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }

                // 3. Enlaces Directos M3U8
                val m3u8Regex = """(https?://[^"'\s]+\.(?:m3u8|mpd)[^"'\s]*)""".toRegex()
                val relativeRegex = """(?:source|file|src)\s*:\s*["']([^"']+\.(?:m3u8|mpd)[^"']*)["']""".toRegex()

                val allMatches = m3u8Regex.findAll(cleanHtml).map { it.groupValues[1] }.toList() +
                        relativeRegex.findAll(cleanHtml).map { it.groupValues[1] }.toList()

                val validM3u8 = allMatches.firstOrNull { !isDecoy(it) }
                if (validM3u8 != null) {
                    val finalUrl = if (validM3u8.startsWith("http")) validM3u8 else "https://$hostSeguro/${validM3u8.removePrefix("/")}"
                    Log.d(TAG, "M3U8 Encontrado directamente.")
                    return Video(finalUrl, emptyList(), mapOf("Referer" to currentUrl, "User-Agent" to USER_AGENT, "Origin" to origin))
                }

                // 4. JS Ofuscado (eval)
                if (cleanHtml.contains("eval(function(p,a,c,k,e,d)")) {
                    val unpackedJS = JsUnpacker(cleanHtml).unpack()
                    if (!unpackedJS.isNullOrEmpty()) {
                        htmlParaAnalizar += unpackedJS
                        val unpackedMatches = m3u8Regex.findAll(unpackedJS).map { it.groupValues[1] }.toList()
                        val validUnpackedM3u8 = unpackedMatches.firstOrNull { !isDecoy(it) }
                        if (validUnpackedM3u8 != null) {
                            Log.d(TAG, "M3U8 Encontrado en JS.")
                            return Video(validUnpackedM3u8, emptyList(), mapOf("Referer" to currentUrl, "User-Agent" to USER_AGENT, "Origin" to origin))
                        }
                    }
                }

                // 5. Base64 Puro
                val base64HttpRegex = """["'](aHR0c[a-zA-Z0-9=]+)["']""".toRegex()
                val b64Matches = base64HttpRegex.findAll(htmlParaAnalizar)
                for (match in b64Matches) {
                    try {
                        val decoded = String(Base64.decode(match.groupValues[1], Base64.DEFAULT))
                        if (decoded.contains(".m3u8") && !isDecoy(decoded)) {
                            return Video(decoded, emptyList(), mapOf("Referer" to currentUrl, "User-Agent" to USER_AGENT, "Origin" to origin))
                        }
                    } catch (_: Exception) {}
                }

                // 6. Iframes en Base64
                val atobRegex = """atob\(['"]([^"']+)['"]\)""".toRegex()
                val atobMatches = atobRegex.findAll(htmlParaAnalizar)
                var foundHiddenIframe = false

                for (match in atobMatches) {
                    try {
                        val decoded = String(Base64.decode(match.groupValues[1], Base64.DEFAULT))
                        val decodedIframe = Jsoup.parse(decoded).selectFirst("iframe")?.attr("src") ?: if (decoded.startsWith("http")) decoded else ""

                        if (decodedIframe.isNotEmpty() && !isDecoy(decodedIframe)) {
                            currentReferer = currentUrl
                            currentUrl = if (decodedIframe.startsWith("http")) decodedIframe
                            else if (decodedIframe.startsWith("//")) "https:$decodedIframe"
                            else "https://$hostSeguro/${decodedIframe.removePrefix("/")}"
                            foundHiddenIframe = true
                            break
                        }
                    } catch (_: Exception) {}
                }
                if (foundHiddenIframe) continue

                // 7. Iframes HTML normales
                val doc = Jsoup.parse(htmlParaAnalizar)
                val iframes = doc.select("iframe")
                val nextIframe = iframes.firstOrNull {
                    val src = it.attr("src").ifEmpty { it.attr("data-src") }
                    src.isNotEmpty() && !src.contains("chatango") && !src.contains("monetag")
                }?.let { it.attr("src").ifEmpty { it.attr("data-src") } } ?: ""

                if (nextIframe.isNotEmpty() && nextIframe != currentUrl && !isDecoy(nextIframe)) {
                    currentReferer = currentUrl
                    currentUrl = if (nextIframe.startsWith("http")) nextIframe
                    else if (nextIframe.startsWith("//")) "https:$nextIframe"
                    else "https://$hostSeguro/${nextIframe.removePrefix("/")}"
                    continue
                }

                // 8. Redirecciones JS
                val windowLocationRegex = """(?:window\.location\.replace|window\.location\.href)\s*=\s*['"]([^"']+)['"]""".toRegex()
                val locMatch = windowLocationRegex.find(htmlParaAnalizar)
                if (locMatch != null && !isDecoy(locMatch.groupValues[1])) {
                    val redirectUrl = locMatch.groupValues[1]
                    currentReferer = currentUrl
                    currentUrl = if (redirectUrl.startsWith("http")) redirectUrl
                    else if (redirectUrl.startsWith("//")) "https:$redirectUrl"
                    else "https://$hostSeguro/${redirectUrl.removePrefix("/")}"
                    continue
                }

                break
            } catch (e: Exception) {
                Log.e(TAG, "Error en rastreo: ${e.message}")
                break
            }
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