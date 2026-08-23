package com.betterstreamflix.providers

import com.betterstreamflix.data.RateLimiters
import com.betterstreamflix.data.ResponseCache
import com.betterstreamflix.data.RetryHelper
import com.betterstreamflix.data.Result

/**
 * Base class for all providers with built-in caching, rate limiting, and retry.
 * Providers extend this to get consistent error handling and performance optimizations.
 */
abstract class EnhancedProvider : Provider {

    protected val responseCache = ResponseCache()
    protected val rateLimiter = RateLimiters.forProvider(name)

    /**
     * Cached API call with rate limiting and retry.
     */
    protected suspend fun <T> cachedCall(
        cacheKey: String,
        ttlMs: Long = ResponseCache.DEFAULT_TTL_MS,
        block: suspend () -> T,
    ): T {
        // Check cache first
        responseCache.get<T>(cacheKey)?.let { return it }

        // Rate limit + execute
        val result = rateLimiter.execute {
            RetryHelper.retryOrThrow(maxRetries = 2) { block() }
        }

        // Cache the result
        responseCache.put(cacheKey, result, ttlMs)
        return result
    }

    /**
     * Safe API call with Result wrapper.
     */
    protected suspend fun <T> safeCall(block: suspend () -> T): Result<T> {
        return Result.runCatching {
            rateLimiter.execute { block() }
        }
    }

    /**
     * Clear cached responses for this provider.
     */
    fun clearCache() = responseCache.clear()
}
