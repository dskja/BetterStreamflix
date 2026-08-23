package com.betterstreamflix.performance

import android.content.Context
import androidx.core.content.edit

/**
 * Lazy loading helper — manages lazy initialization of expensive resources
 * with thread safety.
 */
class LazyInitializer<T>(private val initializer: () -> T) {
    @Volatile
    private var value: T? = null

    fun get(): T {
        return value ?: synchronized(this) {
            value ?: initializer().also { value = it }
        }
    }

    fun isInitialized(): Boolean = value != null

    fun reset() {
        value = null
    }
}

/**
 * Preload manager — preloads data for anticipated user actions.
 */
object PreloadManager {

    private val preloadedData = mutableMapOf<String, Any?>()

    /**
     * Preload data for a key.
     */
    fun <T> preload(key: String, data: T) {
        preloadedData[key] = data
    }

    /**
     * Get preloaded data.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String): T? {
        return preloadedData[key] as? T
    }

    /**
     * Check if data is preloaded.
     */
    fun isPreloaded(key: String): Boolean = preloadedData.containsKey(key)

    /**
     * Consume preloaded data (get and remove).
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> consume(key: String): T? {
        val data = preloadedData[key] as? T
        preloadedData.remove(key)
        return data
    }

    /**
     * Clear all preloaded data.
     */
    fun clear() {
        preloadedData.clear()
    }
}
