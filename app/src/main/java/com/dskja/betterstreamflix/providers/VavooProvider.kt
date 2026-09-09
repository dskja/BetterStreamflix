package com.dskja.betterstreamflix.providers

import android.util.Log
import com.dskja.betterstreamflix.adapters.AppAdapter
import com.dskja.betterstreamflix.models.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class VavooProvider(override val language: String) : IptvProvider {

    companion object {
        private const val TAG = "VavooProvider"
        private const val CACHE_DURATION = 30 * 60 * 1000L
        private const val POSTER =
            "https://www.clipartmax.com/png/full/46-463028_television-images-clip-art.png"
        private const val CLIENT_VERSION = "3.0.2"
        private const val PING_URL = "https://www.vavoo.tv/api/app/ping"

        // Prefer the configured host first; kool.to is a known MediaHub fallback mirror.
        private val BASE_URLS = listOf("https://vavoo.to", "https://kool.to")

        private val LANG_CONFIG = mapOf(
            "de" to Triple("de", "DE", listOf("Germany", "GERMANY")),
            "it" to Triple("it", "IT", listOf("Italy")),
            "fr" to Triple("fr", "FR", listOf("France", "France Sport")),
            "es" to Triple("es", "ES", listOf("Spain")),
            "pl" to Triple("pl", "PL", listOf("Poland"))
        )

        private val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        private val sharedSignature = AtomicReference<String?>(null)

        private fun invalidateSignature() {
            sharedSignature.set(null)
        }

        private fun obtainSignature(forceRefresh: Boolean = false): String? {
            if (!forceRefresh) {
                sharedSignature.get()?.let { return it }
            }
            val now = System.currentTimeMillis()
            val uniqueId = UUID.randomUUID().toString().replace("-", "").take(16)
            val body = JSONObject().apply {
                put("token", "")
                put("reason", "app-focus")
                put("locale", "de")
                put("theme", "dark")
                put("metadata", JSONObject().apply {
                    put("device", JSONObject().apply {
                        put("type", "Handset")
                        put("brand", "google")
                        put("model", "Nexus")
                        put("name", "21081111RG")
                        put("uniqueId", uniqueId)
                    })
                    put("os", JSONObject().apply {
                        put("name", "android")
                        put("version", "7.1.2")
                        put("abis", JSONArray(listOf("arm64-v8a", "armeabi-v7a", "armeabi")))
                        put("host", "android")
                    })
                    put("app", JSONObject().apply {
                        put("platform", "android")
                        put("version", "3.1.20")
                        put("buildId", "289515000")
                        put("engine", "hbc85")
                        put(
                            "signatures",
                            JSONArray(
                                listOf("6e8a975e3cbf07d5de823a760d4c2547f86c1403105020adee5de67ac510999e")
                            )
                        )
                        put("installer", "com.android.vending")
                    })
                    put("version", JSONObject().apply {
                        put("package", "tv.vavoo.app")
                        put("binary", "3.1.20")
                        put("js", "3.1.20")
                    })
                })
                put("appFocusTime", 0)
                put("playerActive", false)
                put("playDuration", 0)
                put("devMode", false)
                put("hasAddon", true)
                put("castConnected", false)
                put("package", "tv.vavoo.app")
                put("version", "3.1.20")
                put("process", "app")
                put("firstAppStart", now)
                put("lastAppStart", now)
                put("ipLocation", JSONObject.NULL)
                put("adblockEnabled", true)
                put("proxy", JSONObject().apply {
                    put("supported", JSONArray(listOf("ss", "openvpn")))
                    put("engine", "ss")
                    put("ssVersion", 1)
                    put("enabled", false)
                    put("autoServer", true)
                })
                put("iap", JSONObject().apply { put("supported", false) })
            }.toString()

            return try {
                val request = Request.Builder()
                    .url(PING_URL)
                    .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .header("User-Agent", "okhttp/4.11.0")
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json; charset=utf-8")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e(TAG, "Signature ping failed: HTTP ${response.code}")
                        return null
                    }
                    val sig = JSONObject(response.body?.string().orEmpty())
                        .optString("addonSig")
                        .takeIf { it.isNotEmpty() }
                    if (sig != null) {
                        sharedSignature.set(sig)
                    }
                    sig
                }
            } catch (e: Exception) {
                Log.e(TAG, "Signature ping error: ${e.message}")
                null
            }
        }
    }

    override val baseUrl: String = BASE_URLS.first()

    private val homeCache = mutableMapOf<String, List<VavooChannel>>()
    private val cacheTimestamps = mutableMapOf<String, Long>()
    private val channelCache = java.util.concurrent.ConcurrentHashMap<String, VavooChannel>()

    data class VavooChannel(
        val id: String,
        val name: String,
        val url: String
    )

    private val config = LANG_CONFIG[language] ?: LANG_CONFIG["de"]!!
    private val apiLanguage: String = config.first
    private val apiRegion: String = config.second

    override val name: String = "Vavoo ${config.third.first()} Live TV"
    override val logo: String = "$baseUrl/assets/favicon-Djqjt9PL.ico"

    private val primaryGroups: List<String> = config.third

    private fun catalogUrl(base: String) = "$base/mediahubmx-catalog.json"
    private fun resolveEndpoints(base: String) = listOf(
        "$base/mediahubmx-resolve.json",
        "$base/mediaurl-resolve.json"
    )

    private fun remember(channels: List<VavooChannel>) {
        channels.forEach { channelCache[it.id] = it }
    }

    private fun fetchChannels(
        search: String,
        group: String,
        cursor: Int? = null
    ): Pair<List<VavooChannel>, Int?> {
        val filterObj = JSONObject().apply { put("group", group) }
        val body = JSONObject().apply {
            put("language", apiLanguage)
            put("region", apiRegion)
            put("catalogId", "iptv")
            put("id", "iptv")
            put("adult", false)
            put("search", search)
            put("sort", "name")
            put("filter", filterObj)
            put("clientVersion", CLIENT_VERSION)
            if (cursor != null) put("cursor", cursor) else put("cursor", JSONObject.NULL)
        }.toString()

        for (base in BASE_URLS) {
            val result = fetchCatalogOnce(base, body, signed = false)
                ?: fetchCatalogOnce(base, body, signed = true)
            if (result != null) {
                remember(result.first)
                return result
            }
        }
        Log.e(TAG, "Error fetching channels (search='$search', group='$group')")
        return Pair(emptyList(), null)
    }

    private fun fetchCatalogOnce(
        base: String,
        body: String,
        signed: Boolean
    ): Pair<List<VavooChannel>, Int?>? {
        val signature = if (signed) obtainSignature() ?: return null else null
        return try {
            val builder = Request.Builder()
                .url(catalogUrl(base))
                .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .header("User-Agent", if (signed) "MediaHubMX/2" else "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Accept", "application/json")
                .header("Origin", base)
                .header("Referer", "$base/")
            if (signature != null) {
                builder.header("mediahubmx-signature", signature)
                builder.header("mediaurl-signature", signature)
            }
            client.newCall(builder.build()).execute().use { response ->
                if (shouldRefreshSignature(response.code)) {
                    invalidateSignature()
                    return null
                }
                if (!response.isSuccessful) return null
                val json = JSONObject(response.body?.string() ?: return null)
                val items = json.optJSONArray("items") ?: return null
                val nextCursor = if (json.isNull("nextCursor")) null else json.optInt("nextCursor")
                val channels = (0 until items.length()).mapNotNull { i ->
                    val item = items.getJSONObject(i)
                    val url = item.optString("url").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                    val name = item.optString("name").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                    VavooChannel(
                        id = item.optJSONObject("ids")?.optString("id")?.takeIf { it.isNotEmpty() }
                            ?: url.substringAfterLast('/'),
                        name = name,
                        url = url
                    )
                }
                if (channels.isEmpty()) null else Pair(channels, nextCursor)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Catalog request failed on $base (signed=$signed): ${e.message}")
            null
        }
    }

    private fun loadHomeGroupChannels(group: String): List<VavooChannel> {
        val now = System.currentTimeMillis()
        val cached = homeCache[group]
        if (cached != null && (now - (cacheTimestamps[group] ?: 0)) < CACHE_DURATION) {
            return cached
        }

        // Paginate the catalog so Poland/other large groups are not truncated to the first page.
        val all = linkedMapOf<String, VavooChannel>()
        var cursor: Int? = null
        var pages = 0
        while (pages < 20) {
            val (pageChannels, nextCursor) = fetchChannels("", group, cursor)
            if (pageChannels.isEmpty()) break
            pageChannels.forEach { all[it.id] = it }
            pages++
            cursor = nextCursor ?: break
        }

        val channels = all.values.toList()
        if (channels.isNotEmpty()) {
            homeCache[group] = channels
            cacheTimestamps[group] = now
        }
        return channels
    }

    data class ResolvedChannel(val name: String, val url: String)

    private fun shouldRefreshSignature(code: Int): Boolean =
        code == 401 || code == 403 || code == 451

    private fun parseResolvedPayload(payload: String): ResolvedChannel? {
        val trimmed = payload.trim()
        if (trimmed.isEmpty()) return null
        return try {
            when {
                trimmed.startsWith("[") -> {
                    val array = JSONArray(trimmed)
                    if (array.length() == 0) return null
                    val obj = array.getJSONObject(0)
                    val url = obj.optString("url").ifEmpty { obj.optString("streamUrl") }
                        .takeIf { it.isNotEmpty() } ?: return null
                    ResolvedChannel(name = obj.optString("name"), url = url)
                }
                trimmed.startsWith("{") -> {
                    val obj = JSONObject(trimmed)
                    val nested = obj.optJSONObject("data")
                    val url = obj.optString("url")
                        .ifEmpty { obj.optString("streamUrl") }
                        .ifEmpty { nested?.optString("url").orEmpty() }
                        .takeIf { it.isNotEmpty() } ?: return null
                    ResolvedChannel(name = obj.optString("name"), url = url)
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun resolveChannel(channelUrl: String): ResolvedChannel? {
        // 1) Keep the old unsigned MediaHubMX resolve as the fast path.
        resolveUnsigned(channelUrl)?.let { return it }

        // 2) Fall back to the current signed MediaHub / MediaUrl session flow.
        resolveSigned(channelUrl, allowRetry = true)?.let { return it }

        return null
    }

    private fun resolveUnsigned(channelUrl: String): ResolvedChannel? {
        val body = JSONObject().apply {
            put("language", apiLanguage)
            put("region", apiRegion)
            put("url", channelUrl)
        }.toString()

        for (base in resolveBasesFor(channelUrl)) {
            for (endpoint in resolveEndpoints(base)) {
                try {
                    val request = Request.Builder()
                        .url(endpoint)
                        .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                        .header("Accept", "application/json")
                        .header("Origin", base)
                        .header("Referer", "$base/")
                        .build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@use
                        parseResolvedPayload(response.body?.string().orEmpty())?.let { return it }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Unsigned resolve failed on $endpoint: ${e.message}")
                }
            }
        }
        return null
    }

    private fun resolveSigned(channelUrl: String, allowRetry: Boolean): ResolvedChannel? {
        var signature = obtainSignature() ?: return null
        val body = JSONObject().apply {
            put("language", apiLanguage)
            put("region", apiRegion)
            put("url", channelUrl)
            put("clientVersion", CLIENT_VERSION)
        }.toString()

        for (base in resolveBasesFor(channelUrl)) {
            for (endpoint in resolveEndpoints(base)) {
                try {
                    val request = Request.Builder()
                        .url(endpoint)
                        .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
                        .header("User-Agent", "MediaHubMX/2")
                        .header("Accept", "application/json")
                        .header("Content-Type", "application/json; charset=utf-8")
                        .header("Origin", base)
                        .header("Referer", "$base/")
                        .header("mediahubmx-signature", signature)
                        .header("mediaurl-signature", signature)
                        .build()
                    client.newCall(request).execute().use { response ->
                        if (shouldRefreshSignature(response.code)) {
                            invalidateSignature()
                            if (allowRetry) {
                                signature = obtainSignature(forceRefresh = true) ?: return null
                                return resolveSigned(channelUrl, allowRetry = false)
                            }
                            return null
                        }
                        if (!response.isSuccessful) return@use
                        parseResolvedPayload(response.body?.string().orEmpty())?.let { return it }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Signed resolve failed on $endpoint: ${e.message}")
                }
            }
        }
        return null
    }

    private fun resolveBasesFor(channelUrl: String): List<String> {
        val hostBase = runCatching {
            val host = java.net.URI(channelUrl).host ?: return@runCatching null
            "https://$host"
        }.getOrNull()
        return (listOfNotNull(hostBase) + BASE_URLS).distinct()
    }

    private fun channelUrlFor(idOrUrl: String): String =
        candidateChannelUrls(idOrUrl).first()

    private fun candidateChannelUrls(idOrUrl: String): List<String> {
        if (idOrUrl.startsWith("http")) return listOf(idOrUrl)

        channelCache[idOrUrl]?.url?.let { return listOf(it) }
        for (group in primaryGroups) {
            homeCache[group]?.find { it.id == idOrUrl }?.url?.let { return listOf(it) }
        }

        // Last-resort reconstruction when caches were cleared (e.g. process death).
        // Try both known MediaHub hosts and brand path styles.
        return BASE_URLS.flatMap { base ->
            val brand = base.removePrefix("https://").removePrefix("http://").substringBefore('.')
            listOf(
                "$base/$brand-iptv/play/$idOrUrl",
                "$base/vavoo-iptv/play/$idOrUrl",
                "$base/kool-iptv/play/$idOrUrl"
            )
        }.distinct()
    }

    override suspend fun getHome(): List<Category> {
        return primaryGroups.map { group ->
            val channels = loadHomeGroupChannels(group)
            Category(
                name = "Vavoo $group Live TV",
                list = channels.take(300).map { ch ->
                    TvShow(id = ch.id, title = ch.name, poster = POSTER, banner = POSTER)
                }
            )
        }
    }

    override suspend fun search(query: String, page: Int): List<AppAdapter.Item> {
        val cursor = if (page > 1) (page - 1) * 300 else null
        val channels = primaryGroups.flatMap { group -> fetchChannels(query, group, cursor).first }
        return channels.map { ch ->
            TvShow(id = ch.id, title = ch.name, poster = POSTER)
        }
    }

    override suspend fun getMovies(page: Int): List<Movie> = emptyList()

    override suspend fun getTvShows(page: Int): List<TvShow> {
        val cursor = if (page > 1) (page - 1) * 300 else null
        val channels = primaryGroups.flatMap { group -> fetchChannels("", group, cursor).first }
        return channels.map { ch ->
            TvShow(id = ch.id, title = ch.name, poster = POSTER)
        }
    }

    override suspend fun getMovie(id: String): Movie = Movie(id = id, title = "Live", poster = "")

    override suspend fun getTvShow(id: String): TvShow {
        val cached = channelCache[id]
            ?: primaryGroups.firstNotNullOfOrNull { group ->
                homeCache[group]?.find { it.id == id }
            }

        val title = cached?.name ?: run {
            val resolved = resolveChannel(channelUrlFor(id))
            if (resolved != null && resolved.name.isNotEmpty()) {
                channelCache[id] = VavooChannel(
                    id = id,
                    name = resolved.name,
                    url = channelUrlFor(id)
                )
                resolved.name
            } else {
                id
            }
        }

        return TvShow(
            id = id,
            title = title,
            poster = POSTER,
            banner = POSTER,
            overview = "Vavoo Live IPTV Stream",
            seasons = listOf(Season(id = id, number = 1, title = "Watch"))
        )
    }

    override suspend fun getEpisodesBySeason(seasonId: String): List<Episode> {
        return listOf(Episode(id = seasonId, number = 1, title = "Watch Now", season = null))
    }

    override suspend fun getGenre(id: String, page: Int): Genre {
        val cursor = if (page > 1) (page - 1) * 300 else null
        val channels = primaryGroups.flatMap { group -> fetchChannels(id, group, cursor).first }
        val tvShows = channels.map { ch ->
            TvShow(id = ch.id, title = ch.name, poster = POSTER)
        }
        return Genre(id = id, name = id, shows = tvShows)
    }

    override suspend fun getPeople(id: String, page: Int): People {
        return People(
            id = id,
            name = "Vavoo",
            image = logo,
            biography = "",
            birthday = "",
            deathday = "",
            placeOfBirth = ""
        )
    }

    override suspend fun getServers(id: String, videoType: Video.Type): List<Video.Server> {
        val channelUrl = channelUrlFor(id)
        // Keep the exact catalog URL on the server so playback never rebuilds /play/<id>.
        return listOf(
            Video.Server(
                id = id,
                name = "Vavoo",
                src = channelUrl
            )
        )
    }

    override suspend fun getVideo(server: Video.Server): Video {
        val candidates = if (server.src.isNotBlank()) {
            listOf(server.src)
        } else {
            candidateChannelUrls(server.id)
        }

        var lastError: String? = null
        for (channelUrl in candidates) {
            Log.d(TAG, "[$language] Resolving: $channelUrl")
            // Always resolve immediately before playback; never reuse resolved HLS URLs.
            val resolved = resolveChannel(channelUrl)
            if (resolved != null) {
                // Refresh catalog URL mapping for subsequent playbacks.
                if (!server.id.startsWith("http")) {
                    channelCache[server.id] = VavooChannel(
                        id = server.id,
                        name = resolved.name.ifBlank {
                            channelCache[server.id]?.name ?: server.id
                        },
                        url = channelUrl
                    )
                }
                Log.d(TAG, "[$language] Playing: ${resolved.url}")
                // Vavoo CDN hosts frequently present expired TLS certs; ExoPlayer's
                // default OkHttp client rejects them. Prefer plaintext HTTP for playback.
                val playUrl = if (resolved.url.startsWith("https://", ignoreCase = true)) {
                    "http://" + resolved.url.removePrefix("https://").removePrefix("HTTPS://")
                } else {
                    resolved.url
                }
                return Video(
                    source = playUrl,
                    subtitles = emptyList(),
                    headers = mapOf(
                        "User-Agent" to "VAVOO/2.6",
                        "Referer" to "$baseUrl/",
                        "Origin" to baseUrl
                    )
                )
            }
            lastError = channelUrl
        }

        throw Exception("Vavoo: could not resolve stream URL for ${lastError ?: server.id}")
    }
}
