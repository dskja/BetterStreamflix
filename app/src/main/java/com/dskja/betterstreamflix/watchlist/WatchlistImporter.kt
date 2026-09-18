package com.dskja.betterstreamflix.watchlist

import android.content.Context
import android.util.Log
import com.dskja.betterstreamflix.database.AppDatabase
import com.dskja.betterstreamflix.models.TvShow
import com.dskja.betterstreamflix.providers.AniWorldProvider
import com.dskja.betterstreamflix.providers.Provider
import com.dskja.betterstreamflix.providers.SerienStreamProvider
import com.dskja.betterstreamflix.utils.NetworkClient
import com.dskja.betterstreamflix.utils.UserDataCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URI

/**
 * Scrapes SerienStream / AniWorld account watchlists after WebView login and
 * writes entries into local Favorites for that provider.
 *
 * Primary path is HTML handed in from the WebView (challenge already solved).
 * OkHttp + CookieManager is kept as a secondary fallback.
 */
object WatchlistImporter {

    private const val TAG = "WatchlistImporter"
    const val MAX_PAGES = 40

    enum class Source {
        SERIENSTREAM,
        ANIWORLD,
    }

    data class Result(
        val importedCount: Int,
        val skippedCount: Int = 0,
        val pagesScraped: Int = 0,
        val errors: List<String> = emptyList(),
    )

    data class ImportedItem(
        val id: String,
        val title: String,
        val poster: String?,
    )

    fun providerFor(source: Source): Provider = when (source) {
        Source.SERIENSTREAM -> SerienStreamProvider
        Source.ANIWORLD -> AniWorldProvider
    }

    fun baseUrlFor(source: Source): String =
        providerFor(source).baseUrl.trimEnd('/') + "/"

    /** Candidate watchlist URLs for a given 1-based page index. */
    fun watchlistUrls(source: Source, page: Int): List<String> {
        val base = baseUrlFor(source).trimEnd('/')
        return if (page <= 1) {
            listOf(
                "$base/account/watchlist",
                "$base/account/listed",
            )
        } else {
            listOf(
                "$base/account/watchlist?page=$page",
                "$base/account/watchlist/$page",
                "$base/account/listed?page=$page",
            )
        }
    }

    fun looksLikeLoginPage(html: String, finalUrl: String? = null): Boolean {
        val url = finalUrl.orEmpty().lowercase()
        if (url.contains("/login")) return true
        val lower = html.lowercase()
        if (lower.contains("ddos-guard") || lower.contains("cf-browser-verification")) {
            return false // challenge, not login — handled separately
        }
        val hasLoginForm = lower.contains("name=\"email\"") ||
            lower.contains("name=\"password\"") ||
            lower.contains("id=\"login\"") ||
            (lower.contains("einloggen") && lower.contains("passwort"))
        val hasWatchlist = lower.contains("serieslistcontainer") ||
            lower.contains("/anime/stream/") ||
            lower.contains("/serie/")
        return hasLoginForm && !hasWatchlist
    }

    fun looksLikeChallengePage(html: String): Boolean {
        val lower = html.lowercase()
        return lower.contains("ddos-guard") ||
            lower.contains("cf-browser-verification") ||
            lower.contains("checking your browser") ||
            lower.contains("just a moment") ||
            (lower.contains("challenge") && lower.length < 8_000 && !lower.contains("serieslistcontainer"))
    }

    fun findNextPageUrl(html: String, currentUrl: String, baseUrl: String): String? {
        if (html.isBlank()) return null
        val doc = Jsoup.parse(html, baseUrl)
        val candidates = doc.select(
            "a[rel=next], .pagination a, ul.pagination a, .pager a, nav.pagination a",
        )
        for (link in candidates) {
            val text = link.text().trim().lowercase()
            val label = link.attr("aria-label").lowercase()
            val cls = link.className().lowercase()
            val isNext = text in setOf("»", "›", ">", "next", "weiter") ||
                label.contains("next") ||
                cls.contains("next")
            val href = link.attr("abs:href").ifBlank { link.attr("href") }
            if (isNext && href.isNotBlank() && href != currentUrl) {
                return href
            }
        }
        // Numeric page links greater than current
        val currentPage = Regex("""[?&]page=(\d+)|/(\d+)/?$""")
            .find(currentUrl)
            ?.let { it.groupValues[1].ifBlank { it.groupValues[2] }.toIntOrNull() }
            ?: 1
        var best: Pair<Int, String>? = null
        for (link in candidates) {
            val href = link.attr("abs:href").ifBlank { link.attr("href") }
            val page = Regex("""[?&]page=(\d+)|/watchlist/(\d+)|/listed/(\d+)""")
                .find(href)
                ?.groupValues
                ?.drop(1)
                ?.firstOrNull { it.isNotBlank() }
                ?.toIntOrNull()
                ?: continue
            if (page == currentPage + 1) {
                return href
            }
            if (page > currentPage && (best == null || page < best.first)) {
                best = page to href
            }
        }
        return best?.second
    }

    /**
     * Parse a watchlist / catalog HTML page into importable TV show items.
     * Movie (`/film/`) links are skipped — both providers are TV-only.
     */
    fun parseItems(html: String, baseUrl: String, source: Source): List<ImportedItem> {
        if (html.isBlank()) return emptyList()
        if (looksLikeChallengePage(html) || looksLikeLoginPage(html)) return emptyList()

        val doc = Jsoup.parse(html, baseUrl)
        val items = linkedMapOf<String, ImportedItem>()

        val cardSelector =
            "div.seriesListContainer div[class*=col-], " +
                "div.coverListItem, div.card.cover-card, " +
                ".seriesListContainer .col-md-3, .seriesListContainer .col-md-15, " +
                ".seriesListContainer .col-sm-3, .seriesListContainer .col-xs-6"

        doc.select(cardSelector).forEach { card ->
            toItemFromCard(card, baseUrl, source)?.let { items.putIfAbsent(it.id, it) }
        }

        if (items.isEmpty()) {
            val anchorSelector = when (source) {
                Source.SERIENSTREAM -> "a[href*=/serie/]"
                Source.ANIWORLD -> "a[href*=/anime/stream/], a[href*=/serie/]"
            }
            doc.select(anchorSelector).forEach { link ->
                toItemFromAnchor(link, baseUrl, source)?.let { items.putIfAbsent(it.id, it) }
            }
        }

        return items.values.toList()
    }

    fun parseItemFromHref(
        href: String,
        title: String,
        poster: String?,
        baseUrl: String,
        source: Source,
    ): ImportedItem? {
        val absolute = absolutize(href, baseUrl) ?: return null
        val path = pathOf(absolute) ?: return null
        if (isMoviePath(path)) return null
        val id = extractShowId(path, source) ?: return null
        if (id.equals("stream", ignoreCase = true) || id.equals("serie", ignoreCase = true)) {
            return null
        }
        val cleanTitle = cleanTitle(title).ifBlank {
            id.replace('-', ' ').replaceFirstChar { it.uppercase() }
        }
        return ImportedItem(
            id = id,
            title = cleanTitle,
            poster = normalizePoster(poster, baseUrl),
        )
    }

    suspend fun persist(
        context: Context,
        source: Source,
        items: Collection<ImportedItem>,
    ): Result = withContext(Dispatchers.IO) {
        if (items.isEmpty()) {
            return@withContext Result(importedCount = 0, errors = listOf("No watchlist items found"))
        }
        val provider = providerFor(source)
        val database = AppDatabase.getInstanceForProvider(provider.name, context)
        var count = 0
        val now = System.currentTimeMillis()
        for (item in items) {
            runCatching {
                val show = TvShow(
                    id = item.id,
                    title = item.title,
                    poster = item.poster,
                ).apply {
                    isFavorite = true
                    favoritedAtMillis = now
                }
                database.tvShowDao().upsertFavorite(show, favorite = true)
                UserDataCache.addTvShowToFavorites(context, provider, show)
                count++
            }.onFailure { e ->
                Log.w(TAG, "Failed persisting ${item.id}: ${e.message}")
            }
        }
        Result(importedCount = count)
    }

    /**
     * OkHttp fallback using WebView CookieManager (via [NetworkClient.cookieJar]).
     */
    suspend fun importViaHttp(
        context: Context,
        source: Source,
        cookieHeader: String,
    ): Result = withContext(Dispatchers.IO) {
        val baseUrl = baseUrlFor(source)
        val seen = linkedMapOf<String, ImportedItem>()
        val errors = mutableListOf<String>()
        var pages = 0
        var nextOverride: String? = null

        for (page in 1..MAX_PAGES) {
            val urls = if (nextOverride != null) {
                listOf(nextOverride!!)
            } else {
                watchlistUrls(source, page)
            }
            var pageItems = emptyList<ImportedItem>()
            var pageHtml: String? = null
            var pageUrl: String? = null

            for (url in urls) {
                runCatching {
                    val html = fetchHtml(url, cookieHeader, baseUrl)
                    if (looksLikeLoginPage(html, url)) {
                        errors += "Not logged in (redirected to login)"
                        return@withContext Result(0, errors = errors)
                    }
                    if (looksLikeChallengePage(html)) {
                        errors += "Bot challenge still active — finish the check in the WebView"
                        return@withContext Result(0, errors = errors)
                    }
                    val parsed = parseItems(html, baseUrl, source)
                    if (parsed.isNotEmpty() || pageHtml == null) {
                        pageHtml = html
                        pageUrl = url
                        pageItems = parsed
                    }
                    if (parsed.isNotEmpty()) return@runCatching
                }.onFailure { e ->
                    Log.w(TAG, "HTTP fetch failed $url: ${e.message}")
                    errors += "${url.substringAfter(baseUrl)}: ${e.message}"
                }
            }

            if (pageItems.isEmpty()) {
                if (page == 1) {
                    runCatching {
                        val html = fetchHtml(baseUrl + "account", cookieHeader, baseUrl)
                        pageItems = parseItems(html, baseUrl, source)
                        pageHtml = html
                        pageUrl = baseUrl + "account"
                    }.onFailure { e -> errors += "account: ${e.message}" }
                }
                if (pageItems.isEmpty()) break
            }

            val before = seen.size
            pageItems.forEach { seen.putIfAbsent(it.id, it) }
            pages++
            if (seen.size == before && page > 1) break

            val next = pageHtml?.let { findNextPageUrl(it, pageUrl.orEmpty(), baseUrl) }
            nextOverride = next
            if (next == null && pageItems.isEmpty()) break
            if (next == null && page >= 1) {
                // Try implicit next page numbers even without pagination links.
                if (pageItems.isEmpty()) break
            }
        }

        val persisted = persist(context, source, seen.values)
        persisted.copy(pagesScraped = pages, errors = errors + persisted.errors)
    }

    /** Convenience: persist already-parsed items from WebView scrape. */
    suspend fun importParsed(
        context: Context,
        source: Source,
        items: Collection<ImportedItem>,
        pagesScraped: Int,
        errors: List<String> = emptyList(),
    ): Result {
        val persisted = persist(context, source, items)
        return persisted.copy(pagesScraped = pagesScraped, errors = errors + persisted.errors)
    }

    @Deprecated("Use importViaHttp or WebView scrape + importParsed", ReplaceWith("importViaHttp(context, source, cookieHeader)"))
    suspend fun import(
        context: Context,
        source: Source,
        cookieHeader: String,
    ): Result = importViaHttp(context, source, cookieHeader)

    private fun toItemFromCard(card: Element, baseUrl: String, source: Source): ImportedItem? {
        val link = card.selectFirst(
            when (source) {
                Source.SERIENSTREAM -> "a[href*=/serie/]"
                Source.ANIWORLD -> "a[href*=/anime/stream/], a[href*=/serie/]"
            },
        ) ?: card.selectFirst("a[href]") ?: return null
        return toItemFromAnchor(link, baseUrl, source, card)
    }

    private fun toItemFromAnchor(
        link: Element,
        baseUrl: String,
        source: Source,
        card: Element? = null,
    ): ImportedItem? {
        val href = link.attr("abs:href").ifBlank { link.attr("href") }
        val img = link.selectFirst("img")
            ?: card?.selectFirst("img")
        val poster = sequenceOf(
            img?.attr("abs:src"),
            img?.attr("abs:data-src"),
            img?.attr("data-src"),
            img?.attr("src"),
        ).mapNotNull { it?.takeIf { s -> s.isNotBlank() } }.firstOrNull()

        val title = sequenceOf(
            card?.selectFirst("h3")?.text(),
            link.selectFirst("h3")?.text(),
            link.attr("title"),
            link.text(),
            card?.selectFirst(".title, .coverListItem-title, strong")?.text(),
        ).mapNotNull { it?.takeIf { s -> s.isNotBlank() } }.firstOrNull().orEmpty()

        return parseItemFromHref(href, title, poster, baseUrl, source)
    }

    internal fun extractShowId(path: String, source: Source): String? {
        val segments = path.trim('/').split('/').filter { it.isNotBlank() }
        if (segments.isEmpty()) return null
        return when (source) {
            Source.SERIENSTREAM -> {
                when {
                    segments[0] == "serie" -> segments.getOrNull(1)
                    segments[0] == "film" -> null
                    else -> segments[0] // already stripped in some relative forms
                }?.substringBefore('?')?.takeIf { it.isNotBlank() }
            }
            Source.ANIWORLD -> {
                when {
                    segments.size >= 3 &&
                        segments[0] == "anime" &&
                        segments[1] == "stream" -> segments[2]
                    segments[0] == "serie" -> segments.getOrNull(1)
                    segments[0] == "anime" -> segments.getOrNull(1)?.takeIf { it != "stream" }
                    else -> null
                }?.substringBefore('?')?.takeIf { it.isNotBlank() }
            }
        }
    }

    private fun isMoviePath(path: String): Boolean {
        val segments = path.trim('/').split('/').filter { it.isNotBlank() }
        return segments.firstOrNull() == "film"
    }

    private fun pathOf(url: String): String? {
        val path = runCatching { URI(url).path }.getOrNull() ?: return null
        return path.trimStart('/')
    }

    private fun absolutize(href: String, baseUrl: String): String? {
        if (href.isBlank()) return null
        if (href.startsWith("http://") || href.startsWith("https://")) return href
        if (href.startsWith("//")) return "https:$href"
        val root = baseUrl.trimEnd('/')
        return if (href.startsWith("/")) {
            val uri = runCatching { URI(root) }.getOrNull() ?: return "$root$href"
            "${uri.scheme}://${uri.host}$href"
        } else {
            "$root/$href"
        }
    }

    private fun normalizePoster(poster: String?, baseUrl: String): String? {
        if (poster.isNullOrBlank()) return null
        if (poster.startsWith("http://") || poster.startsWith("https://")) return poster
        if (poster.startsWith("//")) return "https:$poster"
        return baseUrl.trimEnd('/') + "/" + poster.trimStart('/')
    }

    private fun cleanTitle(raw: String): String {
        var title = raw.replace(Regex("""<[^>]+>"""), " ").trim()
        title = title
            .replace(Regex("""\s*stream online.*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*alle Staffeln.*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*kostenlos.*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*sofort.*$""", RegexOption.IGNORE_CASE), "")
            .trim()
        return title
    }

    private fun fetchHtml(url: String, cookieHeader: String, referer: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Cookie", cookieHeader)
            .header("User-Agent", NetworkClient.USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "de-DE,de;q=0.9,en-US;q=0.8,en;q=0.7")
            .header("Referer", referer)
            .get()
            .build()
        NetworkClient.default.newCall(request).execute().use { response ->
            // Followed redirects may land on login — still return body for detection.
            if (!response.isSuccessful && response.code !in 300..399) {
                error("HTTP ${response.code}")
            }
            return response.body?.string().orEmpty()
        }
    }
}
