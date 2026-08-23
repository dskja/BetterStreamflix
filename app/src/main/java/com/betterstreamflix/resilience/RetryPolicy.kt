package com.betterstreamflix.resilience

import kotlinx.coroutines.delay
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Retry policy with exponential backoff and jitter.
 * Used for network requests and provider operations.
 */
object RetryPolicy {

    /**
     * Execute a block with retry and exponential backoff.
     */
    suspend fun <T> withRetry(
        maxRetries: Int = 3,
        initialDelayMs: Long = 1_000,
        maxDelayMs: Long = 10_000,
        jitterFactor: Double = 0.2,
        block: suspend () -> T,
    ): Result<T> {
        var lastException: Exception? = null
        for (attempt in 0..maxRetries) {
            try {
                return Result.success(block())
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                lastException = e
                if (attempt < maxRetries) {
                    val baseDelay = (initialDelayMs * 2.0.pow(attempt)).roundToLong().coerceAtMost(maxDelayMs)
                    val jitter = (baseDelay * jitterFactor * (Math.random() * 2 - 1)).toLong()
                    val delayMs = (baseDelay + jitter).coerceAtLeast(0)
                    delay(delayMs)
                }
            }
        }
        return Result.failure(lastException ?: Exception("Unknown retry failure"))
    }

    /**
     * Execute a block with retry, returning a fallback on failure.
     */
    suspend fun <T> withRetryAndFallback(
        maxRetries: Int = 3,
        fallback: T,
        block: suspend () -> T,
    ): T {
        return withRetry(maxRetries = maxRetries, block = block).getOrDefault(fallback)
    }

    /**
     * Check if an exception is retryable.
     */
    fun isRetryable(exception: Exception): Boolean {
        return when (exception) {
            is java.net.SocketTimeoutException -> true
            is java.net.UnknownHostException -> true
            is javax.net.ssl.SSLException -> true
            is java.io.IOException -> true
            is kotlinx.coroutines.CancellationException -> false
            else -> false
        }
    }
}
