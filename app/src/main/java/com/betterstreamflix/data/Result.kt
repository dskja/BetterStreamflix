package com.betterstreamflix.data

/**
 * Unified Result wrapper for all data operations.
 * Replaces ad-hoc try-catch patterns with a consistent sealed class.
 */
sealed class Result<out T> {
    data class Success<out T>(val data: T) : Result<T>()
    data class Error(val error: ErrorType) : Result<Nothing>()
    data object Loading : Result<Nothing>()

    /**
     * Returns the data if Success, or null otherwise.
     */
    fun getOrNull(): T? = (this as? Success)?.data

    /**
     * Returns true if this is a Success.
     */
    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error
    val isLoading: Boolean get() = this is Loading

    /**
     * Returns the data if Success, or the result of default otherwise.
     */
    inline fun getOrElse(default: () -> T): T {
        return if (this is Success) data else default()
    }

    /**
     * Map the success data to a new type.
     */
    inline fun <R> map(transform: (T) -> R): Result<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> this
        is Loading -> this
    }

    /**
     * Execute action on success, return original result.
     */
    inline fun onSuccess(action: (T) -> Unit): Result<T> {
        if (this is Success) action(data)
        return this
    }

    /**
     * Execute action on error, return original result.
     */
    inline fun onError(action: (ErrorType) -> Unit): Result<T> {
        if (this is Error) action(error)
        return this
    }

    companion object {
        /**
         * Wrap a suspend block in a Result, catching exceptions.
         */
        suspend inline fun <T> runCatching(crossinline block: suspend () -> T): Result<T> {
            return try {
                Success(block())
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Error(ErrorType.from(e))
            }
        }
    }
}

/**
 * Error types for Result.
 */
sealed class ErrorType {
    data class Network(val message: String) : ErrorType()
    data class Http(val code: Int, val message: String) : ErrorType()
    data class Provider(val providerName: String, val message: String) : ErrorType()
    data class Database(val message: String) : ErrorType()
    data class Parse(val message: String) : ErrorType()
    data class Unknown(val message: String) : ErrorType()

    companion object {
        fun from(throwable: Throwable): ErrorType {
            val message = throwable.message.orEmpty()
            return when {
                throwable is java.net.SocketTimeoutException -> Network("Timeout")
                throwable is java.net.UnknownHostException -> Network("DNS resolution failed")
                throwable is javax.net.ssl.SSLException -> Network("SSL error: $message")
                message.contains("403") -> Http(403, "Forbidden")
                message.contains("404") -> Http(404, "Not found")
                message.contains("500") -> Http(500, "Server error")
                message.contains("502") -> Http(502, "Bad gateway")
                message.contains("503") -> Http(503, "Service unavailable")
                throwable is com.google.gson.JsonSyntaxException -> Parse("JSON parse error: $message")
                throwable is android.database.sqlite.SQLiteException -> Database(message)
                else -> Unknown(message.ifEmpty { throwable::class.java.simpleName })
            }
        }
    }
}
