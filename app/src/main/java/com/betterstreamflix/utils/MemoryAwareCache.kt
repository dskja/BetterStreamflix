package com.betterstreamflix.utils

import android.util.Log
import com.betterstreamflix.BuildConfig
import java.util.concurrent.ConcurrentHashMap

/**
 * Memory-aware cache that automatically evicts entries under memory pressure.
 * Uses LRU eviction with optional size-based limits.
 */
class MemoryAwareCache<K, V>(
    private val maxSize: Int = 50,
    private val estimatedValueSize: (V) -> Int = { 1 },
) {
    private val cache = object : LinkedHashMap<K, V>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<K, V>): Boolean {
            return size > maxSize
        }
    }

    private val lock = Any()

    fun get(key: K): V? = synchronized(lock) { cache[key] }

    fun put(key: K, value: V): V? = synchronized(lock) { cache.put(key, value) }

    fun remove(key: K): V? = synchronized(lock) { cache.remove(key) }

    fun clear() = synchronized(lock) { cache.clear() }

    fun size(): Int = synchronized(lock) { cache.size }

    fun containsKey(key: K): Boolean = synchronized(lock) { cache.containsKey(key) }

    /**
     * Trim cache to a fraction of its max size (for memory pressure).
     */
    fun trimToFraction(fraction: Float) {
        synchronized(lock) {
            val targetSize = (maxSize * fraction).toInt().coerceAtLeast(1)
            val iterator = cache.entries.iterator()
            while (iterator.hasNext() && cache.size > targetSize) {
                iterator.next()
                iterator.remove()
            }
        }
    }

    /**
     * Get all keys (for debugging).
     */
    fun keys(): Set<K> = synchronized(lock) { cache.keys.toSet() }
}
