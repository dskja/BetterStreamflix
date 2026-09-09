package com.dskja.betterstreamflix.providers

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

import com.dskja.betterstreamflix.utils.UserPreferences

import android.util.Log
import com.dskja.betterstreamflix.adapters.AppAdapter
import com.dskja.betterstreamflix.models.*
import com.dskja.betterstreamflix.utils.SportsIptvStreamResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder

object CableVisionHDProvider : IptvProvider, ProviderConfigUrl {
    override val name = "CableVisionHD"
    override val defaultBaseUrl = "https://www.cablevisionhd.com"
    override val baseUrl: String
        get() = UserPreferences.getProviderCache(this, UserPreferences.PROVIDER_URL).ifBlank { defaultBaseUrl }
    override val changeUrlMutex = Mutex()

    override suspend fun onChangeUrl(forceRefresh: Boolean): String = changeUrlMutex.withLock {
        baseUrl
    }
    override val logo = "https://i.ibb.co/4gMQkN2b/imagen-2025-09-05-212536248.png"
    override val language = "es"

    private const val TAG = "CableVisionHD"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36"
    private val channelsUrl = "$baseUrl/parrilla-directo.php"

    private var serverSocket: ServerSocket? = null
    private var localServerThread: Thread? = null
    private var currentPlaylistUrl: String = ""
    private var localPort: Int = 0

    private var cachedChannels: List<TvShow>? = null
    private var cachedHome: List<Category>? = null

    private val client = OkHttpClient.Builder()
        .cookieJar(object : CookieJar {
            private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                val domainCookies = cookieStore.getOrPut(url.host) { mutableListOf() }
                for (cookie in cookies) {
                    val existingIndex = domainCookies.indexOfFirst { it.name == cookie.name && it.path == cookie.path }
                    if (existingIndex != -1) {
                        domainCookies[existingIndex] = cookie
                    } else {
                        domainCookies.add(cookie)
                    }
                }
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                return cookieStore[url.host]?.filter { it.expiresAt > System.currentTimeMillis() } ?: emptyList()
            }
        })
        .addInterceptor { chain ->
            val originalRequest = chain.request()
            val originalUrl = originalRequest.url.toString()

            val requestBuilder = originalRequest.newBuilder()
                .header("User-Agent", USER_AGENT)
                .header("Accept", "*/*")
                .header("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
                .header("X-Requested-With", "XMLHttpRequest")

            if (originalUrl.contains("ksdjugfssddeports.com") ||
                originalUrl.contains("saohgdassregions.com") ||
                originalUrl.contains("playlist.php") ||
                originalUrl.contains(".ts") ||
                originalUrl.contains(":9092")) {
                requestBuilder
                    .header("Origin", "https://embed.ksdjugfssddeports.com")
                    .header("Referer", "https://embed.ksdjugfssddeports.com/")
            }

            chain.proceed(requestBuilder.build())
        }
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private suspend fun fetchDocument(url: String, referer: String = baseUrl): Document? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("Referer", referer)
                .build()
            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: return null
            Jsoup.parse(html)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching $url: ${e.message}")
            null
        }
    }

    private fun cleanChannelTitle(title: String): String {
        var cleanTitle = title.trim()
        cleanTitle = cleanTitle.replace(Regex("\\.php$", RegexOption.IGNORE_CASE), "")
        cleanTitle = cleanTitle.replace(Regex("\\s*en vivo por internet\\s*$", RegexOption.IGNORE_CASE), "")
        cleanTitle = cleanTitle.replace(Regex("\\s*en vivo\\s*$", RegexOption.IGNORE_CASE), "")
        cleanTitle = cleanTitle.replace(Regex("\\s*online\\s*$", RegexOption.IGNORE_CASE), "")
        cleanTitle = cleanTitle.replace(Regex("\\s*gratis\\s*$", RegexOption.IGNORE_CASE), "")

        cleanTitle = cleanTitle.split(" ").joinToString(" ") { word ->
            if (word.length > 2 && word.all { it.isLowerCase() }) {
                word.replaceFirstChar { it.uppercase() }
            } else {
                word
            }
        }

        return cleanTitle.trim()
    }

    private fun extractChannelsFromHtml(doc: Document): List<TvShow> {
        val channels = mutableListOf<TvShow>()
        val seenIds = mutableSetOf<String>()

        // 1. Extraer canales del div#channels (Deportes)
        doc.select("div#channels a.channel-card").forEach { element ->
            val title = element.selectFirst("p")?.text()?.trim() ?: ""
            val href = element.attr("href")
            val link = if (href.startsWith("http")) href else if (href.isNotEmpty()) "$baseUrl/$href" else ""

            val imgElement = element.selectFirst("img")
            var img = ""
            if (imgElement != null) {
                val src = imgElement.attr("src")
                if (src.isNotEmpty()) {
                    img = when {
                        src.startsWith("http") -> src
                        src.startsWith("/") -> "$baseUrl$src"
                        else -> "$baseUrl/$src"
                    }
                }
            }

            if (title.isNotEmpty() && link.isNotEmpty() && isValidChannel(link, title)) {
                val cleanTitle = cleanChannelTitle(title)
                if (seenIds.add(link)) {
                    channels.add(
                        TvShow(
                            id = link,
                            title = cleanTitle,
                            poster = img,
                            banner = img,
                            providerName = name
                        )
                    )
                }
            }
        }

        // 2. Extraer canales del div#regional-seo-links (Regionales)
        doc.select("div#regional-seo-links a.channel-card").forEach { element ->
            val title = element.selectFirst("p")?.text()?.trim()
                ?: element.selectFirst("img")?.attr("alt")?.trim()
                ?: ""
            val href = element.attr("href")
            val link = if (href.startsWith("http")) href else if (href.isNotEmpty()) "$baseUrl/$href" else ""

            val imgElement = element.selectFirst("img")
            var img = ""
            if (imgElement != null) {
                val src = imgElement.attr("src")
                if (src.isNotEmpty()) {
                    img = when {
                        src.startsWith("http") -> src
                        src.startsWith("/") -> "$baseUrl$src"
                        else -> "$baseUrl/$src"
                    }
                }
            }

            if (title.isNotEmpty() && link.isNotEmpty() && isValidChannel(link, title)) {
                val cleanTitle = cleanChannelTitle(title)
                if (seenIds.add(link)) {
                    channels.add(
                        TvShow(
                            id = link,
                            title = cleanTitle,
                            poster = img,
                            banner = img,
                            providerName = name
                        )
                    )
                }
            }
        }

        // 3. Fallback: Buscar todos los enlaces .php o -ver.php
        if (channels.size < 10) {
            doc.select("a[href*='-ver.php'], a[href*='.php']").forEach { element ->
                val href = element.attr("href")
                val link = if (href.startsWith("http")) href else if (href.isNotEmpty()) "$baseUrl/$href" else ""

                if (link.isNotEmpty() && link != baseUrl && link != "$baseUrl/" && link != channelsUrl) {
                    val imgElement = element.selectFirst("img")
                    var title = ""
                    var img = ""

                    if (imgElement != null) {
                        title = imgElement.attr("alt").trim()
                        if (title.isEmpty()) {
                            title = imgElement.attr("title").trim()
                        }
                        val src = imgElement.attr("src")
                        if (src.isNotEmpty()) {
                            img = when {
                                src.startsWith("http") -> src
                                src.startsWith("/") -> "$baseUrl$src"
                                else -> "$baseUrl/$src"
                            }
                        }
                    }

                    if (title.isEmpty()) {
                        title = element.selectFirst("p")?.text()?.trim() ?: ""
                    }

                    if (title.isEmpty()) {
                        title = element.text().trim()
                    }

                    if (title.isEmpty()) {
                        title = link.substringAfterLast("/").replace("-ver.php", "").replace(".php", "").replace("-", " ")
                    }

                    val cleanTitle = cleanChannelTitle(title)

                    if (cleanTitle.isNotEmpty() && isValidChannel(link, cleanTitle)) {
                        if (seenIds.add(link)) {
                            channels.add(
                                TvShow(
                                    id = link,
                                    title = cleanTitle,
                                    poster = img,
                                    banner = img,
                                    providerName = name
                                )
                            )
                        }
                    }
                }
            }
        }

        Log.d(TAG, "Canales extraídos: ${channels.size}")
        return channels.distinctBy { it.id }
    }

    private fun isValidChannel(link: String, title: String): Boolean {
        val cleanLink = link.trim().removeSuffix("/")
        val cleanBase = baseUrl.removeSuffix("/")

        return link.isNotEmpty() &&
                title.isNotEmpty() &&
                title.length > 2 &&
                cleanLink != cleanBase &&
                !link.contains("#") &&
                !link.contains("javascript:") &&
                !title.contains("Telegram", ignoreCase = true) &&
                !title.contains("Soporte", ignoreCase = true) &&
                !title.contains("Donar", ignoreCase = true) &&
                !title.contains("Paypal", ignoreCase = true) &&
                !title.contains("Mundo Latam", ignoreCase = true) &&
                !title.contains("🌐", ignoreCase = true) &&
                !title.contains(".php", ignoreCase = true) &&
                !title.contains("en vivo por internet", ignoreCase = true) &&
                !title.contains("inicio", ignoreCase = true) &&
                !title.contains("home", ignoreCase = true)
    }

    override suspend fun getHome(): List<Category> = coroutineScope {
        cachedHome?.let { return@coroutineScope it }

        try {
            val all = if (cachedChannels != null) cachedChannels!! else {
                val doc = fetchDocument(channelsUrl) ?: throw Exception("No se pudo cargar")
                val channels = extractChannelsFromHtml(doc)
                cachedChannels = channels
                channels
            }

            Log.d(TAG, "✅ Total canales: ${all.size}")

            val categories = mutableListOf<Category>()

            if (all.isNotEmpty()) {
                val contentCategories = listOf(
                    async { Category(name = "Todos los Canales", list = all) },
                    async {
                        Category(name = "Deportes", list = all.filter {
                            it.title.contains("sport", true) ||
                                    it.title.contains("espn", true) ||
                                    it.title.contains("fox", true) ||
                                    it.title.contains("tyc", true) ||
                                    it.title.contains("directv", true) ||
                                    it.title.contains("azteca", true) ||
                                    it.title.contains("futbol", true)
                        })
                    },
                    async {
                        Category(name = "Regionales", list = all.filter {
                            it.title.contains("telefe", true) ||
                                    it.title.contains("trece", true) ||
                                    it.title.contains("telemundo", true) ||
                                    it.title.contains("univision", true) ||
                                    it.title.contains("caracol", true) ||
                                    it.title.contains("rcn", true) ||
                                    it.title.contains("latina", true) ||
                                    it.title.contains("atv", true) ||
                                    it.title.contains("america", true) ||
                                    it.title.contains("estrellas", true)
                        })
                    },
                    async {
                        Category(name = "Cine y Series", list = all.filter {
                            listOf("hbo", "max", "cine", "warner", "star", "tnt", "film", "movie", "golden", "amc", "axn", "sony", "universal").any { s ->
                                it.title.contains(s, true)
                            }
                        })
                    }
                ).awaitAll().filter { it.list.isNotEmpty() }

                categories.addAll(contentCategories)
            }

            categories.add(
                Category(
                    name = "Soporte y Ayuda",
                    list = listOf(getInfoItem("creador-info"), getInfoItem("apoyo-info"))
                )
            )

            cachedHome = categories
            categories
        } catch (e: Exception) {
            Log.e(TAG, "❌ ERROR: ${e.message}")
            listOf(Category(name = "Soporte y Ayuda", list = listOf(getInfoItem("creador-info"), getInfoItem("apoyo-info"))))
        }
    }

    override suspend fun search(query: String, page: Int): List<AppAdapter.Item> = try {
        val allChannels = getTvShows(1)
        allChannels.filter { it.title.contains(query, ignoreCase = true) }
    } catch (_: Exception) { emptyList() }

    override suspend fun getMovies(page: Int): List<Movie> = emptyList()

    override suspend fun getTvShows(page: Int): List<TvShow> {
        cachedChannels?.let { return it }

        return try {
            val doc = fetchDocument(channelsUrl) ?: throw Exception("No se pudo cargar")
            val channels = extractChannelsFromHtml(doc)
            cachedChannels = channels
            channels
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun getGenre(id: String, page: Int): Genre = throw Exception("Not supported")
    override suspend fun getMovie(id: String): Movie = throw Exception("Not supported")

    override suspend fun getTvShow(id: String): TvShow = when (id) {
        "creador-info", "apoyo-info" -> getInfoItem(id)
        else -> {
            try {
                val doc = fetchDocument(id) ?: throw Exception("No se pudo cargar")
                val title = doc.selectFirst("meta[property=og:title]")?.attr("content")
                    ?: doc.selectFirst("h1")?.text()
                    ?: "Canal en Vivo"
                val poster = doc.selectFirst("meta[property=og:image]")?.attr("content") ?: ""

                TvShow(
                    id = id,
                    title = cleanChannelTitle(title),
                    overview = doc.selectFirst("meta[property=og:description]")?.attr("content") ?: "",
                    poster = poster,
                    banner = poster,
                    seasons = listOf(Season(id, 1, "En Vivo", episodes = listOf(Episode(id, 1, "Directo", poster)))),
                    providerName = name
                )
            } catch (e: Exception) {
                TvShow(id = id, title = "Error al cargar señal", providerName = name)
            }
        }
    }

    override suspend fun getEpisodesBySeason(seasonId: String): List<Episode> =
        listOf(Episode(seasonId, 1, "Señal en Directo"))

    override suspend fun getPeople(id: String, page: Int): People = throw Exception("Not supported")

    override suspend fun getServers(id: String, videoType: Video.Type): List<Video.Server> = withContext(Dispatchers.IO) {
        try {
            val doc = fetchDocument(id) ?: throw Exception("No se pudo cargar")
            val servers = SportsIptvStreamResolver.collectServerUrls(doc, baseUrl)
                .map { (name, url) -> Video.Server(id = url, name = name, src = url) }
                .toMutableList()

            if (servers.isEmpty()) {
                val coreRegex = Regex("""https?://[^"'\\\s]+(?:live\d*/|stream\d*/)?core\.php[^"'\\\s]*""")
                coreRegex.findAll(doc.html()).forEachIndexed { index, match ->
                    val url = match.value.replace("\\/", "/")
                    servers.add(Video.Server(id = url, name = "Opción ${index + 1}", src = url))
                }
            }

            servers.distinctBy { it.id }.ifEmpty {
                listOf(Video.Server(id = id, name = "Opción 1", src = id))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getServers failed: ${e.message}")
            listOf(Video.Server(id = id, name = "Opción 1", src = id))
        }
    }

    override suspend fun getVideo(server: Video.Server): Video = withContext(Dispatchers.IO) {
        try {
            stopLocalServer()

            val playlistUrl = SportsIptvStreamResolver.resolvePlaylistUrl(
                client = client,
                serverUrl = server.src.ifBlank { server.id },
                pageReferer = baseUrl,
                userAgent = USER_AGENT,
            )
            if (playlistUrl.isNullOrBlank()) {
                Log.e(TAG, "Servidor offline (sin playlist)")
                return@withContext Video("")
            }

            currentPlaylistUrl = playlistUrl
            val localServerUrl = startLocalServer(playlistUrl)
            if (localServerUrl.isEmpty()) {
                return@withContext Video("")
            }

            Video(
                source = "$localServerUrl/manifest.m3u8",
                headers = emptyMap(),
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}")
            Video("")
        }
    }

    // ==================== SERVIDOR LOCAL ====================

    private fun stopLocalServer() {
        try {
            serverSocket?.close()
            localServerThread?.interrupt()
            serverSocket = null
            localServerThread = null
        } catch (e: Exception) {
            Log.e(TAG, "Error deteniendo servidor: ${e.message}")
        }
    }

    private fun startLocalServer(playlistUrl: String): String {
        try {
            serverSocket = ServerSocket(0)
            localPort = serverSocket!!.localPort

            localServerThread = Thread {
                try {
                    while (!Thread.currentThread().isInterrupted) {
                        val clientSocket = serverSocket?.accept() ?: break
                        Thread {
                            try {
                                handleLocalRequest(clientSocket, playlistUrl)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error: ${e.message}")
                            }
                        }.start()
                    }
                } catch (e: Exception) {
                    if (!Thread.currentThread().isInterrupted) {
                        Log.e(TAG, "Error en servidor: ${e.message}")
                    }
                }
            }

            localServerThread?.start()
            return "http://127.0.0.1:$localPort"
        } catch (e: Exception) {
            Log.e(TAG, "Error iniciando servidor: ${e.message}")
            return ""
        }
    }

    private fun handleLocalRequest(clientSocket: Socket, playlistUrl: String) {
        try {
            val input = clientSocket.getInputStream()
            val output = clientSocket.getOutputStream()

            val buffer = ByteArray(8192)
            val bytesRead = input.read(buffer)
            if (bytesRead <= 0) return

            val requestStr = String(buffer, 0, bytesRead)
            val firstLine = requestStr.split("\n")[0]

            if (firstLine.startsWith("GET")) {
                val path = firstLine.split(" ")[1]

                when {
                    path.startsWith("/manifest.m3u8") -> {
                        val freshManifest = fetchFreshManifest(playlistUrl)
                        if (freshManifest.isNotEmpty()) {
                            val manifestBytes = freshManifest.toByteArray()
                            val responseHeaders = "HTTP/1.1 200 OK\r\n" +
                                    "Content-Type: application/vnd.apple.mpegurl\r\n" +
                                    "Content-Length: ${manifestBytes.size}\r\n" +
                                    "Access-Control-Allow-Origin: *\r\n" +
                                    "Cache-Control: no-cache\r\n" +
                                    "Connection: close\r\n\r\n"

                            output.write(responseHeaders.toByteArray())
                            output.write(manifestBytes)
                            output.flush()
                        }
                    }

                    path.startsWith("/segment/") -> {
                        val encodedUrl = path.substring("/segment/".length)
                        val segmentUrl = URLDecoder.decode(encodedUrl, "UTF-8")

                        val segmentReq = Request.Builder()
                            .url(segmentUrl)
                            .header("User-Agent", USER_AGENT)
                            .header("Accept", "*/*")
                            .header("Origin", "https://embed.ksdjugfssddeports.com")
                            .header("Referer", "https://embed.ksdjugfssddeports.com/")
                            .build()

                        val segmentRes = client.newCall(segmentReq).execute()

                        if (segmentRes.isSuccessful) {
                            val segmentBytes = segmentRes.body?.bytes() ?: ByteArray(0)
                            val responseHeaders = "HTTP/1.1 200 OK\r\n" +
                                    "Content-Type: video/mp2t\r\n" +
                                    "Content-Length: ${segmentBytes.size}\r\n" +
                                    "Access-Control-Allow-Origin: *\r\n" +
                                    "Cache-Control: no-cache\r\n" +
                                    "Connection: close\r\n\r\n"

                            output.write(responseHeaders.toByteArray())
                            output.write(segmentBytes)
                            output.flush()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}")
        } finally {
            try { clientSocket.close() } catch (_: Exception) {}
        }
    }

    private fun fetchFreshManifest(playlistUrl: String): String {
        try {
            val request = Request.Builder()
                .url(playlistUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "*/*")
                .header("Origin", "https://embed.ksdjugfssddeports.com")
                .header("Referer", "https://embed.ksdjugfssddeports.com/")
                .build()

            val response = client.newCall(request).execute()
            val manifestContent = response.body?.string() ?: ""

            if (manifestContent.contains("#EXTM3U")) {
                return manifestContent.replace(
                    Regex("""https://deportes\.ksdjugfssddeports\.com:9092/([^\s]+)"""),
                    "http://127.0.0.1:$localPort/segment/https://deportes.ksdjugfssddeports.com:9092/$1"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}")
        }

        return ""
    }

    private fun getInfoItem(id: String): TvShow {
        val title = if(id == "creador-info") "Reportar problemas" else "Apoya al Proveedor"
        val poster = if(id == "creador-info")
            "https://i.ibb.co/dsknGBHT/Imagen-de-Whats-App-2025-09-06-a-las-19-00-50-e8e5bcaa.jpg"
        else
            "https://i.ibb.co/B5gKLkqS/nuevo-formato-2-K-202604112205.jpg"

        return TvShow(
            id = id,
            title = title,
            poster = poster,
            banner = poster,
            overview = if(id == "creador-info") "@NandoGT" else "Apoya el proyecto.",
            providerName = name
        )
    }
}