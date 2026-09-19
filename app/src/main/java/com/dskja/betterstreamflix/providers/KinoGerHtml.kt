package com.dskja.betterstreamflix.providers

import com.dskja.betterstreamflix.adapters.AppAdapter
import com.dskja.betterstreamflix.models.Episode
import com.dskja.betterstreamflix.models.Movie
import com.dskja.betterstreamflix.models.TvShow
import com.dskja.betterstreamflix.models.Video
import com.dskja.betterstreamflix.utils.MeinecloudEmbedHelper
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Pure HTML parsers for KinoGer, verified against live scrapes (kinoger.fun / .vip, Sep 2026).
 */
internal object KinoGerHtml {

    fun cleanTitle(raw: String): String =
        raw.replace(Regex("""\s*\(\d{4}\)\s*$"""), "")
            .replace(Regex("""\s*\*(English|Subbed)\*\s*$""", RegexOption.IGNORE_CASE), "")
            .trim()

    fun extractYear(raw: String): Int? =
        Regex("""\((\d{4})\)""").find(raw)?.groupValues?.get(1)?.toIntOrNull()

    fun extractStaffelNumber(titleRaw: String): Int =
        Regex("""Staffel\s+(\d+)""", RegexOption.IGNORE_CASE)
            .find(titleRaw)?.groupValues?.get(1)?.toIntOrNull() ?: 1

    /** Prefer #news-title — bare `h1` matches the logo header first. */
    fun pageTitle(document: Document): String =
        document.selectFirst("h1#news-title, #news-title, .titlecontrol .title")
            ?.text()
            ?.trim()
            .orEmpty()
            .ifBlank {
                document.selectFirst("meta[property=og:title]")?.attr("content")?.trim().orEmpty()
            }

    fun isSeriesCard(el: Element, title: String): Boolean {
        if (el.selectFirst(".serie-num") != null) return true
        if (title.contains("Staffel", ignoreCase = true)) return true
        val cats = el.select(".content_text").text()
        return cats.contains("Serien", ignoreCase = true)
    }

    fun parseShort(el: Element, absoluteUrl: (String) -> String): AppAdapter.Item? {
        val link = el.selectFirst(".titlecontrol .title a[href*=.html]")
            ?: el.selectFirst(".title a[href*=.html]")
            ?: el.selectFirst("a[href*=.html]")
            ?: return null
        val href = link.attr("href").trim()
        if (href.isBlank()) return null
        val titleRaw = link.text().trim().ifBlank {
            el.selectFirst(".title")?.text()?.trim().orEmpty()
        }
        if (titleRaw.isBlank()) return null
        val poster = extractPosterUrl(el, absoluteUrl)
        val title = cleanTitle(titleRaw)

        return if (isSeriesCard(el, titleRaw)) {
            TvShow(
                id = absoluteUrl(href),
                title = title,
                poster = poster,
                banner = poster, // list cards only have portrait art
            )
        } else {
            Movie(
                id = absoluteUrl(href),
                title = title,
                poster = poster,
                banner = poster,
            )
        }
    }

    /**
     * Prefer the real poster inside `.content_text`. Never use the title-row
     * `postinfo-icon` / favicon chrome images (comma CSS selectors match those first).
     */
    fun extractPosterUrl(el: Element, absoluteUrl: (String) -> String): String? {
        val candidates = listOfNotNull(
            el.selectFirst(".content_text img[src], .content_text img[data-src]"),
            el.selectFirst("img[src]:not(.img), img[data-src]:not(.img)"),
        )
        for (img in candidates) {
            val raw = img.attr("data-src").ifBlank { img.attr("src") }.trim()
            if (raw.isBlank()) continue
            if (isJunkPoster(raw)) continue
            val resolved = absoluteUrl(raw).ifBlank {
                runCatching { img.absUrl("src") }.getOrNull().orEmpty()
            }
            if (resolved.isNotBlank() && !isJunkPoster(resolved)) return resolved
        }
        return null
    }

    private fun isJunkPoster(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("postinfo-icon") ||
            lower.contains("favicon") ||
            (
                lower.contains("/templates/kinoger/images/") &&
                    (lower.endsWith(".png") || lower.endsWith(".ico") || lower.endsWith(".svg"))
                )
    }

    fun parseShorts(document: Document, absoluteUrl: (String) -> String): List<AppAdapter.Item> =
        document.select("div.short").mapNotNull { parseShort(it, absoluteUrl) }

    fun parseFeaturedCarousel(document: Document, absoluteUrl: (String) -> String): List<AppAdapter.Item> {
        return document.select(".owl-iteml-post a[href*=.html], .owl-item a[href*=.html]")
            .mapNotNull { a ->
                val href = a.attr("href").trim()
                if (href.isBlank()) return@mapNotNull null
                val titleRaw = a.attr("title").ifBlank {
                    a.selectFirst("span")?.text().orEmpty()
                }.ifBlank { a.text() }.trim()
                if (titleRaw.isBlank()) return@mapNotNull null
                val posterRaw = a.selectFirst("img")?.let { img ->
                    img.attr("data-src").ifBlank { img.attr("src") }
                }.orEmpty()
                if (isJunkPoster(posterRaw)) return@mapNotNull null
                val poster = absoluteUrl(posterRaw).takeIf { it.isNotBlank() && !isJunkPoster(it) }
                val title = cleanTitle(titleRaw)
                if (isSeriesCard(a.parent() ?: a, titleRaw) || titleRaw.contains("Staffel", true)) {
                    TvShow(
                        id = absoluteUrl(href),
                        title = title,
                        poster = poster,
                        banner = poster,
                    )
                } else {
                    Movie(
                        id = absoluteUrl(href),
                        title = title,
                        poster = poster,
                        banner = poster,
                    )
                }
            }
            .distinctBy {
                when (it) {
                    is Movie -> it.id
                    is TvShow -> it.id
                }
            }
    }

    fun parseMovieListSidebar(document: Document, absoluteUrl: (String) -> String): List<AppAdapter.Item> {
        return document.select(".movieList > a[href*=.html]")
            .mapNotNull { a ->
                val href = a.attr("href").trim()
                if (href.isBlank()) return@mapNotNull null
                val titleRaw = a.selectFirst("span")?.text()?.trim().orEmpty()
                    .ifBlank { a.attr("title").trim() }
                    .ifBlank { a.text().trim() }
                if (titleRaw.isBlank()) return@mapNotNull null
                val posterRaw = a.selectFirst("img")?.let { img ->
                    img.attr("data-src").ifBlank { img.attr("src") }
                }.orEmpty()
                if (isJunkPoster(posterRaw)) return@mapNotNull null
                val poster = absoluteUrl(posterRaw).takeIf { it.isNotBlank() && !isJunkPoster(it) }
                Movie(
                    id = absoluteUrl(href),
                    title = cleanTitle(titleRaw),
                    poster = poster,
                    banner = poster,
                )
            }
            .distinctBy { it.id }
    }

    /**
     * Episode IDs are `serie-{S}_{E}`. On some Staffel-N pages S matches N; on others
     * (e.g. MobLand Staffel 2) the menu still uses `serie-1_*`. Prefer matching S,
     * otherwise accept every episode on the page and attach [seasonNumber] from the title.
     */
    fun parseEpisodes(
        showUrl: String,
        seasonNumber: Int,
        document: Document,
        tmdbEpisodes: List<Episode> = emptyList(),
    ): List<Episode> {
        val raw = document.select(
            "ul.ep-menu li[id^=serie-], ul.series-select-menu.ep-menu li[id^=serie-], " +
                "ul.series-select-menu li[id^=serie-]",
        ).mapNotNull { li ->
            val idAttr = li.id().removePrefix("serie-")
            val parts = idAttr.split("_")
            val s = parts.getOrNull(0)?.toIntOrNull() ?: 1
            val e = parts.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
            Triple(s, e, li)
        }
        if (raw.isEmpty()) return emptyList()

        val matched = raw.filter { it.first == seasonNumber }
        val chosen = matched.ifEmpty { raw }

        return chosen.map { (_, e, _) ->
            val tmdbEp = tmdbEpisodes.find { it.number == e }
            Episode(
                id = "$showUrl#s${seasonNumber}e$e",
                number = e,
                title = tmdbEp?.title ?: "Episode $e",
                poster = tmdbEp?.poster,
                overview = tmdbEp?.overview,
            )
        }.distinctBy { it.number }.sortedBy { it.number }
    }

    fun findEpisodeElement(document: Document, season: Int, episode: Int): Element? {
        val menus = "ul.ep-menu, ul.series-select-menu"
        return document.selectFirst("$menus li#serie-${season}_$episode")
            ?: document.selectFirst("$menus li#serie-1_$episode")
            ?: document.select("$menus li[id^=serie-]").firstOrNull { li ->
                li.id().substringAfter('_', "").toIntOrNull() == episode
            }
    }

    fun normalizeStreamUrl(raw: String, absoluteUrl: (String) -> String): String? {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return null
        if (trimmed.contains("/vod/vpn", ignoreCase = true)) return null
        return when {
            trimmed.startsWith("//") -> "https:$trimmed"
            trimmed.startsWith("http") -> trimmed
            trimmed.startsWith("/") -> absoluteUrl(trimmed)
            else -> MeinecloudEmbedHelper.decodeDataLink(trimmed)?.let { decoded ->
                when {
                    decoded.startsWith("//") -> "https:$decoded"
                    decoded.startsWith("http") -> decoded
                    else -> null
                }
            }
        }
    }

    fun hosterDisplayName(url: String, fallback: String): String {
        val host = runCatching {
            url.toHttpUrlOrNull()?.host
                ?.removePrefix("www.")
                ?.substringBefore('.')
        }.getOrNull().orEmpty()
        return when {
            host.equals("voe", ignoreCase = true) -> "Voe"
            host.equals("meinecloud", ignoreCase = true) -> "Meinecloud"
            host.equals("vidara", ignoreCase = true) -> "Vidara"
            host.equals("firestream", ignoreCase = true) -> "Firestream"
            host.equals("mixdrop", ignoreCase = true) -> "Mixdrop"
            host.equals("streamtape", ignoreCase = true) -> "Streamtape"
            host.equals("supervideo", ignoreCase = true) -> "Supervideo"
            host.equals("dood", ignoreCase = true) || host.startsWith("dood", ignoreCase = true) -> "Doodstream"
            fallback.isNotBlank() -> fallback
            host.isNotBlank() -> host.replaceFirstChar { it.uppercase() }
            else -> "Server"
        }
    }

    fun parseMovieServers(document: Document, absoluteUrl: (String) -> String): List<Pair<String, String>> {
        return document.select(".player-mirrors span[data-link], .player-mirrors a[data-link]")
            .mapNotNull { el ->
                val link = normalizeStreamUrl(el.attr("data-link"), absoluteUrl) ?: return@mapNotNull null
                if (link.contains("youtube", ignoreCase = true)) return@mapNotNull null
                val label = el.ownText().ifBlank { el.text() }.trim()
                link to label
            }
            .distinctBy { it.first }
    }

    fun parseEpisodeServers(
        document: Document,
        season: Int,
        episode: Int,
        absoluteUrl: (String) -> String,
    ): List<Pair<String, String>> {
        val li = findEpisodeElement(document, season, episode) ?: return emptyList()
        return li.select("a[data-link], [data-link]").mapNotNull { a ->
            val link = normalizeStreamUrl(a.attr("data-link"), absoluteUrl) ?: return@mapNotNull null
            if (link.contains("youtube", ignoreCase = true)) return@mapNotNull null
            val label = a.ownText().ifBlank { a.text() }.trim()
            link to label
        }.distinctBy { it.first }
    }

    fun buildSearchUrl(baseUrl: String, query: String, page: Int): String {
        val base = baseUrl.trim().trimEnd('/')
        val encoded = java.net.URLEncoder.encode(query, Charsets.UTF_8.name())
        // Live site uses GET /?do=search&subaction=search&story=…
        return if (page <= 1) {
            "$base/?do=search&subaction=search&story=$encoded"
        } else {
            "$base/?do=search&subaction=search&story=$encoded&search_start=$page"
        }
    }
}
