package com.betterstreamflix.metadata

/**
 * Recommendation engine — suggests content based on watch history and favorites.
 */
object RecommendationEngine {

    /**
     * Generate recommendations based on watched genres.
     */
    fun recommendByGenre(
        watchedTitles: List<Pair<String, String>>, // (title, type)
        availableContent: List<RecommendableItem>,
    ): List<RecommendableItem> {
        // Get genres from watched content
        val watchedGenres = mutableMapOf<String, Int>()
        watchedTitles.forEach { (title, type) ->
            MetadataCache.get(title, type)?.genres?.forEach { genre ->
                watchedGenres[genre] = (watchedGenres[genre] ?: 0) + 1
            }
        }

        if (watchedGenres.isEmpty()) return availableContent.take(20)

        // Score available content by genre overlap
        return availableContent.mapNotNull { item ->
            val metadata = MetadataCache.get(item.title, item.type) ?: return@mapNotNull null
            val score = metadata.genres.sumOf { genre ->
                (watchedGenres[genre] ?: 0)
            }
            item to score
        }.sortedByDescending { it.second }
            .map { it.first }
            .take(20)
    }

    /**
     * Get trending content (most watched in history).
     */
    fun getTrending(
        watchHistory: List<com.betterstreamflix.utils.WatchHistoryManager.HistoryItem>,
    ): List<String> {
        return watchHistory
            .groupingBy { it.title }
            .eachCount()
            .entries.sortedByDescending { it.value }
            .take(10)
            .map { it.key }
    }
}

/**
 * An item that can be recommended.
 */
data class RecommendableItem(
    val title: String,
    val type: String,
    val providerName: String,
    val thumbnailUrl: String?,
)
