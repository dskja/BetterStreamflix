package com.dskja.betterstreamflix.player

import android.net.Uri
import android.webkit.CookieManager
import com.dskja.betterstreamflix.models.Video
import com.dskja.betterstreamflix.providers.SerienStreamProvider
import com.dskja.betterstreamflix.providers.TmdbProvider
import com.dskja.betterstreamflix.utils.UserPreferences
import java.util.Locale

/** Shared SerienStream / CF bypass helpers used by mobile WebView and TV QR paths. */
object SerienStreamBypassHelper {

    private const val TMDB_DE_SERIENSTREAM = "tmdbde:serienstream:"

    /** Cookie names that are only browser noise (DuckDuckGo etc.), never a SerienStream login. */
    private val NOISE_COOKIE_NAMES = setOf(
        "__ddg1", "__ddg2", "__ddg3", "__ddg4", "__ddg5",
        "__ddg6", "__ddg7", "__ddg8", "__ddg9", "__ddg10",
        "__ddgid", "__ddgmark", "__ddg_privacy",
    )

    /** Cookie names that indicate a real SerienStream / CF / session pass. */
    private val AUTH_COOKIE_MARKERS = listOf(
        "cf_clearance",
        "ddos_token",
        "remember",
        "login",
        "session",
        "phpsessid",
        "xsrf",
        "laravel",
        "altcha",
        "serien",
        "auth",
        "token",
        "sso",
    )

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
        if (provider != SerienStreamProvider) return null
        return "${base}serie/$episodeId"
    }

    fun applyCookies(url: String, cookieHeader: String) {
        val cleaned = sanitizeSessionCookies(cookieHeader)
        val parts = mutableListOf<String>()
        if (cleaned.isNotBlank()) parts += cleaned
        val stored = sanitizeSessionCookies(UserPreferences.serienStreamSessionCookies)
        if (stored.isNotBlank() && stored != cleaned) parts += stored
        seedCookieHeader(url, parts.joinToString("; "))
    }

    /** Apply only the user session cookies (TV / VPN path without QR). */
    fun applyStoredSessionCookies(url: String = SerienStreamProvider.baseUrl) {
        seedCookieHeader(url, sanitizeSessionCookies(UserPreferences.serienStreamSessionCookies))
    }

    /**
     * Keep only SerienStream/CF/session cookies. Drops DuckDuckGo `__ddg*` noise that
     * previously made Settings show "Cookies saved" without a real login.
     */
    fun sanitizeSessionCookies(cookieHeader: String): String {
        if (cookieHeader.isBlank()) return ""
        val byName = linkedMapOf<String, String>()
        cookieHeader.split(";")
            .map { it.trim() }
            .filter { it.contains("=") }
            .forEach { cookie ->
                val name = cookie.substringBefore("=").trim().lowercase(Locale.US)
                if (name.isBlank()) return@forEach
                if (NOISE_COOKIE_NAMES.any { noise ->
                        name == noise || name.startsWith("${noise}_") || name.startsWith(noise)
                    }
                ) {
                    return@forEach
                }
                byName[name] = cookie
            }
        return byName.values.joinToString("; ")
    }

    /** True when the jar contains a real challenge/login cookie (not just browser noise). */
    fun looksLikeBypassSolved(cookieHeader: String): Boolean {
        val cleaned = sanitizeSessionCookies(cookieHeader)
        if (cleaned.isBlank()) return false
        val lower = cleaned.lowercase(Locale.US)
        return AUTH_COOKIE_MARKERS.any { lower.contains(it) }
    }

    /** Persist sanitized cookies only when they look like a real session. */
    fun persistSessionCookiesIfValid(cookieHeader: String): Boolean {
        val cleaned = sanitizeSessionCookies(cookieHeader)
        if (!looksLikeBypassSolved(cleaned)) {
            return false
        }
        UserPreferences.serienStreamSessionCookies = cleaned
        applyStoredSessionCookies()
        return true
    }

    fun clearStoredSessionCookies() {
        UserPreferences.serienStreamSessionCookies = ""
    }

    private fun seedCookieHeader(url: String, cookieHeader: String) {
        val cleaned = sanitizeSessionCookies(cookieHeader)
        if (cleaned.isBlank()) return
        val host = runCatching { Uri.parse(url).host.orEmpty() }.getOrDefault("")
        val targets = linkedSetOf<String>().apply {
            if (url.isNotBlank()) add(url)
            if (host.isNotBlank()) {
                add("https://$host/")
                add("http://$host/")
            }
            add("https://serienstream.to/")
            add("https://serienstream.cx/")
            SerienStreamProvider.candidateDomains().forEach { domain ->
                add("https://$domain/")
            }
            runCatching {
                add(SerienStreamProvider.baseUrl.trimEnd('/') + "/")
            }
        }
        val cookieManager = CookieManager.getInstance()
        val byName = linkedMapOf<String, String>()
        cleaned.split(";")
            .map { it.trim() }
            .filter { it.contains("=") }
            .forEach { cookie ->
                val name = cookie.substringBefore("=").trim().lowercase(Locale.US)
                if (name.isNotBlank()) byName[name] = cookie
            }
        byName.values.forEach { cookie ->
            targets.forEach { target ->
                runCatching { cookieManager.setCookie(target, cookie) }
            }
        }
        runCatching { cookieManager.flush() }
    }
}
