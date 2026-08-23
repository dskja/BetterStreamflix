package com.betterstreamflix.metadata

/**
 * Content filter helper — filters content by various criteria.
 */
object ContentFilterHelper {

    /**
     * Filter criteria for content.
     */
    data class FilterCriteria(
        val genres: Set<String> = emptySet(),
        val minYear: Int? = null,
        val maxYear: Int? = null,
        val minRating: Double? = null,
        val type: String? = null,
        val providerName: String? = null,
    )

    /**
     * Filter content items by criteria.
     */
    fun <T> filter(items: List<T>, criteria: FilterCriteria, selectors: FilterSelectors<T>): List<T> {
        return items.filter { item ->
            // Genre filter
            if (criteria.genres.isNotEmpty()) {
                val itemGenres = selectors.genres(item).map { it.lowercase() }
                if (criteria.genres.none { it.lowercase() in itemGenres }) return@filter false
            }

            // Year filter
            val year = selectors.year(item)
            if (criteria.minYear != null && year < criteria.minYear) return@filter false
            if (criteria.maxYear != null && year > criteria.maxYear) return@filter false

            // Rating filter
            if (criteria.minRating != null && selectors.rating(item) < criteria.minRating) return@filter false

            // Type filter
            if (criteria.type != null && selectors.type(item) != criteria.type) return@filter false

            // Provider filter
            if (criteria.providerName != null && selectors.providerName(item) != criteria.providerName) return@filter false

            true
        }
    }

    /**
     * Selector functions for filtering.
     */
    data class FilterSelectors<T>(
        val genres: (T) -> List<String>,
        val year: (T) -> Int,
        val rating: (T) -> Double,
        val type: (T) -> String,
        val providerName: (T) -> String,
    )
}
