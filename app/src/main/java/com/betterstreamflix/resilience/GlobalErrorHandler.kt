package com.betterstreamflix.resilience

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Global error handler — central place for unhandled exceptions
 * and error state management.
 */
object GlobalErrorHandler {

    private val _errorState = MutableStateFlow<ErrorState>(ErrorState.Idle)
    val errorState: StateFlow<ErrorState> = _errorState

    private val _networkErrorCount = MutableStateFlow(0)
    val networkErrorCount: StateFlow<Int> = _networkErrorCount

    /**
     * Report an unhandled error.
     */
    fun reportError(throwable: Throwable, context: String? = null) {
        val errorType = classifyError(throwable)
        _errorState.value = ErrorState.Active(
            type = errorType,
            message = throwable.message ?: "Unknown error",
            context = context,
            timestamp = System.currentTimeMillis(),
        )

        if (errorType == ErrorType.NETWORK) {
            _networkErrorCount.value = _networkErrorCount.value + 1
        }
    }

    /**
     * Clear the current error state.
     */
    fun clearError() {
        _errorState.value = ErrorState.Idle
    }

    /**
     * Reset network error count.
     */
    fun resetNetworkErrorCount() {
        _networkErrorCount.value = 0
    }

    /**
     * Classify an error into a standard type.
     */
    private fun classifyError(throwable: Throwable): ErrorType {
        return when (throwable) {
            is java.net.SocketTimeoutException -> ErrorType.NETWORK
            is java.net.UnknownHostException -> ErrorType.NETWORK
            is javax.net.ssl.SSLException -> ErrorType.NETWORK
            is java.io.IOException -> ErrorType.NETWORK
            is kotlinx.coroutines.CancellationException -> ErrorType.CANCELLED
            is com.google.gson.JsonSyntaxException -> ErrorType.PARSE
            is IllegalStateException -> ErrorType.STATE
            else -> ErrorType.UNKNOWN
        }
    }

    sealed class ErrorState {
        data object Idle : ErrorState()
        data class Active(
            val type: ErrorType,
            val message: String,
            val context: String?,
            val timestamp: Long,
        ) : ErrorState()
    }

    enum class ErrorType {
        NETWORK,
        PARSE,
        STATE,
        CANCELLED,
        UNKNOWN,
    }
}
