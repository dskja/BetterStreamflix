package com.dskja.betterstreamflix.utils

import android.util.Base64
import android.util.Log
import com.dskja.betterstreamflix.models.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.jsoup.Jsoup
import java.util.concurrent.TimeUnit

/**
 * Expands meinecloud / similar DE embed pages into concrete hoster URLs.
 * Used by German providers so playback never stops at the embed wrapper.
 */
object MeinecloudEmbedHelper {

    private const val TAG = "MeinecloudEmbed"

    fun isMeinecloudUrl(url: String): Boolean {
        val host = url.toHttpUrlOrNull()?.host?.lowercase().orEmpty()
        return host.contains("meinecloud") || host.contains("devideosrc")
    }

    fun isFirestreamUrl(url: String): Boolean {
        val host = url.toHttpUrlOrNull()?.host?.lowercase().orEmpty()
        return host.contains("firestream")
    }

    /** True for embed wrappers that must be expanded before Extractor.extract. */
    fun isEmbedWrapper(url: String): Boolean =
        isMeinecloudUrl(url) || isFirestreamUrl(url)

    suspend fun expandToServers(embedUrl: String, referer: String? = null): List<Video.Server> {
        if (embedUrl.isBlank()) return emptyList()
        return runCatching {
            val html = withContext(Dispatchers.IO) { fetchHtml(embedUrl, referer) }
            parseMirrors(html, embedUrl)
        }.onFailure {
            Log.w(TAG, "expand failed for $embedUrl: ${it.message}")
        }.getOrDefault(emptyList())
    }

    /**
     * Resolve a wrapper URL to the first concrete hoster URL suitable for Extractor.
     * Returns [embedUrl] unchanged when expansion finds nothing.
     */
    suspend fun resolveToHosterUrl(embedUrl: String, referer: String? = null): String {
        if (!isEmbedWrapper(embedUrl)) return embedUrl
        val servers = expandToServers(embedUrl, referer)
        val best = servers.firstOrNull { !isEmbedWrapper(it.src) } ?: servers.firstOrNull()
        return best?.src?.takeIf { it.isNotBlank() } ?: embedUrl
    }

    private fun fetchHtml(url: String, referer: String?): String {
        val client = NetworkClient.default.newBuilder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", NetworkClient.USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "de-DE,de;q=0.9,en;q=0.7")
            .apply {
                if (!referer.isNullOrBlank()) header("Referer", referer)
            }
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code} fetching embed")
            }
            response.body?.string().orEmpty()
        }
    }

    private fun parseMirrors(html: String, baseUri: String): List<Video.Server> {
        val doc = Jsoup.parse(html, baseUri)
        val selectors = listOf(
            "ul._player-mirrors li[data-link]",
            "ul._source_list li[data-link]",
            "li[data-link]",
            ".mirror-list li[data-link]",
            "a[data-link]",
            ".player-mirrors span[data-link]",
            "a[href^=http]",
        )
        val out = linkedMapOf<String, Video.Server>()
        for (selector in selectors) {
            doc.select(selector).forEach { el ->
                val raw = el.attr("data-link").trim()
                    .ifBlank { el.attr("href").trim() }
                if (raw.isBlank()) return@forEach
                val decoded = decodeDataLink(raw) ?: return@forEach
                val normalized = normalizeUrl(decoded) ?: return@forEach
                if (normalized.contains("youtube", ignoreCase = true)) return@forEach
                if (isMeinecloudUrl(normalized) && normalized == baseUri) return@forEach
                val label = el.ownText().ifBlank { el.text() }.trim()
                val name = label.ifBlank {
                    normalized.toHttpUrlOrNull()?.host
                        ?.removePrefix("www.")
                        ?.substringBefore('.')
                        ?.replaceFirstChar { it.uppercase() }
                        ?: "Server"
                }
                out.putIfAbsent(normalized, Video.Server(id = normalized, name = name, src = normalized))
            }
            if (out.isNotEmpty()) break
        }

        // Firestream / packed pages: pick iframe or packed source redirect.
        if (out.isEmpty()) {
            doc.select("iframe[src], iframe[data-src]").forEach { iframe ->
                val src = iframe.attr("src").ifBlank { iframe.attr("data-src") }.trim()
                val normalized = normalizeUrl(src) ?: return@forEach
                if (normalized.contains("youtube", ignoreCase = true)) return@forEach
                out.putIfAbsent(
                    normalized,
                    Video.Server(id = normalized, name = "Embed", src = normalized),
                )
            }
        }

        return out.values.toList()
    }

    fun decodeDataLink(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.startsWith("http", ignoreCase = true) || trimmed.startsWith("//")) {
            return trimmed
        }
        return runCatching {
            val decoded = String(Base64.decode(trimmed, Base64.DEFAULT), Charsets.UTF_8).trim()
            decoded.takeIf {
                it.startsWith("http", ignoreCase = true) || it.startsWith("//") || it.contains('.')
            }
        }.getOrNull() ?: trimmed.takeIf { it.contains('.') }
    }

    private fun normalizeUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null
        return when {
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("http") -> trimmed
            trimmed.startsWith("/") -> null
            trimmed.contains('.') -> "https://$trimmed"
            else -> null
        }
    }
}
