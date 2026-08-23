package com.betterstreamflix.utils

import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlin.coroutines.CoroutineContext

/**
 * Global coroutine exception handler that logs unhandled exceptions
 * from viewModelScope and lifecycleScope launches.
 *
 * Usage:
 *   viewModelScope.launch(GlobalErrorHandler.handler) { ... }
 *   lifecycleScope.launch(GlobalErrorHandler.handler) { ... }
 *
 * Or set as default in StreamFlixApp applicationScope.
 */
object GlobalErrorHandler {

    private const val TAG = "GlobalErrorHandler"

    /**
     * A CoroutineExceptionHandler that logs the exception with context.
     * Does NOT swallow CancellationException — those should propagate.
     */
    val handler = CoroutineExceptionHandler { _: CoroutineContext, throwable: Throwable ->
        if (throwable is kotlinx.coroutines.CancellationException) {
            throw throwable
        }
        Log.e(TAG, "Unhandled coroutine exception", throwable)
        FileLogger.e(TAG, "Unhandled coroutine exception: ${throwable.message}", throwable)
    }

    /**
     * Wraps a throwable in user-friendly logging and returns true if handled.
     * Can be used in catch blocks for consistent error logging.
     */
    fun logError(tag: String, message: String, throwable: Throwable) {
        Log.e(tag, message, throwable)
    }
}
