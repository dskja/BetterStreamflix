package com.betterstreamflix.metadata

import android.content.Context
import android.util.LruCache

/**
 * In-memory metadata cache with TTL.
 * Avoids repeated TMDB API calls for the same content.
 */
object MetadataCache {

    private const val MAX_CACHE_SIZE = 100
    private const val TTL_MS = 24 * 60 * 60 * 1000L // 24 hours

    private data class CacheEntry(
        val metadata: TmdbMetadata?,
        val timestamp: Long = System.currentTimeMillis(),
    )

    private val cache = LruCache<String, CacheEntry>(MAX_CACHE_SIZE)

    /**
     * Get cached metadata by title and type.
     */
    fun get(title: String, type: String): TmdbMetadata? {
        val key = buildKey(title, type)
        val entry = cache.get(key) ?: return null
        if (System.currentTimeMillis() - entry.timestamp > TTL_MS) {
            cache.remove(key)
            return null
        }
        return entry.metadata
    }

    /**
     * Cache metadata.
     */
    fun put(title: String, type: String, metadata: TmdbMetadata?) {
        cache.put(buildKey(title, type), CacheEntry(metadata))
    }

    /**
     * Check if metadata is cached (even if null — negative caching).
     */
    fun isCached(title: String, type: String): Boolean {
        return cache.get(buildKey(title, type)) != null
    }

    /**
     * Clear all cached metadata.
     */
    fun clear() = cache.evictAll()

    /**
     * Get cache size.
     */
    fun size() = cache.size()

    private fun buildKey(title: String, type: String) = "${type.lowercase()}_${title.lowercase()}"
}
