package com.dskja.betterstreamflix.player

import android.net.Uri
import android.webkit.CookieManager
import com.dskja.betterstreamflix.models.Video
import com.dskja.betterstreamflix.providers.SerienStreamProvider
import com.dskja.betterstreamflix.providers.TmdbProvider
import com.dskja.betterstreamflix.utils.UserPreferences

/** Shared SerienStream / CF bypass helpers used by mobile WebView and TV QR paths. */
object SerienStreamBypassHelper {

    private const val TMDB_DE_SERIENSTREAM = "tmdbde:serienstream:"

    fun isSerienStreamHost(url: String): Boolean {
        if (url.startsWith(TMDB_DE_SERIENSTREAM, ignoreCase = true)) return true
        if (url.contains("tmdbde:serienstream:", ignoreCase = true)) return true
        return SerienStreamProvider.isSerienStreamHost(url)
    }

    /**
     * Build a SerienStream episode page URL for CF bypass.
     * Works when the current provider is SerienStream OR when TMDb DE routed a
     * `tmdbde:serienstream:…` server (title search bridge).
     */
    fun buildEpisodeBypassUrl(
        videoType: Video.Type,
        servers: List<Video.Server> = emptyList(),
    ): String? {
        val base = SerienStreamProvider.baseUrl.trimEnd('/') + "/"

        // Prefer episode path embedded in a TMDb→SerienStream routed server id
        val routed = servers.firstOrNull {
            it.id.startsWith(TMDB_DE_SERIENSTREAM, ignoreCase = true) ||
                it.src.contains("serienstream", ignoreCase = true) ||
                SerienStreamProvider.isSerienStreamHost(it.src) ||
                SerienStreamProvider.isSerienStreamHost(it.id)
        }
        if (routed != null) {
            val episodePath = when {
                routed.id.startsWith(TMDB_DE_SERIENSTREAM, ignoreCase = true) ->
                    routed.id.removePrefix(TMDB_DE_SERIENSTREAM).removePrefix("tmdbde:serienstream:")
                else -> null
            }
            if (!episodePath.isNullOrBlank() && !episodePath.startsWith("http", ignoreCase = true)) {
                return "${base}serie/${episodePath.trimStart('/')}"
            }
            // Fall through to src-based host pages when id isn't a site path
            val src = routed.src
            if (SerienStreamProvider.isSerienStreamHost(src) && src.contains("/serie/")) {
                return src.substringBefore('?')
            }
        }

        val provider = UserPreferences.currentProvider
        val allowed = provider == SerienStreamProvider ||
            (provider is TmdbProvider && provider.language.lowercase().startsWith("de"))
        if (!allowed) return null

        val episodeId = when (videoType) {
            is Video.Type.Episode -> videoType.id
            is Video.Type.Movie -> return null
        }
        // TMDb episode ids are numeric — only useful when SerienStream is current provider
        if (provider != SerienStreamProvider) return null
        return "${base}serie/$episodeId"
    }

    fun applyCookies(url: String, cookieHeader: String) {
        if (cookieHeader.isBlank()) return
        val host = runCatching { Uri.parse(url).host.orEmpty() }.getOrDefault("")
        val targets = linkedSetOf<String>().apply {
            add(url)
            if (host.isNotBlank()) {
                add("https://$host/")
                add("http://$host/")
                // Also seed common SerienStream hosts so OkHttp/WebView share the session.
                add("https://s.to/")
                add("https://serienstream.to/")
            }
            runCatching {
                val configured = SerienStreamProvider.baseUrl.trimEnd('/') + "/"
                add(configured)
            }
        }
        val cookieManager = CookieManager.getInstance()
        cookieHeader.split(";")
            .map { it.trim() }
            .filter { it.contains("=") }
            .forEach { cookie ->
                targets.forEach { target ->
                    runCatching { cookieManager.setCookie(target, cookie) }
                }
            }
        runCatching { cookieManager.flush() }
    }

    /** True when the cookie jar looks like a real challenge pass, not just a session id. */
    fun looksLikeBypassSolved(cookieHeader: String): Boolean {
        if (cookieHeader.isBlank()) return false
        val lower = cookieHeader.lowercase()
        // Cloudflare / DDoS-Guard style markers — session-only cookies are not enough.
        val markers = listOf(
            "cf_clearance",
            "ddos_token",
            "__ddg1",
            "__ddg2",
            "altcha",
            "challenge",
        )
        return markers.any { lower.contains(it) }
    }
}
