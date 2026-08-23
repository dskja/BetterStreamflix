package com.betterstreamflix.search

/**
 * Fuzzy search — provides fuzzy string matching with similarity scoring
 * for typo-tolerant search.
 */
object FuzzySearch {

    /**
     * Calculate Levenshtein distance between two strings.
     */
    fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost,
                )
            }
        }
        return dp[a.length][b.length]
    }

    /**
     * Calculate similarity score (0.0 to 1.0).
     */
    fun similarity(a: String, b: String): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val maxLen = maxOf(a.length, b.length)
        val distance = levenshtein(a.lowercase(), b.lowercase())
        return 1.0 - (distance.toDouble() / maxLen)
    }

    /**
     * Check if a string contains a fuzzy match of the query.
     */
    fun containsFuzzy(text: String, query: String, threshold: Double = 0.7): Boolean {
        if (query.isBlank()) return true
        val textLower = text.lowercase()
        val queryLower = query.lowercase()

        if (textLower.contains(queryLower)) return true

        // Try matching against words
        val words = textLower.split(Regex("\\s+"))
        return words.any { word ->
            similarity(word, queryLower) >= threshold ||
            (queryLower.length > 3 && word.contains(queryLower.take(3)))
        }
    }

    /**
     * Search and rank results by fuzzy relevance.
     */
    fun <T> searchRanked(
        items: List<T>,
        query: String,
        textExtractor: (T) -> String,
        threshold: Double = 0.5,
    ): List<Pair<T, Double>> {
        if (query.isBlank()) return items.map { it to 1.0 }

        return items.mapNotNull { item ->
            val text = textExtractor(item)
            val score = similarity(text, query)
            if (score >= threshold || containsFuzzy(text, query, 0.7)) {
                item to maxOf(score, if (containsFuzzy(text, query, 0.7)) 0.7 else score)
            } else null
        }.sortedByDescending { it.second }
    }
}
