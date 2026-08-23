package com.betterstreamflix.cast

/**
 * Cast error handler — handles cast-specific errors with
 * user-friendly messages and retry logic.
 */
object CastErrorHandler {

    /**
     * Classify a cast error.
     */
    fun classify(error: Throwable): CastErrorType {
        val message = error.message?.lowercase() ?: ""
        return when {
            "timeout" in message -> CastErrorType.TIMEOUT
            "not found" in message || "no devices" in message -> CastErrorType.NO_DEVICE
            "connection" in message || "disconnected" in message -> CastErrorType.CONNECTION_LOST
            "auth" in message || "unauthorized" in message -> CastErrorType.AUTH_ERROR
            "network" in message -> CastErrorType.NETWORK_ERROR
            "protocol" in message -> CastErrorType.PROTOCOL_ERROR
            else -> CastErrorType.UNKNOWN
        }
    }

    /**
     * Get a user-friendly error message.
     */
    fun getUserMessage(error: Throwable): String {
        return when (classify(error)) {
            CastErrorType.TIMEOUT -> "Casting timed out. Check your network connection and try again."
            CastErrorType.NO_DEVICE -> "No casting devices found. Make sure your device is on the same network."
            CastErrorType.CONNECTION_LOST -> "Connection to the casting device was lost."
            CastErrorType.AUTH_ERROR -> "Authentication failed with the casting device."
            CastErrorType.NETWORK_ERROR -> "Network error occurred while casting."
            CastErrorType.PROTOCOL_ERROR -> "Protocol error with the casting device."
            CastErrorType.UNKNOWN -> "An unexpected casting error occurred."
        }
    }

    /**
     * Check if a cast error is retryable.
     */
    fun isRetryable(error: Throwable): Boolean {
        return when (classify(error)) {
            CastErrorType.TIMEOUT -> true
            CastErrorType.CONNECTION_LOST -> true
            CastErrorType.NETWORK_ERROR -> true
            CastErrorType.NO_DEVICE -> false
            CastErrorType.AUTH_ERROR -> false
            CastErrorType.PROTOCOL_ERROR -> false
            CastErrorType.UNKNOWN -> false
        }
    }

    /**
     * Get a suggested action for an error.
     */
    fun getSuggestedAction(error: Throwable): SuggestedAction {
        return when (classify(error)) {
            CastErrorType.TIMEOUT -> SuggestedAction.RETRY
            CastErrorType.NO_DEVICE -> SuggestedAction.CHECK_NETWORK
            CastErrorType.CONNECTION_LOST -> SuggestedAction.RECONNECT
            CastErrorType.AUTH_ERROR -> SuggestedAction.RESTART_APP
            CastErrorType.NETWORK_ERROR -> SuggestedAction.CHECK_NETWORK
            CastErrorType.PROTOCOL_ERROR -> SuggestedAction.RESTART_DEVICE
            CastErrorType.UNKNOWN -> SuggestedAction.RETRY
        }
    }

    enum class CastErrorType {
        TIMEOUT, NO_DEVICE, CONNECTION_LOST, AUTH_ERROR, NETWORK_ERROR, PROTOCOL_ERROR, UNKNOWN,
    }

    enum class SuggestedAction {
        RETRY, RECONNECT, CHECK_NETWORK, RESTART_APP, RESTART_DEVICE, NONE,
    }
}
