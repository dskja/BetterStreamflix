package com.betterstreamflix.performance

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache

/**
 * Bitmap cache — manages in-memory bitmap storage with size limits
 * based on available memory.
 */
object BitmapCache {

    private var cache: LruCache<String, Bitmap>? = null

    /**
     * Initialize the cache with a fraction of available memory.
     */
    fun init(context: Context) {
        if (cache != null) return

        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val maxMemory = activityManager?.memoryClass?.times(1024 * 1024) ?: (16 * 1024 * 1024)
        val cacheSize = maxMemory / 8

        cache = object : LruCache<String, Bitmap>(cacheSize) {
            override fun sizeOf(key: String, value: Bitmap): Int {
                return value.allocationByteCount
            }
        }
    }

    /**
     * Put a bitmap in the cache.
     */
    fun put(key: String, bitmap: Bitmap) {
        cache?.put(key, bitmap)
    }

    /**
     * Get a bitmap from the cache.
     */
    fun get(key: String): Bitmap? = cache?.get(key)

    /**
     * Remove a bitmap from the cache.
     */
    fun remove(key: String) {
        cache?.remove(key)
    }

    /**
     * Clear the cache.
     */
    fun clear() {
        cache?.evictAll()
    }

    /**
     * Get cache size.
     */
    fun size(): Int = cache?.size() ?: 0

    /**
     * Get max cache size.
     */
    fun maxSize(): Int = cache?.maxSize() ?: 0
}
