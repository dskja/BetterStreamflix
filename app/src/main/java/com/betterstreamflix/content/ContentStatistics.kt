package com.betterstreamflix.content

/**
 * Content statistics — tracks and computes statistics about
 * user's watching habits.
 */
object ContentStatistics {

    data class WatchStats(
        val totalItemsWatched: Int,
        val totalWatchTimeMs: Long,
        val averageCompletionRate: Float,
        val topGenres: List<GenreCount>,
        val topProviders: List<ProviderCount>,
        val watchStreakDays: Int,
        val lastWatchedDate: Long,
    )

    data class GenreCount(val genre: String, val count: Int)
    data class ProviderCount(val provider: String, val count: Int)

    /**
     * Compute watch statistics from history items.
     */
    fun computeStats(
        history: List<HistoryItem>,
    ): WatchStats {
        if (history.isEmpty()) {
            return WatchStats(0, 0, 0f, emptyList(), emptyList(), 0, 0)
        }

        val totalItems = history.size
        val totalWatchTime = history.sumOf { it.watchTimeMs }
        val avgCompletion = history.map { it.progressPercent }.average().toFloat()

        val genreCounts = history.flatMap { it.genres }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(5)
            .map { GenreCount(it.key, it.value) }

        val providerCounts = history.groupingBy { it.providerName }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .map { ProviderCount(it.key, it.value) }

        val streak = calculateWatchStreak(history.map { it.watchedAt })
        val lastWatched = history.maxOf { it.watchedAt }

        return WatchStats(
            totalItemsWatched = totalItems,
            totalWatchTimeMs = totalWatchTime,
            averageCompletionRate = avgCompletion,
            topGenres = genreCounts,
            topProviders = providerCounts,
            watchStreakDays = streak,
            lastWatchedDate = lastWatched,
        )
    }

    /**
     * Format total watch time for display.
     */
    fun formatWatchTime(ms: Long): String {
        val hours = ms / (60 * 60 * 1000)
        val days = hours / 24
        return when {
            days > 0 -> "${days}d ${hours % 24}h"
            hours > 0 -> "${hours}h"
            else -> "${ms / (60 * 1000)}m"
        }
    }

    private fun calculateWatchStreak(watchTimestamps: List<Long>): Int {
        if (watchTimestamps.isEmpty()) return 0
        val calendar = java.util.Calendar.getInstance()
        val days = watchTimestamps.map { timestamp ->
            calendar.timeInMillis = timestamp
            "${calendar.get(java.util.Calendar.YEAR)}-${calendar.get(java.util.Calendar.DAY_OF_YEAR)}"
        }.toSet().sortedDescending()

        var streak = 0
        var expectedDay = java.util.Calendar.getInstance()
        for (dayStr in days) {
            val parts = dayStr.split("-")
            val year = parts.getOrNull(0)?.toIntOrNull()
            val day = parts.getOrNull(1)?.toIntOrNull()
            if (year == null || day == null) continue
            calendar.set(year, 0, day)
            if (calendar.get(java.util.Calendar.YEAR) == expectedDay.get(java.util.Calendar.YEAR) &&
                calendar.get(java.util.Calendar.DAY_OF_YEAR) == expectedDay.get(java.util.Calendar.DAY_OF_YEAR)) {
                streak++
                expectedDay.add(java.util.Calendar.DAY_OF_YEAR, -1)
            } else {
                break
            }
        }
        return streak
    }

    data class HistoryItem(
        val videoId: String,
        val title: String,
        val providerName: String,
        val watchedAt: Long,
        val progressPercent: Float,
        val watchTimeMs: Long,
        val genres: List<String>,
    )
}
