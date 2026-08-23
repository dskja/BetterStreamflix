package com.betterstreamflix.metadata

/**
 * Content sort helper — provides various sorting options for content lists.
 */
object ContentSortHelper {

    enum class SortMode {
        TITLE_ASC, TITLE_DESC,
        DATE_NEWEST, DATE_OLDEST,
        RATING_HIGHEST, RATING_LOWEST,
        MOST_WATCHED, RECENTLY_WATCHED,
    }

    /**
     * Sort a list of content items by the given mode.
     */
    fun <T> sort(items: List<T>, mode: SortMode, selectors: SortSelectors<T>): List<T> {
        return when (mode) {
            SortMode.TITLE_ASC -> items.sortedBy(selectors.title)
            SortMode.TITLE_DESC -> items.sortedByDescending(selectors.title)
            SortMode.DATE_NEWEST -> items.sortedByDescending(selectors.date)
            SortMode.DATE_OLDEST -> items.sortedBy(selectors.date)
            SortMode.RATING_HIGHEST -> items.sortedByDescending(selectors.rating)
            SortMode.RATING_LOWEST -> items.sortedBy(selectors.rating)
            SortMode.MOST_WATCHED -> items.sortedByDescending(selectors.watchCount)
            SortMode.RECENTLY_WATCHED -> items.sortedByDescending(selectors.lastWatched)
        }
    }

    /**
     * Selector functions for sorting.
     */
    data class SortSelectors<T>(
        val title: (T) -> String,
        val date: (T) -> String,
        val rating: (T) -> Double,
        val watchCount: (T) -> Int,
        val lastWatched: (T) -> Long,
    )
}
