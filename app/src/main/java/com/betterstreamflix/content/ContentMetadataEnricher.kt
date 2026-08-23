package com.betterstreamflix.content

/**
 * Content metadata enricher — enhances content with additional metadata
 * from TMDB or other sources.
 */
object ContentMetadataEnricher {

    /**
     * Enrich content with TMDB metadata.
     */
    fun <T> enrichContent(
        items: List<T>,
        tmdbData: Map<String, TmdbMetadata>,
        titleExtractor: (T) -> String,
        enricher: (T, TmdbMetadata) -> T,
    ): List<T> {
        return items.map { item ->
            val title = titleExtractor(item).lowercase()
            val match = tmdbData.entries.find { it.key.lowercase() == title }
            if (match != null) enricher(item, match.value) else item
        }
    }

    /**
     * TMDB metadata container.
     */
    data class TmdbMetadata(
        val tmdbId: Int,
        val title: String,
        val overview: String?,
        val posterPath: String?,
        val backdropPath: String?,
        val rating: Double?,
        val releaseDate: String?,
        val genres: List<String>,
        val runtime: Int?,
    )

    /**
     * Build a search key for TMDB lookup.
     */
    fun buildTmdbSearchKey(title: String, year: Int? = null): String {
        return if (year != null) "$title $year" else title
    }

    /**
     * Check if content needs metadata enrichment.
     */
    fun <T> needsEnrichment(
        item: T,
        hasOverview: (T) -> Boolean,
        hasPoster: (T) -> Boolean,
        hasRating: (T) -> Boolean,
    ): Boolean {
        return !hasOverview(item) || !hasPoster(item) || !hasRating(item)
    }
}
