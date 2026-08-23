package com.betterstreamflix.search

/**
 * Content recommender — provides recommendations based on watch history,
 * favorites, and genre preferences.
 */
object ContentRecommender {

    /**
     * Get recommended genres based on watch history.
     */
    fun getRecommendedGenres(
        watchHistoryGenres: List<String>,
        favoriteGenres: List<String>,
    ): List<String> {
        val genreScores = mutableMapOf<String, Int>()

        // Weight watch history higher than favorites
        watchHistoryGenres.forEach { genre ->
            genreScores[genre] = (genreScores[genre] ?: 0) + 3
        }
        favoriteGenres.forEach { genre ->
            genreScores[genre] = (genreScores[genre] ?: 0) + 2
        }

        return genreScores.entries
            .sortedByDescending { it.value }
            .map { it.key }
    }

    /**
     * Get recommended content based on similar titles.
     */
    fun <T> getSimilarContent(
        target: T,
        allContent: List<T>,
        titleExtractor: (T) -> String,
        genreExtractor: (T) -> List<String>,
        maxResults: Int = 10,
    ): List<T> {
        val targetGenres = genreExtractor(target).map { it.lowercase() }.toSet()
        val targetTitle = titleExtractor(target)

        return allContent
            .filter { titleExtractor(it) != targetTitle }
            .map { item ->
                val itemGenres = genreExtractor(item).map { it.lowercase() }.toSet()
                val genreScore = targetGenres.intersect(itemGenres).size
                val titleScore = FuzzySearch.similarity(targetTitle, titleExtractor(item))
                item to (genreScore.toDouble() * 0.7 + titleScore * 0.3)
            }
            .sortedByDescending { it.second }
            .take(maxResults)
            .map { it.first }
    }

    /**
     * Get trending content based on watch count.
     */
    fun <T> getTrending(
        items: List<T>,
        watchCountExtractor: (T) -> Int,
        recencyExtractor: (T) -> Long,
        maxResults: Int = 20,
    ): List<T> {
        val now = System.currentTimeMillis()
        return items
            .map { item ->
                val watchCount = watchCountExtractor(item)
                val recency = recencyExtractor(item)
                val daysSinceWatch = ((now - recency) / (24 * 60 * 60 * 1000)).coerceAtLeast(1)
                val trendingScore = watchCount.toDouble() / daysSinceWatch
                item to trendingScore
            }
            .sortedByDescending { it.second }
            .take(maxResults)
            .map { it.first }
    }
}
