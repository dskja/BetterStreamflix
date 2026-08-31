package com.betterstreamflix.search

import android.os.Handler
import android.os.Looper

/**
 * Search debounce manager — prevents rapid search calls by
 * debouncing search input.
 */
class SearchDebounceManager(
    private val onSearch: (String) -> Unit,
    private val delayMs: Long = 400,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var pendingRunnable: Runnable? = null
    private var lastQuery = ""

    /**
     * Submit a search query. Will be debounced.
     */
    fun submit(query: String) {
        pendingRunnable?.let(handler::removeCallbacks)
        lastQuery = query
        val runnable = Runnable { onSearch(query) }
        pendingRunnable = runnable
        handler.postDelayed(runnable, delayMs)
    }

    /**
     * Force immediate search.
     */
    fun searchNow(query: String) {
        pendingRunnable?.let(handler::removeCallbacks)
        pendingRunnable = null
        lastQuery = query
        onSearch(query)
    }

    /**
     * Reset the debounce manager.
     */
    fun reset() {
        pendingRunnable?.let(handler::removeCallbacks)
        pendingRunnable = null
        lastQuery = ""
    }

    /**
     * Get the last query.
     */
    fun getLastQuery(): String = lastQuery
}
