package com.betterstreamflix.data

import android.util.LruCache
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory LRU cache for provider responses.
 * Reduces network calls for frequently accessed data.
 */
class ResponseCache(maxSize: Int = 50) {

    private val cache = LruCache<String, CacheEntry<*>>(maxSize)
    private val mutex = Mutex()

    /**
     * Get a cached value if it exists and hasn't expired.
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun <T> get(key: String): T? {
        return mutex.withLock {
            val entry = cache.get(key) as? CacheEntry<T>
            if (entry != null && !entry.isExpired()) {
                entry.lastAccessed = System.currentTimeMillis()
                entry.value
            } else {
                if (entry != null) cache.remove(key)
                null
            }
        }
    }

    /**
     * Put a value into the cache with a TTL.
     */
    suspend fun <T> put(key: String, value: T, ttlMs: Long = DEFAULT_TTL_MS) {
        mutex.withLock {
            cache.put(key, CacheEntry(value, ttlMs))
        }
    }

    /**
     * Remove a specific key from the cache.
     */
    suspend fun remove(key: String) {
        mutex.withLock { cache.remove(key) }
    }

    /**
     * Clear all cached entries.
     */
    fun clear() {
        cache.evictAll()
    }

    /**
     * Get the current cache size.
     */
    fun size(): Int = cache.size()

    companion object {
        const val DEFAULT_TTL_MS = 5 * 60 * 1000L // 5 minutes
    }
}

/**
 * A cache entry with TTL and last-accessed tracking.
 */
private data class CacheEntry<T>(
    val value: T,
    private val ttlMs: Long,
    var createdAt: Long = System.currentTimeMillis(),
    var lastAccessed: Long = System.currentTimeMillis(),
) {
    fun isExpired(): Boolean {
        return System.currentTimeMillis() - createdAt > ttlMs
    }
}
