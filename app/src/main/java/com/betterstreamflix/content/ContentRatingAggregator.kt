package com.betterstreamflix.content

/**
 * Content rating aggregator — aggregates ratings from multiple sources
 * (TMDB, IMDB, provider) into a unified score.
 */
object ContentRatingAggregator {

    data class AggregatedRating(
        val averageRating: Double,
        val voteCount: Int,
        val sources: Map<String, Double>,
    )

    /**
     * Aggregate ratings from multiple sources.
     */
    fun aggregateRatings(ratings: Map<String, Double>): AggregatedRating {
        if (ratings.isEmpty()) return AggregatedRating(0.0, 0, emptyMap())

        val average = ratings.values.average()
        return AggregatedRating(
            averageRating = average,
            voteCount = ratings.size,
            sources = ratings,
        )
    }

    /**
     * Normalize a rating to a 0-10 scale.
     */
    fun normalizeRating(rating: Double, maxScale: Double): Double {
        if (maxScale <= 0) return 0.0
        return (rating / maxScale * 10.0).coerceIn(0.0, 10.0)
    }

    /**
     * Format a rating for display.
     */
    fun formatRating(rating: Double): String {
        return if (rating > 0) String.format("%.1f", rating) else "N/A"
    }

    /**
     * Get a star rating string (e.g., "★★★★☆").
     */
    fun getStarRating(rating: Double, maxRating: Double = 10.0): String {
        val stars = ((rating / maxRating) * 5).toInt().coerceIn(0, 5)
        return "★".repeat(stars) + "☆".repeat(5 - stars)
    }

    /**
     * Get rating color based on score.
     */
    fun getRatingColor(rating: Double): RatingColor {
        return when {
            rating >= 7.5 -> RatingColor.GREEN
            rating >= 5.0 -> RatingColor.YELLOW
            rating >= 2.5 -> RatingColor.ORANGE
            rating > 0 -> RatingColor.RED
            else -> RatingColor.GRAY
        }
    }

    enum class RatingColor(val hex: String) {
        GREEN("#4CAF50"),
        YELLOW("#FFC107"),
        ORANGE("#FF9800"),
        RED("#F44336"),
        GRAY("#9E9E9E"),
    }
}
