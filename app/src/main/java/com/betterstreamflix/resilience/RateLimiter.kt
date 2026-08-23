package com.betterstreamflix.resilience

import android.content.Context
import androidx.core.content.edit

/**
 * Rate limiter — prevents too many requests to a provider within a time window.
 */
class RateLimiter(
    private val minIntervalMs: Long = 1_000,
) {
    private val lastRequestTime = mutableMapOf<String, Long>()

    /**
     * Check if a request is allowed for the given key.
     */
    fun isAllowed(key: String): Boolean {
        val now = System.currentTimeMillis()
        val lastTime = lastRequestTime[key] ?: 0
        return now - lastTime >= minIntervalMs
    }

    /**
     * Record a request for the given key.
     */
    fun recordRequest(key: String) {
        lastRequestTime[key] = System.currentTimeMillis()
    }

    /**
     * Wait until a request is allowed, then record it.
     */
    suspend fun acquire(key: String) {
        val now = System.currentTimeMillis()
        val lastTime = lastRequestTime[key] ?: 0
        val waitMs = (minIntervalMs - (now - lastTime)).coerceAtLeast(0)
        if (waitMs > 0) {
            kotlinx.coroutines.delay(waitMs)
        }
        recordRequest(key)
    }

    /**
     * Reset the rate limiter for a key.
     */
    fun reset(key: String) {
        lastRequestTime.remove(key)
    }

    /**
     * Reset all rate limits.
     */
    fun resetAll() {
        lastRequestTime.clear()
    }
}
