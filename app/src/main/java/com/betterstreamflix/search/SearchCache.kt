package com.betterstreamflix.search

/**
 * Search cache — caches search results to avoid re-querying providers
 * for recent searches.
 */
object SearchCache {

    private val cache = mutableMapOf<String, CacheEntry>()
    private var maxCacheSize = 50
    private var cacheTtlMs = 5 * 60 * 1000L // 5 minutes

    data class CacheEntry(
        val results: List<*>,
        val timestamp: Long,
    )

    /**
     * Cache search results.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> put(key: String, results: List<T>) {
        if (cache.size >= maxCacheSize) {
            // Remove oldest entry
            cache.entries.minByOrNull { it.value.timestamp }?.key?.let { cache.remove(it) }
        }
        cache[key] = CacheEntry(results, System.currentTimeMillis())
    }

    /**
     * Get cached search results.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String): List<T>? {
        val entry = cache[key] ?: return null
        if (System.currentTimeMillis() - entry.timestamp > cacheTtlMs) {
            cache.remove(key)
            return null
        }
        return entry.results as? List<T>
    }

    /**
     * Check if a key is cached and valid.
     */
    fun isValid(key: String): Boolean {
        val entry = cache[key] ?: return false
        return System.currentTimeMillis() - entry.timestamp <= cacheTtlMs
    }

    /**
     * Clear the cache.
     */
    fun clear() {
        cache.clear()
    }

    /**
     * Set cache TTL.
     */
    fun setTtl(ttlMs: Long) {
        cacheTtlMs = ttlMs
    }

    /**
     * Get cache size.
     */
    fun size(): Int = cache.size
}
