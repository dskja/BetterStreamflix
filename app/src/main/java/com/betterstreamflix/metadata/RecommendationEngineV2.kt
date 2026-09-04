package com.betterstreamflix.metadata

import com.betterstreamflix.database.AppDataRepository
import kotlinx.coroutines.flow.first

/**
 * Recommendation engine v2 — TMDB metadata + watch-history scoring.
 */
object RecommendationEngineV2 {

    suspend fun scoreByWatchHistory(
        context: android.content.Context,
        availableContent: List<RecommendableItem>,
    ): List<RecommendableItem> {
        val history = AppDataRepository(context).getWatchHistory().first()
        if (history.isEmpty()) return availableContent.take(20)

        val genreWeights = mutableMapOf<String, Double>()
        history.forEach { entry ->
            MetadataCache.get(entry.title, entry.type)?.genres?.forEach { genre ->
                val progressWeight = (entry.progressPercent / 100.0).coerceIn(0.1, 1.0)
                genreWeights[genre] = (genreWeights[genre] ?: 0.0) + progressWeight
            }
        }

        return availableContent.mapNotNull { item ->
            val metadata = MetadataCache.get(item.title, item.type) ?: return@mapNotNull null
            val score = metadata.genres.sumOf { genre -> genreWeights[genre] ?: 0.0 }
            item to score
        }
            .sortedByDescending { it.second }
            .map { it.first }
            .take(20)
    }
}
