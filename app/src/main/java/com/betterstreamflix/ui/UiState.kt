package com.betterstreamflix.ui

/**
 * Unified UI state for all screens.
 * ViewModels can expose this as StateFlow<UiState<T>>.
 */
sealed class UiState<out T> {
    data object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String, val retryable: Boolean = true) : UiState<Nothing>()
    data object Empty : UiState<Nothing>()

    val isLoading: Boolean get() = this is Loading
    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error
    val isEmpty: Boolean get() = this is Empty

    fun getOrNull(): T? = (this as? Success)?.data

    companion object {
        fun <T> fromResult(result: com.betterstreamflix.data.Result<T>): UiState<T> = when (result) {
            is com.betterstreamflix.data.Result.Loading -> Loading
            is com.betterstreamflix.data.Result.Success -> Success(result.data)
            is com.betterstreamflix.data.Result.Error -> Error(
                message = result.error.toString(),
                retryable = result.error !is com.betterstreamflix.data.ErrorType.Parse,
            )
        }
    }
}
