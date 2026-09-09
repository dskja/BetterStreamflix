package com.dskja.betterstreamflix.providers

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

import com.dskja.betterstreamflix.utils.UserPreferences
import com.dskja.betterstreamflix.utils.M3uChannelIdCodec

import android.util.Base64
import android.util.Log
import com.dskja.betterstreamflix.adapters.AppAdapter
import com.dskja.betterstreamflix.models.*
import okhttp3.*
import java.util.concurrent.TimeUnit

object PelotaLibreTvHdProvider : IptvProvider, ProviderConfigUrl {

    override val name = "Sports Events"
    override val defaultBaseUrl = "https://raw.githubusercontent.com"
    override val baseUrl: String
        get() = UserPreferences.getProviderCache(this, UserPreferences.PROVIDER_URL).ifBlank { defaultBaseUrl }
    override val changeUrlMutex = Mutex()

    override suspend fun onChangeUrl(forceRefresh: Boolean): String = changeUrlMutex.withLock {
        baseUrl
    }
    override val logo = "https://i.ibb.co/3s2mhm6/sports-logo.png"
    override val language = "es"

    private const val TAG = "SportsEventsProvider"

    private const val OBFUSCATED_PLAYLIST = "aHR0cHM6Ly9yYXcuZ2l0aHVidXNlcmNvbnRlbnQuY29tL0J1ZGR5Q2hld0NoZXcvc3BvcnRzL3JlZnMvaGVhZHMvbWFpbi9saXZlZXZlbnRzZmlsdGVyLm0zdTg="

    private const val FALLBACK_VIDEO_URL = "https://raw.githubusercontent.com/NANDOFS/ModoPrueba/main/VIDEO/SIN-SE%C3%91AL.mp4"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .cookieJar(object : CookieJar {
            private val cookieStore = mutableMapOf<String, List<Cookie>>()
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                cookieStore[url.host] = cookies
            }
            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                return cookieStore[url.host] ?: listOf()
            }
        })
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.0.0 Safari/537.36")
                .build()
            chain.proceed(request)
        }
        .build()

    private var cachedChannels: List<M3UChannel>? = null
    private var lastFetchTime: Long = 0
    private const val CACHE_DURATION = 30 * 60 * 1000

    data class M3UChannel(
        val name: String,
        val url: String,
        val logo: String?,
        val group: String?,
        val userAgent: String? = null,
        val referrer: String? = null,
        val origin: String? = null,
    )

    private fun createId(channel: M3UChannel): String {
        return M3uChannelIdCodec.encode(
            url = channel.url,
            name = channel.name,
            logo = channel.logo,
            userAgent = channel.userAgent,
            referrer = channel.referrer,
            origin = channel.origin,
        )
    }

    private fun decodeId(id: String): Triple<String, String, String> {
        val payload = M3uChannelIdCodec.decode(id)
        return Triple(payload.url, payload.name, payload.logo)
    }

    private fun getAllChannels(): List<M3UChannel> {
        val now = System.currentTimeMillis()
        if (cachedChannels != null && (now - lastFetchTime) < CACHE_DURATION) return cachedChannels!!

        return try {
            val decodedUrl = String(Base64.decode(OBFUSCATED_PLAYLIST, Base64.DEFAULT))
            Log.d(TAG, "Obteniendo lista: $decodedUrl")
            val request = Request.Builder().url(decodedUrl).build()
            val body = client.newCall(request).execute().body?.string() ?: return emptyList()
            val channels = parseM3U(body)
            cachedChannels = channels
            lastFetchTime = now
            channels
        } catch (e: Exception) {
            Log.e(TAG, "Error obteniendo M3U de Sports Events: ${e.message}")
            cachedChannels ?: emptyList()
        }
    }

    override suspend fun getHome(): List<Category> {
        val channels = getAllChannels()
        val categories = mutableListOf<Category>()

        val channelCategories = channels
            .filter { it.group != null && it.group.isNotEmpty() }
            .groupBy { it.group!! }
            .map { (groupName, channelList) ->
                Category(
                    name = groupName,
                    list = channelList.distinctBy { it.name }.take(30).map { channel ->
                        TvShow(
                            id = createId(channel),
                            title = channel.name,
                            poster = channel.logo ?: "",
                            banner = channel.logo ?: "",
                        )
                    },
                )
            }.sortedBy { it.name }

        categories.addAll(channelCategories)

        val ungrouped = channels.filter { it.group.isNullOrEmpty() }
        if (ungrouped.isNotEmpty()) {
            categories.add(
                Category(
                    name = "General / Sin Categoría",
                    list = ungrouped.distinctBy { it.name }.take(30).map { channel ->
                        TvShow(
                            id = createId(channel),
                            title = channel.name,
                            poster = channel.logo ?: "",
                            banner = channel.logo ?: "",
                        )
                    },
                )
            )
        }

        return categories
    }

    override suspend fun search(query: String, page: Int): List<AppAdapter.Item> {
        if (page > 1) return emptyList()
        val allChannels = getAllChannels()
        return allChannels.filter {
            it.name.contains(query, ignoreCase = true) ||
                (it.group?.contains(query, ignoreCase = true) == true)
        }.distinctBy { it.name }.take(80).map { channel ->
            TvShow(id = createId(channel), title = channel.name, poster = channel.logo ?: "")
        }
    }

    override suspend fun getGenre(id: String, page: Int): Genre {
        val groupChannels = getAllChannels().filter {
            it.group?.contains(id, ignoreCase = true) ?: false
        }.distinctBy { it.name }
        val pagedList = groupChannels.drop((page - 1) * 40).take(40).map { channel ->
            TvShow(id = createId(channel), title = channel.name, poster = channel.logo ?: "")
        }
        return Genre(id = id, name = id, shows = pagedList)
    }

    override suspend fun getPeople(id: String, page: Int): People {
        return People(
            id = id,
            name = "Sports Events",
            image = logo,
            biography = "",
            birthday = "",
            deathday = "",
            placeOfBirth = "",
        )
    }

    override suspend fun getTvShow(id: String): TvShow {
        val (_, name, logo) = decodeId(id)
        return TvShow(
            id = id,
            title = name,
            poster = logo,
            banner = logo,
            overview = "Live sports channel: $name",
            seasons = emptyList(),
        )
    }

    override suspend fun getEpisodesBySeason(seasonId: String): List<Episode> = emptyList()

    override suspend fun getServers(id: String, videoType: Video.Type): List<Video.Server> {
        return listOf(Video.Server(id = id, name = "Sports Stream"))
    }

    override suspend fun getVideo(server: Video.Server): Video {
        val payload = M3uChannelIdCodec.decode(server.id)
        val url = payload.url
        val videoHeaders = M3uChannelIdCodec.playbackHeaders(server.id).toMutableMap()

        Log.d(TAG, "Solicitando: $url headers=${videoHeaders.keys}")

        return try {
            val checkRequest = Request.Builder()
                .url(url)
                .apply { videoHeaders.forEach { (k, v) -> addHeader(k, v) } }
                .build()

            val response = client.newCall(checkRequest).execute()
            var isAlive = response.isSuccessful

            if (isAlive) {
                val contentType = response.header("Content-Type") ?: ""
                if (contentType.contains("text/html", ignoreCase = true)) {
                    isAlive = false
                    Log.e(TAG, "Servidor devolvió HTML en lugar de video")
                } else if (url.contains(".mpd") || url.contains(".m3u8")) {
                    val peekBody = response.peekBody(15360).string()
                    if (url.contains(".mpd")) {
                        if (!peekBody.contains("<MPD", ignoreCase = true)) {
                            isAlive = false
                        } else if (
                            peekBody.contains("ContentProtection", ignoreCase = true) ||
                            peekBody.contains("cenc:pssh", ignoreCase = true)
                        ) {
                            isAlive = false
                            Log.e(TAG, "MPD DRM detectado")
                        }
                    } else if (url.contains(".m3u8") && !peekBody.contains("#EXTM3U", ignoreCase = true)) {
                        isAlive = false
                    }
                }
            }
            response.close()

            if (isAlive) {
                Video(
                    source = url,
                    subtitles = emptyList(),
                    headers = videoHeaders.takeIf { it.isNotEmpty() },
                )
            } else if (url.contains(".m3u8", ignoreCase = true) && videoHeaders.isNotEmpty()) {
                // Live HLS probes race with rotating segments / geo. Prefer real
                // playback with Referer/Origin over the fake "no signal" clip.
                Log.w(TAG, "Probe failed; attempting live HLS with headers anyway")
                Video(
                    source = url,
                    subtitles = emptyList(),
                    headers = videoHeaders,
                )
            } else {
                Log.e(TAG, "Canal muerto — fallback")
                Video(source = FALLBACK_VIDEO_URL, subtitles = emptyList())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error red: ${e.message}")
            Video(source = FALLBACK_VIDEO_URL, subtitles = emptyList())
        }
    }

    private fun parseM3U(m3uRaw: String): List<M3UChannel> {
        val channels = mutableListOf<M3UChannel>()
        var curName = ""
        var curLogo = ""
        var curGroup = ""
        var curUA: String? = null
        var curRef: String? = null
        var curOrigin: String? = null

        for (line in m3uRaw.lines()) {
            val t = line.trim()
            if (t.startsWith("#EXTINF")) {
                curName = t.substringAfterLast(",").trim()
                curLogo = Regex("""tvg-logo="([^"]+)"""").find(t)?.groupValues?.get(1) ?: ""
                curGroup = Regex("""group-title="([^"]+)"""").find(t)?.groupValues?.get(1) ?: ""
                curUA = Regex("""http-user-agent="([^"]+)"""").find(t)?.groupValues?.get(1)
                curRef = Regex("""http-referrer="([^"]+)"""").find(t)?.groupValues?.get(1)
                curOrigin = Regex("""http-origin="([^"]+)"""").find(t)?.groupValues?.get(1)
            } else if (t.startsWith("#EXTVLCOPT:")) {
                when {
                    t.contains("http-user-agent=") ->
                        curUA = t.substringAfter("http-user-agent=").trim()
                    t.contains("http-referrer=") ->
                        curRef = t.substringAfter("http-referrer=").trim()
                    t.contains("http-origin=") ->
                        curOrigin = t.substringAfter("http-origin=").trim()
                }
            } else if (t.startsWith("http")) {
                if (curName.isNotEmpty()) {
                    channels.add(
                        M3UChannel(curName, t, curLogo, curGroup, curUA, curRef, curOrigin),
                    )
                    curName = ""
                    curLogo = ""
                    curGroup = ""
                    curUA = null
                    curRef = null
                    curOrigin = null
                }
            }
        }
        return channels
    }

    override suspend fun getMovies(page: Int): List<Movie> = emptyList()

    override suspend fun getTvShows(page: Int): List<TvShow> {
        val channels = getAllChannels()
        val start = (page - 1) * 50
        if (start >= channels.size) return emptyList()
        return channels.drop(start).take(50).map { channel ->
            TvShow(id = createId(channel), title = channel.name, poster = channel.logo ?: "")
        }
    }

    override suspend fun getMovie(id: String): Movie = Movie(id = id, title = "Live", poster = "")
}
