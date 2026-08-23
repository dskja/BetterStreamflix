package com.betterstreamflix.data

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Retry logic for network/extractor calls with exponential backoff.
 */
object RetryHelper {

    /**
     * Execute a block with retry logic.
     * Retries up to maxRetries times with exponential backoff.
     * Returns null if all retries fail.
     */
    suspend fun <T> retryWithBackoff(
        maxRetries: Int = 3,
        initialDelayMs: Long = 1000L,
        maxDelayMs: Long = 10000L,
        block: suspend (attempt: Int) -> T?,
    ): T? {
        var lastResult: T? = null
        repeat(maxRetries) { attempt ->
            if (attempt > 0) {
                val delayMs = (initialDelayMs * (1L shl (attempt - 1))).coerceAtMost(maxDelayMs)
                delay(delayMs)
            }
            lastResult = block(attempt)
            if (lastResult != null) return lastResult
        }
        return lastResult
    }

    /**
     * Execute a block with retry logic, throwing on failure.
     */
    suspend fun <T> retryOrThrow(
        maxRetries: Int = 3,
        initialDelayMs: Long = 1000L,
        block: suspend (attempt: Int) -> T,
    ): T {
        var lastError: Exception? = null
        repeat(maxRetries) { attempt ->
            try {
                return block(attempt)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                if (attempt < maxRetries - 1) {
                    val delayMs = initialDelayMs * (1L shl attempt)
                    delay(delayMs)
                }
            }
        }
        throw lastError ?: RuntimeException("Retry failed without exception")
    }

    /**
     * Execute a block with a timeout, returning null on timeout.
     */
    suspend fun <T> withTimeout(
        timeoutMs: Long = 30000L,
        block: suspend () -> T,
    ): T? {
        return withTimeoutOrNull(timeoutMs) { block() }
    }
}
