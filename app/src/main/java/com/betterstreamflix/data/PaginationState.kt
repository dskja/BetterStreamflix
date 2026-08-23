package com.betterstreamflix.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pagination state — manages pagination state for lists with
 * page tracking, loading states, and has-more detection.
 */
class PaginationState<T>(
    private val pageSize: Int = 20,
    private val loader: suspend (page: Int, pageSize: Int) -> List<T>,
) {
    private val _items = MutableStateFlow<List<T>>(emptyList())
    val items: Flow<List<T>> = _items.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: Flow<Boolean> = _isLoading.asStateFlow()

    private val _hasMore = MutableStateFlow(true)
    val hasMore: Flow<Boolean> = _hasMore.asStateFlow()

    private val _error = MutableStateFlow<Throwable?>(null)
    val error: Flow<Throwable?> = _error.asStateFlow()

    private var currentPage = 0

    /**
     * Load the first page (reset).
     */
    suspend fun refresh() {
        currentPage = 0
        _hasMore.value = true
        loadNextPage()
    }

    /**
     * Load the next page.
     */
    suspend fun loadNextPage() {
        if (_isLoading.value || !_hasMore.value) return

        _isLoading.value = true
        _error.value = null

        try {
            val newItems = loader(currentPage, pageSize)
            if (newItems.size < pageSize) {
                _hasMore.value = false
            }
            if (currentPage == 0) {
                _items.value = newItems
            } else {
                _items.value = _items.value + newItems
            }
            currentPage++
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            _error.value = e
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Get current items.
     */
    fun getCurrentItems(): List<T> = _items.value

    /**
     * Check if currently loading.
     */
    fun isLoading(): Boolean = _isLoading.value

    /**
     * Check if more pages are available.
     */
    fun hasMore(): Boolean = _hasMore.value

    /**
     * Reset to initial state.
     */
    fun reset() {
        currentPage = 0
        _items.value = emptyList()
        _hasMore.value = true
        _error.value = null
        _isLoading.value = false
    }
}
