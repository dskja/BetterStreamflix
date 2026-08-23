package com.betterstreamflix.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

/**
 * Simple rate limiter for provider requests.
 * Ensures minimum delay between requests to avoid being blocked.
 */
class RateLimiter(
    private val minIntervalMs: Long = 500L,
) {
    private val lastRequestTime = AtomicLong(0)
    private val mutex = Mutex()

    /**
     * Acquire a slot, waiting if necessary to respect the rate limit.
     */
    suspend fun acquire() {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val elapsed = now - lastRequestTime.get()
            if (elapsed < minIntervalMs) {
                delay(minIntervalMs - elapsed)
            }
            lastRequestTime.set(System.currentTimeMillis())
        }
    }

    /**
     * Execute a block with rate limiting.
     */
    suspend fun <T> execute(block: suspend () -> T): T {
        acquire()
        return block()
    }
}

/**
 * Provider-specific rate limiters.
 */
object RateLimiters {
    private val limiters = mutableMapOf<String, RateLimiter>()

    /**
     * Get or create a rate limiter for a provider.
     */
    fun forProvider(providerName: String, minIntervalMs: Long = 500L): RateLimiter {
        return limiters.getOrPut(providerName) { RateLimiter(minIntervalMs) }
    }
}
