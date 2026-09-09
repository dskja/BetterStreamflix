package com.dskja.betterstreamflix.utils

import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * Shared playback helpers for the LatAm sports IPTV mirrors
 * (TvPorInternet / LibreFutbol / CableVisionHD).
 *
 * Site UI moved from `<a.option href>` + `#player-frame` to
 * `<button.option data-src=".../core.php?canal=...">` + `#playerFrame`
 * (often empty until JS assigns data-src). Playback then goes
 * core.php → deportes.ksdjugfssddeports.com/stream.php → playlist.php.
 */
object SportsIptvStreamResolver {

    private val PLAYLIST_PATTERNS = listOf(
        Regex("""["'](https:\\?/\\?/[^"']+playlist\.php[^"']+)["']"""),
        Regex("""["'](https:[^"']+playlist\.php[^"']+)["']"""),
        Regex("""["'](playlist\.php\?[^"']+)["']"""),
        Regex("""["'](https:\\?/\\?/[^"']+\.m3u8[^"']*)["']"""),
        Regex("""["'](https:[^"']+\.m3u8[^"']*)["']"""),
    )

    fun collectServerUrls(document: Document, baseUrl: String): List<Pair<String, String>> {
        val collected = linkedMapOf<String, String>()

        document.select(
            "button.option[data-src], a.option[data-src], a.option[href], " +
                ".options-left .option[data-src], .options .option[data-src], " +
                "a[href*='core.php'], iframe[src*='core.php'], iframe[data-src*='core.php']"
        ).forEachIndexed { index, element ->
            val raw = element.attr("data-src")
                .ifBlank { element.attr("href") }
                .ifBlank { element.attr("src") }
            val absolute = absoluteUrl(raw, baseUrl) ?: return@forEachIndexed
            val label = element.text().trim()
                .ifBlank { element.attr("title").trim() }
                .ifBlank { "Opción ${index + 1}" }
            collected.putIfAbsent(absolute, label)
        }

        return collected.map { (url, name) -> name to url }
    }

    fun resolvePlaylistUrl(
        client: OkHttpClient,
        serverUrl: String,
        pageReferer: String,
        userAgent: String,
    ): String? {
        val firstDoc = fetchDocument(client, serverUrl, pageReferer, userAgent) ?: return null
        val coreUrl = findCoreOrEmbedUrl(firstDoc, serverUrl)
            ?: serverUrl.takeIf { it.contains("core.php", ignoreCase = true) }
            ?: return null

        val coreDoc = if (coreUrl == serverUrl) {
            firstDoc
        } else {
            fetchDocument(client, coreUrl, serverUrl, userAgent) ?: return null
        }

        val embedUrl = findKsdjugEmbed(coreDoc.html())
            ?: coreDoc.selectFirst("iframe[src*='ksdjug'], iframe[src*='stream.php'], iframe[src]")
                ?.attr("src")
                ?.replace("&amp;", "&")
                ?.let { absoluteUrl(it, coreUrl) }
            ?: return null

        val embedHtml = fetchHtml(client, embedUrl, coreUrl, userAgent) ?: return null
        return extractPlaylistUrl(embedHtml, embedUrl)
    }

    private fun findCoreOrEmbedUrl(document: Document, currentUrl: String): String? {
        document.selectFirst(
            "iframe#playerFrame[src], iframe#player-frame[src], iframe.player-frame[src], " +
                "#player iframe[src], .player iframe[src], iframe[src*='core.php']"
        )?.attr("src")
            ?.takeIf { it.isNotBlank() }
            ?.let { return absoluteUrl(it, currentUrl) }

        document.selectFirst("button.option[data-src], a.option[data-src], a.option[href*='core.php']")
            ?.let { el ->
                val raw = el.attr("data-src").ifBlank { el.attr("href") }
                absoluteUrl(raw, currentUrl)?.let { return it }
            }

        return null
    }

    private fun findKsdjugEmbed(html: String): String? {
        return Regex(
            """https://deportes\.ksdjugfssddeports\.com/[^"'\\\s<>]+""",
            RegexOption.IGNORE_CASE,
        ).find(html)
            ?.value
            ?.replace("&amp;", "&")
            ?.replace("\\/", "/")
    }

    private fun extractPlaylistUrl(html: String, embedUrl: String): String? {
        for (pattern in PLAYLIST_PATTERNS) {
            val raw = pattern.find(html)?.groupValues?.getOrNull(1) ?: continue
            val cleaned = raw.replace("\\/", "/").replace("\\u0026", "&")
            absoluteUrl(cleaned, embedUrl)?.let { return it }
        }
        return null
    }

    private fun absoluteUrl(raw: String?, base: String): String? {
        val value = raw?.trim().orEmpty()
        if (value.isBlank() || value.startsWith("about:", ignoreCase = true)) return null
        return when {
            value.startsWith("http://", ignoreCase = true) ||
                value.startsWith("https://", ignoreCase = true) -> value
            value.startsWith("//") -> "https:$value"
            value.startsWith("/") -> {
                val origin = Regex("""^(https?://[^/]+)""").find(base)?.groupValues?.getOrNull(1)
                    ?: return null
                "$origin$value"
            }
            else -> {
                val origin = base.substringBeforeLast('/').ifBlank { base }
                "$origin/$value"
            }
        }
    }

    private fun fetchDocument(
        client: OkHttpClient,
        url: String,
        referer: String,
        userAgent: String,
    ): Document? {
        val html = fetchHtml(client, url, referer, userAgent) ?: return null
        return Jsoup.parse(html, url)
    }

    private fun fetchHtml(
        client: OkHttpClient,
        url: String,
        referer: String,
        userAgent: String,
    ): String? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Accept", "*/*")
                .header("Referer", referer)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.string()
            }
        } catch (_: Exception) {
            null
        }
    }
}
