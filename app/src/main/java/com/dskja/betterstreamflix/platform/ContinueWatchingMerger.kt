package com.dskja.betterstreamflix.platform

import com.dskja.betterstreamflix.adapters.AppAdapter
import com.dskja.betterstreamflix.models.Category
import com.dskja.betterstreamflix.models.Episode
import com.dskja.betterstreamflix.models.Movie
import com.dskja.betterstreamflix.models.TvShow

/**
 * Merges local Continue Watching with optional Trakt / self-host rows without duplicates.
 */
object ContinueWatchingMerger {
    private val SELFHOST_CW_PREFIXES = listOf(
        "Jellyfin · Continue",
        "Plex · On Deck",
        Category.CONTINUE_WATCHING,
    )

    fun isProviderContinueWatching(name: String): Boolean =
        SELFHOST_CW_PREFIXES.any { name.equals(it, ignoreCase = true) } ||
            (
                name.contains("continue", ignoreCase = true) &&
                    (
                        name.contains("jellyfin", ignoreCase = true) ||
                            name.contains("plex", ignoreCase = true) ||
                            name.contains("on deck", ignoreCase = true)
                        )
                )

    /**
     * Builds a single CW list: local first, then remote extras not already present by identity key.
     */
    fun merge(
        local: List<AppAdapter.Item>,
        remoteExtras: List<AppAdapter.Item>,
    ): List<AppAdapter.Item> {
        val seen = linkedSetOf<String>()
        val out = mutableListOf<AppAdapter.Item>()
        fun addAll(items: List<AppAdapter.Item>) {
            for (item in items) {
                val key = identityKey(item) ?: continue
                if (seen.add(key)) out.add(item)
            }
        }
        addAll(local)
        addAll(remoteExtras)
        return out
    }

    fun identityKey(item: AppAdapter.Item): String? = when (item) {
        is Movie -> {
            val imdb = item.imdbId?.takeIf { it.isNotBlank() }
            "movie:${imdb ?: item.id}"
        }
        is Episode -> {
            val show = item.tvShow
            val showKey = show?.imdbId?.takeIf { it.isNotBlank() } ?: show?.id ?: return null
            "ep:$showKey"
        }
        is TvShow -> {
            val imdb = item.imdbId?.takeIf { it.isNotBlank() }
            "show:${imdb ?: item.id}"
        }
        else -> null
    }

    /** Strip provider CW categories that HomeViewModel already merged into the unified row. */
    fun filterProviderCategories(categories: List<Category>): List<Category> =
        categories.filterNot { isProviderContinueWatching(it.name) }
}
