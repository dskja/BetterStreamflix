package com.betterstreamflix.architecture

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel base — lightweight ViewModel-like class with state management,
 * coroutine support, and lifecycle awareness.
 */
abstract class BaseViewModel<T>(initialState: T) {

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<T> = _state.asStateFlow()

    protected var viewModelScope: CoroutineScope? = null

    /**
     * Update the state.
     */
    protected fun updateState(newState: T) {
        _state.value = newState
    }

    /**
     * Update the state with a transform function.
     */
    protected fun updateState(transform: (T) -> T) {
        _state.value = transform(_state.value)
    }

    /**
     * Get the current state.
     */
    protected fun currentState(): T = _state.value

    /**
     * Bind a coroutine scope for this ViewModel.
     */
    fun bind(scope: CoroutineScope) {
        viewModelScope = scope
    }

    /**
     * Unbind and cleanup.
     */
    fun unbind() {
        viewModelScope = null
    }

    /**
     * Launch a coroutine in the ViewModel scope.
     */
    protected fun launch(block: suspend CoroutineScope.() -> Unit): Job? {
        return viewModelScope?.launch(block = block)
    }

    /**
     * Debounced state update.
     */
    protected fun debouncedUpdate(newState: T, delayMs: Long = 300L): Job? {
        return viewModelScope?.launch {
            delay(delayMs)
            updateState(newState)
        }
    }

    /**
     * Cleanup resources.
     */
    open fun onCleared() {
        unbind()
    }
}
