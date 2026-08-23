package com.betterstreamflix.utils

import android.content.Context
import android.util.Log
import com.betterstreamflix.providers.Provider
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.cancellation.CancellationException

/**
 * Utility for executing provider calls with error recovery.
 * Wraps provider suspend calls with timeout, error classification,
 * and retry logic.
 */
object ProviderErrorRecovery {

    private const val TAG = "ProviderErrorRecovery"
    private const val DEFAULT_TIMEOUT_MS = 30_000L
    private const val MAX_RETRIES = 2

    /**
     * Result wrapper for provider calls.
     */
    sealed class Result<out T> {
        data class Success<T>(val data: T) : Result<T>()
        data class Error(val networkError: NetworkError, val providerName: String) : Result<Nothing>()
    }

    /**
     * Execute a provider call with timeout and error classification.
     * Does NOT retry — use executeWithRetry for that.
     */
    suspend fun <T> execute(
        provider: Provider,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        block: suspend () -> T
    ): Result<T> {
        return try {
            val data = withTimeout(timeoutMs) { block() }
            Result.Success(data)
        } catch (e: CancellationException) {
            throw e
        } catch (e: TimeoutCancellationException) {
            Log.w(TAG, "Provider ${provider.name} timed out after ${timeoutMs}ms")
            Result.Error(NetworkError.Timeout, provider.name)
        } catch (e: Exception) {
            Log.e(TAG, "Provider ${provider.name} failed: ${e.message}", e)
            Result.Error(NetworkError.from(e), provider.name)
        }
    }

    /**
     * Execute a provider call with retry logic.
     * Retries up to MAX_RETRIES times with exponential backoff.
     */
    suspend fun <T> executeWithRetry(
        provider: Provider,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        maxRetries: Int = MAX_RETRIES,
        block: suspend () -> T
    ): Result<T> {
        var lastError: Result.Error? = null
        repeat(maxRetries + 1) { attempt ->
            if (attempt > 0) {
                val delayMs = (1000L * (1 shl (attempt - 1))) // 1s, 2s, 4s...
                Log.d(TAG, "Provider ${provider.name} retry $attempt after ${delayMs}ms")
                kotlinx.coroutines.delay(delayMs)
            }
            val result = execute(provider, timeoutMs, block)
            when (result) {
                is Result.Success -> return result
                is Result.Error -> {
                    lastError = result
                    // Don't retry on NoConnection or SSL errors
                    if (result.networkError is NetworkError.NoConnection ||
                        result.networkError is NetworkError.SslError
                    ) {
                        return result
                    }
                }
            }
        }
        return lastError ?: Result.Error(NetworkError.Unknown("Max retries exceeded"), provider.name)
    }

    /**
     * Check network connectivity before attempting a provider call.
     */
    fun isOnline(context: Context): Boolean = NetworkError.isOnline(context)
}
