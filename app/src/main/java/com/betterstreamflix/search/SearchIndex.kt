package com.betterstreamflix.search

/**
 * Search index — builds an in-memory inverted index for fast
 * local content search.
 */
class SearchIndex<T> {

    private val items = mutableListOf<T>()
    private val index = mutableMapOf<String, MutableSet<Int>>()
    private var textExtractor: ((T) -> String)? = null

    /**
     * Set the text extractor for indexing.
     */
    fun setTextExtractor(extractor: (T) -> String) {
        textExtractor = extractor
    }

    /**
     * Add an item to the index.
     */
    fun add(item: T) {
        val extractor = textExtractor ?: return
        val id = items.size
        items.add(item)
        val text = extractor(item).lowercase()
        text.split(Regex("\\s+")).forEach { word ->
            if (word.isNotBlank()) {
                index.getOrPut(word) { mutableSetOf() }.add(id)
            }
        }
    }

    /**
     * Add multiple items.
     */
    fun addAll(newItems: List<T>) {
        newItems.forEach { add(it) }
    }

    /**
     * Search the index.
     */
    fun search(query: String): List<T> {
        if (query.isBlank()) return items.toList()

        val queryWords = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (queryWords.isEmpty()) return emptyList()

        val matchingIds = queryWords.map { word ->
            index.entries
                .filter { it.key.contains(word) || word.contains(it.key) }
                .flatMap { it.value }
                .toSet()
        }

        // Intersection of all word matches
        val resultIds = matchingIds.reduce { acc, set -> acc.intersect(set) }
        return resultIds.map { items[it] }
    }

    /**
     * Clear the index.
     */
    fun clear() {
        items.clear()
        index.clear()
    }

    /**
     * Get index size.
     */
    fun size(): Int = items.size
}
