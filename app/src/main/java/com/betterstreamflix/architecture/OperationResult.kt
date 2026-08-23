package com.betterstreamflix.architecture

/**
 * Result wrapper — unified result type for all data operations
 * with mapping and chaining support.
 */
sealed class OperationResult<out T> {
    data class Success<T>(val data: T) : OperationResult<T>()
    data class Failure(val error: Throwable, val message: String = error.message ?: "Unknown error") : OperationResult<Nothing>()
    data object Loading : OperationResult<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isFailure: Boolean get() = this is Failure
    val isLoading: Boolean get() = this is Loading

    fun getOrNull(): T? = (this as? Success)?.data

    fun getOrDefault(defaultValue: @UnsafeVariance T): T = getOrNull() ?: defaultValue

    inline fun <R> map(transform: (T) -> R): OperationResult<R> = when (this) {
        is Success -> Success(transform(data))
        is Failure -> this
        is Loading -> this
    }

    inline fun <R> flatMap(transform: (T) -> OperationResult<R>): OperationResult<R> = when (this) {
        is Success -> transform(data)
        is Failure -> this
        is Loading -> this
    }

    inline fun onSuccess(action: (T) -> Unit): OperationResult<T> {
        if (this is Success) action(data)
        return this
    }

    inline fun onFailure(action: (Throwable) -> Unit): OperationResult<T> {
        if (this is Failure) action(error)
        return this
    }

    inline fun onLoading(action: () -> Unit): OperationResult<T> {
        if (this is Loading) action()
        return this
    }

    companion object {
        fun <T> success(data: T): OperationResult<T> = Success(data)
        fun failure(error: Throwable): OperationResult<Nothing> = Failure(error)
        fun loading(): OperationResult<Nothing> = Loading

        inline fun <T> runCatching(block: () -> T): OperationResult<T> {
            return try {
                Success(block())
            } catch (e: Exception) {
                Failure(e)
            }
        }
    }
}
