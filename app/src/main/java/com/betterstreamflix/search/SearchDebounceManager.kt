package com.betterstreamflix.search

/**
 * Search debounce manager — prevents rapid search calls by
 * debouncing search input.
 */
class SearchDebounceManager(
    private val onSearch: (String) -> Unit,
    private val delayMs: Long = 400,
) {
    private var lastQuery = ""
    private var lastSearchTime = 0L

    /**
     * Submit a search query. Will be debounced.
     */
    fun submit(query: String) {
        if (query == lastQuery) return
        lastQuery = query

        val now = System.currentTimeMillis()
        val elapsed = now - lastSearchTime

        if (elapsed >= delayMs) {
            lastSearchTime = now
            onSearch(query)
        }
    }

    /**
     * Force immediate search.
     */
    fun searchNow(query: String) {
        lastQuery = query
        lastSearchTime = System.currentTimeMillis()
        onSearch(query)
    }

    /**
     * Reset the debounce manager.
     */
    fun reset() {
        lastQuery = ""
        lastSearchTime = 0L
    }

    /**
     * Get the last query.
     */
    fun getLastQuery(): String = lastQuery
}
