package com.betterstreamflix.imageloading

import android.graphics.Bitmap
import android.util.LruCache

/**
 * Image memory cache — LRU memory cache specifically for images
 * with size-based eviction.
 */
class ImageMemoryCache(maxSizeKb: Int) {

    private val cache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(maxSizeKb) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return value.byteCount / 1024
        }
    }

    /**
     * Get a bitmap from cache.
     */
    fun get(key: String): Bitmap? = cache.get(key)

    /**
     * Put a bitmap into cache.
     */
    fun put(key: String, bitmap: Bitmap) {
        if (get(key) == null) {
            cache.put(key, bitmap)
        }
    }

    /**
     * Remove a bitmap from cache.
     */
    fun remove(key: String) {
        cache.remove(key)
    }

    /**
     * Clear the cache.
     */
    fun clear() {
        cache.evictAll()
    }

    /**
     * Get the current cache size in KB.
     */
    fun size(): Int = cache.size()

    /**
     * Get the max cache size in KB.
     */
    fun maxSize(): Int = cache.maxSize()

    /**
     * Check if cache contains a key.
     */
    fun contains(key: String): Boolean = cache.snapshot().containsKey(key)

    /**
     * Get all keys.
     */
    fun keys(): Set<String> = cache.snapshot().keys

    /**
     * Get cache hit count.
     */
    fun hitCount(): Int = cache.hitCount()

    /**
     * Get cache miss count.
     */
    fun missCount(): Int = cache.missCount()

    companion object {
        /**
         * Create a cache that uses a fraction of available memory.
         */
        fun createWithMemoryFraction(fraction: Float = 0.15f): ImageMemoryCache {
            val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
            return ImageMemoryCache((maxMemory * fraction).toInt())
        }
    }
}
