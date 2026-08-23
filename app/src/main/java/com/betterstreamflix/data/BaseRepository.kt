package com.betterstreamflix.data

import com.betterstreamflix.utils.GlobalErrorHandler
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Base repository providing safe API call execution with error handling.
 * All repositories should extend this class.
 */
abstract class BaseRepository(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    /**
     * Execute a suspend API call safely, wrapping the result in Result.
     * Handles exceptions and dispatching.
     */
    protected suspend fun <T> safeApiCall(
        block: suspend () -> T,
    ): Result<T> {
        return try {
            val result = withContext(ioDispatcher) { block() }
            Result.Success(result)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            GlobalErrorHandler.logError(
                this::class.java.simpleName,
                "API call failed: ${e.message}",
                e,
            )
            Result.Error(ErrorType.from(e))
        }
    }

    /**
     * Execute a suspend API call with a fallback value on error.
     */
    protected suspend fun <T> safeApiCallWithFallback(
        fallback: T,
        block: suspend () -> T,
    ): T {
        return when (val result = safeApiCall(block)) {
            is Result.Success -> result.data
            else -> fallback
        }
    }
}
