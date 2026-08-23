package com.betterstreamflix.performance

import android.content.Context
import androidx.collection.LruCache

/**
 * Memory cache manager — provides size-bounded LRU caches for
 * frequently accessed data like parsed HTML, search results, etc.
 */
object MemoryCacheManager {

    private val htmlCache = LruCache<String, String>(50)
    private val searchCache = LruCache<String, List<*>>(20)
    private val imageListCache = LruCache<String, List<*>>(10)

    /**
     * Cache HTML content.
     */
    fun putHtml(key: String, html: String) {
        htmlCache.put(key, html)
    }

    /**
     * Get cached HTML.
     */
    fun getHtml(key: String): String? = htmlCache.get(key)

    /**
     * Cache search results.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> putSearchResults(key: String, results: List<T>) {
        searchCache.put(key, results)
    }

    /**
     * Get cached search results.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getSearchResults(key: String): List<T>? {
        return searchCache.get(key) as? List<T>
    }

    /**
     * Cache image lists (e.g., season episodes).
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> putImageList(key: String, list: List<T>) {
        imageListCache.put(key, list)
    }

    /**
     * Get cached image list.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getImageList(key: String): List<T>? {
        return imageListCache.get(key) as? List<T>
    }

    /**
     * Clear all caches.
     */
    fun clearAll() {
        htmlCache.evictAll()
        searchCache.evictAll()
        imageListCache.evictAll()
    }

    /**
     * Get total cache size.
     */
    fun totalSize(): Int {
        return htmlCache.size() + searchCache.size() + imageListCache.size()
    }
}
