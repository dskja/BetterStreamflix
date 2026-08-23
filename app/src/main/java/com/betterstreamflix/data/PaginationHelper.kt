package com.betterstreamflix.data

/**
 * Generic pagination helper for paged list loading.
 * Tracks current page, has-more flag, and loading state.
 */
class PaginationHelper(
    private val pageSize: Int = 20,
    private val prefetchDistance: Int = 5,
) {
    private var currentPage = 1
    private var isLoading = false
    private var hasMore = true
    private val mutex = kotlinx.coroutines.sync.Mutex()

    /**
     * Reset pagination to the first page.
     */
    fun reset() {
        currentPage = 1
        isLoading = false
        hasMore = true
    }

    /**
     * Check if more pages can be loaded.
     */
    fun canLoadMore(): Boolean = hasMore && !isLoading

    /**
     * Check if a load-more should be triggered based on scroll position.
     */
    fun shouldPrefetch(visibleItemCount: Int, totalItemCount: Int, firstVisibleItem: Int): Boolean {
        return canLoadMore() &&
            visibleItemCount + firstVisibleItem + prefetchDistance >= totalItemCount
    }

    /**
     * Start loading the next page. Returns the page to load, or null if already loading.
     */
    suspend fun startLoading(): Int? {
        mutex.lock()
        return try {
            if (isLoading || !hasMore) return null
            isLoading = true
            currentPage
        } finally {
            mutex.unlock()
        }
    }

    /**
     * Mark the current page as loaded successfully.
     * @param itemCount number of items returned. If less than pageSize, hasMore is set to false.
     */
    fun pageLoaded(itemCount: Int) {
        isLoading = false
        if (itemCount < pageSize) {
            hasMore = false
        } else {
            currentPage++
        }
    }

    /**
     * Mark the current page load as failed.
     */
    fun pageFailed() {
        isLoading = false
    }

    /**
     * Get current page number.
     */
    fun getCurrentPage(): Int = currentPage

    /**
     * Check if currently loading.
     */
    fun isLoading(): Boolean = isLoading
}
