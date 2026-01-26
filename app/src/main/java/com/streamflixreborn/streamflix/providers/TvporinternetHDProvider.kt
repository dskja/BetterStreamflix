package com.streamflixreborn.streamflix.providers

import android.util.Base64
import android.util.Log
import com.streamflixreborn.streamflix.models.*
import com.streamflixreborn.streamflix.models.cablevisionhd.toTvShows
import com.tanasi.retrofit_jsoup.converter.JsoupConverterFactory
import kotlinx.coroutines.Deferred
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
import com.streamflixreborn.streamflix.utils.JsUnpacker

object TvporinternetHDProvider : Provider {

    override val name = "TvporinternetHD"
    override val baseUrl = "https://www.tvporinternet2.com"
    override val logo = "https://i.ibb.co/yndhPSyq/imagen-2026-01-25-210504580.png"
    override val language = "es"

    private const val TAG = "TvporinternetHDProvider"
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.0.0 Safari/537.36"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        // Implementación de CookieJar interna (Estilo AnimeOnlineLat)
        .cookieJar(object : CookieJar {
            private val cookieStore = HashMap<String, List<Cookie>>()
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                cookieStore[url.host] = cookies
            }
            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                return cookieStore[url.host] ?: ArrayList()
            }
        })
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", USER_AGENT)
                .build()
            chain.proceed(request)
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
            @Header("Referer") referer: String = baseUrl
        ): Document
    }

    override suspend fun getHome(): List<Category> = coroutineScope {
        val categories = mutableListOf<Deferred<Category>>()
        try {
            val document = service.getPage(baseUrl)

            // Verificación básica de que cargó el sitio
            if (document.select("div.channels").isEmpty()) {
                Log.e(TAG, "No se encontraron canales en el home. Posible bloqueo o cambio de estructura.")
            }

            val allShows = document.toTvShows()

            // 1. Todos los canales
            categories.add(async {
                Category(name = "Todos los Canales", list = allShows)
            })

            // 2. Deportes
            categories.add(async {
                Category(
                    name = "Deportes",
                    list = allShows.filter {
                        it.title.contains("sport", ignoreCase = true) ||
                                it.title.contains("espn", ignoreCase = true) ||
                                it.title.contains("fox", ignoreCase = true)
                    }
                )
            })

            // 3. Noticias
            categories.add(async {
                Category(
                    name = "Noticias",
                    list = allShows.filter {
                        it.title.contains("news", ignoreCase = true) ||
                                it.title.contains("noticias", ignoreCase = true) ||
                                it.title.contains("cnn", ignoreCase = true)
                    }
                )
            })

            // 4. Cine y Entretenimiento
            categories.add(async {
                Category(
                    name = "Cine y Series",
                    list = allShows.filter {
                        it.title.contains("hbo", ignoreCase = true) ||
                                it.title.contains("max", ignoreCase = true) ||
                                it.title.contains("cine", ignoreCase = true) ||
                                it.title.contains("warner", ignoreCase = true) ||
                                it.title.contains("star", ignoreCase = true)
                    }
                )
            })

            // 5. Info Especial
            categories.add(async {
                val creador = TvShow(
                    id = "creador-info",
                    title = "Reportar problemas",
                    poster = "https://i.ibb.co/dsknGBHT/Imagen-de-Whats-App-2025-09-06-a-las-19-00-50-e8e5bcaa.jpg"
                )
                val apoyo = TvShow(
                    id = "apoyo-info",
                    title = "Apoya al Proveedor",
                    poster = "https://i.ibb.co/GQypMpS7/aPOYA.png"
                )
                val donate = TvShow(
                    id = "apoyo-info",
                    title = "Apoya al Proveedor",
                    poster = "https://i.ibb.co/C3LsHmth/buy-me-a-Coffe.png"
                )
                Category(
                    name = "Información",
                    list = listOf(creador, apoyo, donate)
                )
            })

            categories.awaitAll().filter { it.list.isNotEmpty() }

        } catch (e: Exception) {
            Log.e(TAG, "Error en getHome: ${e.message}")
            emptyList()
        }
    }

    override suspend fun search(query: String, page: Int): List<Show> {
        return try {
            // CableVision carga todo en el home, filtramos localmente
            val document = service.getPage(baseUrl)
            val allShows = document.toTvShows()
            allShows.filter { it.title.contains(query, ignoreCase = true) }
        } catch (e: Exception) {
            Log.e(TAG, "Error en search: ", e)
            emptyList()
        }
    }

    override suspend fun getMovies(page: Int): List<Movie> = emptyList()

    override suspend fun getTvShows(page: Int): List<TvShow> {
        if (page > 1) return emptyList()
        return try {
            val document = service.getPage(baseUrl)
            document.toTvShows()
        } catch (e: Exception) {
            Log.e(TAG, "Error en getTvShows: ", e)
            emptyList()
        }
    }

    override suspend fun getMovie(id: String): Movie = throw NotImplementedError()

    override suspend fun getTvShow(id: String): TvShow {
        if (id == "creador-info" || id == "apoyo-info") return getInfoItem(id)

        return try {
            val fullUrl = if (id.startsWith("http")) id else "$baseUrl/$id"
            val document = service.getPage(fullUrl)

            val title = document.selectFirst("div.card-body h2")?.text()
                ?: document.selectFirst("h1")?.text()
                ?: "Canal en Vivo"

            val poster = document.selectFirst("div.card-body img")?.attr("src")
            val overview = document.selectFirst("div.card-body p")?.text() ?: "Transmisión en directo"

            // Ajustar URL del poster
            val finalPoster = poster?.let { if (!it.startsWith("http")) "$baseUrl/$it" else it }

            val season = Season(id = id, number = 1, title = "En Vivo", episodes = listOf(
                Episode(id = id, number = 1, title = "Señal en Directo", poster = finalPoster)
            ))

            TvShow(
                id = id,
                title = title,
                overview = overview,
                poster = finalPoster,
                banner = finalPoster,
                seasons = listOf(season)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error en getTvShow: ", e)
            TvShow(id = id, title = "Error de carga")
        }
    }

    override suspend fun getEpisodesBySeason(seasonId: String): List<Episode> {
        // Retornamos un episodio dummy que apunta al mismo canal
        return listOf(Episode(id = seasonId, number = 1, title = "Señal en Directo"))
    }

    override suspend fun getGenre(id: String, page: Int): Genre = throw NotImplementedError()
    override suspend fun getPeople(id: String, page: Int): People = throw NotImplementedError()

    override suspend fun getServers(id: String, videoType: Video.Type): List<Video.Server> {
        return try {
            val fullUrl = if (id.startsWith("http")) id else "$baseUrl/$id"
            val document = service.getPage(fullUrl)

            val servers = mutableListOf<Video.Server>()
            val serverElements = document.select("a.btn.btn-md[target=iframe]")

            serverElements.forEach {
                servers.add(Video.Server(
                    id = it.attr("href"), // URL intermedia
                    name = it.text().ifEmpty { "Opción Principal" }
                ))
            }

            // Fallback si no hay botones pero hay iframe directo
            if (servers.isEmpty() && document.select("iframe").isNotEmpty()) {
                servers.add(Video.Server(id = fullUrl, name = "Directo"))
            }

            servers
        } catch (e: Exception) {
            Log.e(TAG, "Error en getServers: ", e)
            emptyList()
        }
    }

    override suspend fun getVideo(server: Video.Server): Video {
        // Variables para el bucle de "taladro"
        var currentUrl = if (server.name == "Directo") server.id else server.id
        var currentReferer = if (server.name == "Directo") baseUrl else baseUrl

        Log.d(TAG, "🚀 Iniciando extracción recursiva en: $currentUrl")

        var depth = 0
        val maxDepth = 4 // Límite de seguridad para no caer en bucles infinitos

        while (depth < maxDepth) {
            depth++
            try {
                Log.d(TAG, "➡️ Nivel $depth: Analizando $currentUrl (Ref: $currentReferer)")

                val document = service.getPage(currentUrl, referer = currentReferer)
                val html = document.html()

                // --- FASE 1: BUSCAR VIDEO EN EL NIVEL ACTUAL ---

                // A. M3U8 Directo
                val m3u8Regex = Regex("""["'](https?://[^"']+\.m3u8[^"']*)["']""")
                m3u8Regex.find(html)?.let {
                    val url = it.groupValues[1].replace("\\/", "/") // Limpieza importante
                    Log.d(TAG, "✅ ¡Encontrado M3U8 directo!: $url")
                    return Video(source = url, headers = mapOf("Referer" to currentUrl, "User-Agent" to USER_AGENT))
                }

                // B. Script Packer (Desempaquetado)
                val packedRegex = Regex("""eval\(function\(p,a,c,k,e,d\).*?\)""")
                packedRegex.findAll(html).forEach { match ->
                    try {
                        val unpacked = JsUnpacker(match.value).unpack() ?: ""

                        // Buscar URLs dentro del código desempaquetado
                        val urlMatch = m3u8Regex.find(unpacked)
                            ?: Regex("""file\s*:\s*["']([^"']+)["']""").find(unpacked)
                            ?: Regex("""["'](https?://[^"']+\.mp4[^"']*)["']""").find(unpacked)

                        urlMatch?.let {
                            val url = it.groupValues[1].replace("\\/", "/")
                            if (url.startsWith("http")) {
                                Log.d(TAG, "✅ Video en script desempaquetado: $url")
                                return Video(source = url, headers = mapOf("Referer" to currentUrl, "User-Agent" to USER_AGENT))
                            }
                        }
                    } catch (e: Exception) { Log.w(TAG, "Error unpack: ${e.message}") }
                }

                // C. Triple Base64 (Clásico de CableVision)
                if (html.contains("const decodedURL")) {
                    document.select("script").forEach {
                        if (it.data().contains("const decodedURL")) {
                            val encodedUrl = it.data().substringAfter("atob(\"").substringBefore("\"))))")
                            try {
                                val decoded = String(Base64.decode(String(Base64.decode(String(Base64.decode(encodedUrl, Base64.DEFAULT)), Base64.DEFAULT)), Base64.DEFAULT))
                                Log.d(TAG, "✅ Triple Base64 decodificado: $decoded")
                                return Video(source = decoded, headers = mapOf("Referer" to currentUrl, "User-Agent" to USER_AGENT))
                            } catch (e: Exception) {}
                        }
                    }
                }

                // D. Patrones Genéricos (Clappr, JWPlayer, var src)
                val sourceRegexes = listOf(
                    Regex("""source\s*:\s*["']([^"']+)["']"""),
                    Regex("""file\s*:\s*["']([^"']+)["']"""),
                    Regex("""var\s+src\s*=\s*["']([^"']+)["']"""),
                    Regex("""["'](https?://[^"']+\.mp4[^"']*)["']""")
                )
                for (regex in sourceRegexes) {
                    regex.find(html)?.let {
                        // AQUÍ ESTÁ EL FIX FINAL: .replace("\\/", "/")
                        val url = it.groupValues[1].replace("\\/", "/")

                        if (url.startsWith("http")) {
                            Log.d(TAG, "✅ Patrón genérico encontrado y limpiado: $url")
                            return Video(source = url, headers = mapOf("Referer" to currentUrl, "User-Agent" to USER_AGENT))
                        }
                    }
                }

                // --- FASE 2: SI NO HAY VIDEO, BUSCAR IFRAME PARA PROFUNDIZAR ---
                val iframeSrc = document.select("iframe").attr("src")
                if (iframeSrc.isNotEmpty()) {
                    val nextUrl = if (iframeSrc.startsWith("http")) iframeSrc else "$baseUrl/$iframeSrc"

                    // Evitar recargar la misma página (bucle simple)
                    if (nextUrl == currentUrl) {
                        Log.w(TAG, "⚠️ Iframe apunta a sí mismo. Abortando ciclo.")
                        break
                    }

                    Log.d(TAG, "⛏️ No hay video, pero hay iframe. Profundizando a: $nextUrl")

                    // Actualizamos para la siguiente vuelta del while
                    currentReferer = currentUrl
                    currentUrl = nextUrl
                    continue // Vuelve al inicio del while con la nueva URL
                }

                // Si llegamos aquí: No hay video Y no hay iframes. Fin del camino.
                Log.e(TAG, "❌ Fin del camino en nivel $depth. No se halló video ni más iframes.")
                break

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error crítico en nivel $depth: ${e.message}")
                break
            }
        }

        return Video(source = "", subtitles = emptyList())
    }
    // Auxiliar para items de información
    private fun getInfoItem(id: String): TvShow {
        val title = if(id == "creador-info") "Reportar problemas" else "Apoya al Proveedor"
        val poster = if(id == "creador-info") "https://i.ibb.co/dsknGBHT/Imagen-de-Whats-App-2025-09-06-a-las-19-00-50-e8e5bcaa.jpg" else "https://i.ibb.co/B234HsZg/APOYO-NANDO.png"
        val desc = if(id == "creador-info") "Reporta errores a @NandoGT o @Nandofs." else "Escanea el QR para apoyar el proyecto."
        return TvShow(id = id, title = title, poster = poster, banner = poster, overview = desc, seasons = emptyList())
    }
}