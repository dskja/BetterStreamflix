package com.betterstreamflix.content

/**
 * Content deduplicator — removes duplicate content entries based on
 * title similarity and metadata matching.
 */
object ContentDeduplicator {

    /**
     * Deduplicate content by title similarity.
     */
    fun <T> deduplicateByTitle(
        items: List<T>,
        titleExtractor: (T) -> String,
        similarityThreshold: Double = 0.9,
    ): List<T> {
        val seen = mutableListOf<Pair<String, T>>()
        val result = mutableListOf<T>()

        for (item in items) {
            val title = titleExtractor(item).lowercase().trim()
            val isDuplicate = seen.any { (existingTitle, _) ->
                com.betterstreamflix.search.FuzzySearch.similarity(title, existingTitle) >= similarityThreshold
            }
            if (!isDuplicate) {
                seen.add(title to item)
                result.add(item)
            }
        }
        return result
    }

    /**
     * Find duplicate groups in content.
     */
    fun <T> findDuplicates(
        items: List<T>,
        titleExtractor: (T) -> String,
        similarityThreshold: Double = 0.9,
    ): List<List<T>> {
        val groups = mutableListOf<MutableList<T>>()
        val assigned = mutableSetOf<Int>()

        for (i in items.indices) {
            if (i in assigned) continue
            val group = mutableListOf(items[i])
            assigned.add(i)

            for (j in (i + 1) until items.size) {
                if (j in assigned) continue
                val sim = com.betterstreamflix.search.FuzzySearch.similarity(
                    titleExtractor(items[i]).lowercase(),
                    titleExtractor(items[j]).lowercase(),
                )
                if (sim >= similarityThreshold) {
                    group.add(items[j])
                    assigned.add(j)
                }
            }
            if (group.size > 1) groups.add(group)
        }
        return groups
    }

    /**
     * Merge duplicate items, keeping the one with most metadata.
     */
    fun <T> mergeDuplicates(
        items: List<T>,
        metadataScoreExtractor: (T) -> Int,
    ): T {
        return items.maxByOrNull(metadataScoreExtractor) ?: items.firstOrNull()
            ?: throw IllegalArgumentException("Cannot merge duplicates from empty list")
    }
}
