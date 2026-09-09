package com.dskja.betterstreamflix.watchlist

import android.content.Context
import android.util.Log
import com.dskja.betterstreamflix.database.AppDatabase
import com.dskja.betterstreamflix.models.Movie
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

/**
 * Scrapes SerienStream / AniWorld account watchlist pages after the user has logged in
 * via WebView, then maps entries into local Room favorites for that provider.
 */
object WatchlistImporter {

    private const val TAG = "WatchlistImporter"

    enum class Source {
        SERIENSTREAM,
        ANIWORLD,
    }

    data class Result(
        val importedCount: Int,
        val errors: List<String> = emptyList(),
    )

    suspend fun import(
        context: Context,
        source: Source,
        cookieHeader: String,
    ): Result = withContext(Dispatchers.IO) {
        val provider: Provider = when (source) {
            Source.SERIENSTREAM -> SerienStreamProvider
            Source.ANIWORLD -> AniWorldProvider
        }
        val baseUrl = provider.baseUrl.trimEnd('/') + "/"
        val paths = listOf("account/watchlist", "account/listed", "account/watchlist/")
        val seen = linkedMapOf<String, ImportedItem>()
        val errors = mutableListOf<String>()

        for (path in paths) {
            val url = baseUrl + path.removePrefix("/")
            runCatching {
                val html = fetchHtml(url, cookieHeader, baseUrl)
                parseItems(html, baseUrl, source).forEach { item ->
                    seen.putIfAbsent(item.id, item)
                }
            }.onFailure { e ->
                Log.w(TAG, "Failed fetching $url: ${e.message}")
                errors += "${path}: ${e.message}"
            }
        }

        if (seen.isEmpty()) {
            // Fallback: try the account root in case watchlist is embedded.
            runCatching {
                val html = fetchHtml(baseUrl + "account", cookieHeader, baseUrl)
                parseItems(html, baseUrl, source).forEach { item ->
                    seen.putIfAbsent(item.id, item)
                }
            }.onFailure { e ->
                errors += "account: ${e.message}"
            }
        }

        if (seen.isEmpty()) {
            return@withContext Result(
                importedCount = 0,
                errors = errors.ifEmpty { listOf("No watchlist items found (are you logged in?)") },
            )
        }

        val database = AppDatabase.getInstanceForProvider(provider.name, context)
        var count = 0
        for (item in seen.values) {
            when (item.kind) {
                Kind.TV_SHOW -> {
                    val show = TvShow(
                        id = item.id,
                        title = item.title,
                        poster = item.poster,
                    ).apply {
                        isFavorite = true
                        favoritedAtMillis = System.currentTimeMillis()
                    }
                    database.tvShowDao().upsertFavorite(show, favorite = true)
                    UserDataCache.addTvShowToFavorites(context, provider, show)
                    count++
                }
                Kind.MOVIE -> {
                    val movie = Movie(
                        id = item.id,
                        title = item.title,
                        poster = item.poster,
                    ).apply {
                        isFavorite = true
                        favoritedAtMillis = System.currentTimeMillis()
                    }
                    database.movieDao().upsertFavorite(movie, favorite = true)
                    UserDataCache.addMovieToFavorites(context, provider, movie)
                    count++
                }
            }
        }

        Result(importedCount = count, errors = errors)
    }

    private fun fetchHtml(url: String, cookieHeader: String, referer: String): String {
        val request = Request.Builder()
            .url(url)
            .header("Cookie", cookieHeader)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("Referer", referer)
            .get()
            .build()
        NetworkClient.default.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("HTTP ${response.code}")
            }
            return response.body?.string().orEmpty()
        }
    }

    private fun parseItems(html: String, baseUrl: String, source: Source): List<ImportedItem> {
        if (html.isBlank()) return emptyList()
        val doc = Jsoup.parse(html, baseUrl)
        val items = mutableListOf<ImportedItem>()

        val seriesSelector = when (source) {
            Source.SERIENSTREAM -> "a[href*=/serie/], a[href*=/film/]"
            Source.ANIWORLD -> "a[href*=/anime/], a[href*=/film/], a[href*=/serie/]"
        }

        doc.select("div.coverListItem, div.card.cover-card, div.col, li, .seriesListContainer .col-md-3")
            .forEach { card ->
                val link = card.selectFirst(seriesSelector) ?: return@forEach
                val href = link.attr("abs:href").ifBlank { link.attr("href") }
                val item = toItem(href, card.selectFirst("img")?.attr("abs:src")
                    ?: card.selectFirst("img")?.attr("data-src"),
                    link.text().ifBlank {
                        card.selectFirst("h3, .title, .coverListItem-title, strong")?.text().orEmpty()
                    },
                    source,
                ) ?: return@forEach
                items += item
            }

        // Broad fallback: any matching anchor on the page.
        if (items.isEmpty()) {
            doc.select(seriesSelector).forEach { link ->
                val href = link.attr("abs:href").ifBlank { link.attr("href") }
                val item = toItem(
                    href = href,
                    poster = link.selectFirst("img")?.attr("abs:src"),
                    title = link.text().ifBlank { link.attr("title") },
                    source = source,
                ) ?: return@forEach
                items += item
            }
        }

        return items.distinctBy { it.id }
    }

    private fun toItem(
        href: String,
        poster: String?,
        title: String,
        source: Source,
    ): ImportedItem? {
        val path = href.substringAfter("://").substringAfter("/", missingDelimiterValue = href)
            .trimStart('/')
        val kind = when {
            path.startsWith("film/") || path.contains("/film/") -> Kind.MOVIE
            path.startsWith("serie/") || path.startsWith("anime/") ||
                path.contains("/serie/") || path.contains("/anime/") -> Kind.TV_SHOW
            else -> return null
        }
        val id = when (source) {
            Source.SERIENSTREAM -> {
                val slug = path
                    .removePrefix("serie/")
                    .removePrefix("film/")
                    .substringBefore('/')
                    .substringBefore('?')
                if (slug.isBlank()) return null
                if (kind == Kind.MOVIE) "film/$slug" else slug
            }
            Source.ANIWORLD -> {
                val slug = path
                    .removePrefix("anime/")
                    .removePrefix("serie/")
                    .removePrefix("film/")
                    .substringBefore('/')
                    .substringBefore('?')
                if (slug.isBlank()) return null
                slug
            }
        }
        val cleanTitle = title.trim().ifBlank { id.substringAfterLast('/') }
        return ImportedItem(
            id = id,
            title = cleanTitle,
            poster = poster?.takeIf { it.isNotBlank() },
            kind = kind,
        )
    }

    private enum class Kind { MOVIE, TV_SHOW }

    private data class ImportedItem(
        val id: String,
        val title: String,
        val poster: String?,
        val kind: Kind,
    )

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
}
