package com.betterstreamflix.search

/**
 * Search filter processor — applies filters to search results
 * based on the search query criteria.
 */
object SearchFilterProcessor {

    /**
     * Apply filters to a list of items.
     */
    fun <T> applyFilters(
        items: List<T>,
        query: SearchQueryBuilder.SearchQuery,
        extractors: FilterExtractors<T>,
    ): List<T> {
        return items.filter { item ->
            // Type filter
            if (query.type != SearchQueryBuilder.ContentType.ALL) {
                val itemType = extractors.type(item)
                val typeMatch = when (query.type) {
                    SearchQueryBuilder.ContentType.MOVIE -> itemType == "movie"
                    SearchQueryBuilder.ContentType.TV_SHOW -> itemType == "tv"
                    SearchQueryBuilder.ContentType.ANIME -> itemType == "anime" || itemType == "tv"
                    else -> true
                }
                if (!typeMatch) return@filter false
            }

            // Genre filter
            if (query.genres.isNotEmpty()) {
                val itemGenres = extractors.genres(item).map { it.lowercase() }
                if (query.genres.none { it.lowercase() in itemGenres }) return@filter false
            }

            // Year filter
            val year = extractors.year(item)
            if (query.minYear != null && year < query.minYear) return@filter false
            if (query.maxYear != null && year > query.maxYear) return@filter false

            // Rating filter
            if (query.minRating != null && extractors.rating(item) < query.minRating) return@filter false

            // Provider filter
            if (query.providers.isNotEmpty()) {
                val itemProvider = extractors.provider(item)
                if (itemProvider !in query.providers) return@filter false
            }

            true
        }
    }

    /**
     * Sort results based on the sort option.
     */
    fun <T> sortResults(
        items: List<T>,
        sortBy: SearchQueryBuilder.SortOption,
        extractors: FilterExtractors<T>,
    ): List<T> {
        return when (sortBy) {
            SearchQueryBuilder.SortOption.RELEVANCE -> items
            SearchQueryBuilder.SortOption.TITLE_ASC -> items.sortedBy(extractors.title)
            SearchQueryBuilder.SortOption.TITLE_DESC -> items.sortedByDescending(extractors.title)
            SearchQueryBuilder.SortOption.DATE_NEWEST -> items.sortedByDescending(extractors.year)
            SearchQueryBuilder.SortOption.DATE_OLDEST -> items.sortedBy(extractors.year)
            SearchQueryBuilder.SortOption.RATING_HIGHEST -> items.sortedByDescending(extractors.rating)
        }
    }

    data class FilterExtractors<T>(
        val title: (T) -> String,
        val type: (T) -> String,
        val genres: (T) -> List<String>,
        val year: (T) -> Int,
        val rating: (T) -> Double,
        val provider: (T) -> String,
    )
}
