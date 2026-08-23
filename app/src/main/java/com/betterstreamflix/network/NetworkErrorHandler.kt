package com.betterstreamflix.network

import java.util.concurrent.ConcurrentHashMap

/**
 * Network error handler — centralizes network error classification
 * and user-friendly message generation.
 */
object NetworkErrorHandler {

    /**
     * Classify a network error.
     */
    fun classify(error: Throwable): NetworkErrorType {
        return when (error) {
            is java.net.UnknownHostException -> NetworkErrorType.DNS_FAILURE
            is java.net.SocketTimeoutException -> NetworkErrorType.TIMEOUT
            is java.net.ConnectException -> NetworkErrorType.CONNECTION_REFUSED
            is javax.net.ssl.SSLException -> NetworkErrorType.SSL_ERROR
            is java.io.IOException -> NetworkErrorType.IO_ERROR
            is java.net.ProtocolException -> NetworkErrorType.PROTOCOL_ERROR
            else -> NetworkErrorType.UNKNOWN
        }
    }

    /**
     * Get a user-friendly error message.
     */
    fun getUserMessage(error: Throwable): String {
        return when (classify(error)) {
            NetworkErrorType.DNS_FAILURE -> "Unable to resolve host. Check your internet connection."
            NetworkErrorType.TIMEOUT -> "Request timed out. Try again later."
            NetworkErrorType.CONNECTION_REFUSED -> "Connection refused by server."
            NetworkErrorType.SSL_ERROR -> "Secure connection failed. The server certificate may be invalid."
            NetworkErrorType.IO_ERROR -> "Network error occurred. Check your connection."
            NetworkErrorType.PROTOCOL_ERROR -> "Protocol error. The server response was invalid."
            NetworkErrorType.HTTP_ERROR -> "Server returned an error response."
            NetworkErrorType.UNKNOWN -> "An unexpected network error occurred."
        }
    }

    /**
     * Check if an error is retryable.
     */
    fun isRetryable(error: Throwable): Boolean {
        return when (classify(error)) {
            NetworkErrorType.TIMEOUT -> true
            NetworkErrorType.CONNECTION_REFUSED -> true
            NetworkErrorType.IO_ERROR -> true
            NetworkErrorType.DNS_FAILURE -> false
            NetworkErrorType.SSL_ERROR -> false
            NetworkErrorType.PROTOCOL_ERROR -> false
            NetworkErrorType.HTTP_ERROR -> false
            NetworkErrorType.UNKNOWN -> false
        }
    }

    /**
     * Get the HTTP status code from an error if available.
     */
    fun getHttpStatusCode(error: Throwable): Int? {
        val message = error.message ?: return null
        return try {
            if (message.contains("HTTP ")) {
                message.substringAfter("HTTP ").substringBefore(" ").toInt()
            } else null
        } catch (e: Exception) {
            null
        }
    }

    enum class NetworkErrorType {
        DNS_FAILURE,
        TIMEOUT,
        CONNECTION_REFUSED,
        SSL_ERROR,
        IO_ERROR,
        PROTOCOL_ERROR,
        HTTP_ERROR,
        UNKNOWN,
    }
}
