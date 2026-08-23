package com.betterstreamflix.search

/**
 * Search query builder — constructs search queries with filters,
 * sort options, and provider targeting.
 */
object SearchQueryBuilder {

    data class SearchQuery(
        val text: String,
        val type: ContentType = ContentType.ALL,
        val genres: Set<String> = emptySet(),
        val minYear: Int? = null,
        val maxYear: Int? = null,
        val minRating: Double? = null,
        val providers: Set<String> = emptySet(),
        val sortBy: SortOption = SortOption.RELEVANCE,
        val includeAdult: Boolean = false,
    )

    enum class ContentType { ALL, MOVIE, TV_SHOW, ANIME }

    enum class SortOption { RELEVANCE, TITLE_ASC, TITLE_DESC, DATE_NEWEST, DATE_OLDEST, RATING_HIGHEST }

    /**
     * Build a search query from user input.
     */
    fun build(
        text: String,
        type: ContentType = ContentType.ALL,
        genres: Set<String> = emptySet(),
        minYear: Int? = null,
        maxYear: Int? = null,
        minRating: Double? = null,
        providers: Set<String> = emptySet(),
        sortBy: SortOption = SortOption.RELEVANCE,
        includeAdult: Boolean = false,
    ): SearchQuery {
        return SearchQuery(
            text = text.trim(),
            type = type,
            genres = genres,
            minYear = minYear,
            maxYear = maxYear,
            minRating = minRating,
            providers = providers,
            sortBy = sortBy,
            includeAdult = includeAdult,
        )
    }

    /**
     * Check if a query is empty (no text and no filters).
     */
    fun isEmpty(query: SearchQuery): Boolean {
        return query.text.isBlank() &&
            query.genres.isEmpty() &&
            query.minYear == null &&
            query.maxYear == null &&
            query.minRating == null &&
            query.providers.isEmpty()
    }

    /**
     * Convert query to a cache key.
     */
    fun toCacheKey(query: SearchQuery): String {
        return buildString {
            append(query.text.lowercase())
            append("|${query.type}")
            if (query.genres.isNotEmpty()) append("|${query.genres.sorted().joinToString(",")}")
            query.minYear?.let { append("|y>$it") }
            query.maxYear?.let { append("|y<$it") }
            query.minRating?.let { append("|r>$it") }
            if (query.providers.isNotEmpty()) append("|${query.providers.sorted().joinToString(",")}")
            append("|${query.sortBy}")
        }
    }
}
