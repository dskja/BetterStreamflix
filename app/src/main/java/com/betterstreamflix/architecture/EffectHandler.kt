package com.betterstreamflix.architecture

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Effect handler — manages one-shot side effects (navigation, toasts,
 * snackbars) that should not survive configuration changes.
 */
class EffectHandler<Effect> {

    private val _effects = MutableSharedFlow<Effect>(extraBufferCapacity = 16)
    val effects: SharedFlow<Effect> = _effects.asSharedFlow()

    /**
     * Send an effect.
     */
    suspend fun send(effect: Effect) {
        _effects.emit(effect)
    }

    /**
     * Try to send an effect immediately.
     */
    fun trySend(effect: Effect): Boolean {
        return _effects.tryEmit(effect)
    }

    /**
     * Debounced effect sender.
     */
    fun sendDebounced(scope: CoroutineScope, effect: Effect, delayMs: Long = 300L): Job {
        return scope.launch {
            delay(delayMs)
            _effects.emit(effect)
        }
    }
}

/**
 * Common UI effects.
 */
sealed class UiEffect {
    data class ShowToast(val message: String, val duration: Int = 0) : UiEffect()
    data class ShowSnackbar(val message: String, val actionLabel: String? = null) : UiEffect()
    data class NavigateTo(val route: String, val args: Map<String, Any?> = emptyMap()) : UiEffect()
    data object NavigateBack : UiEffect()
    data class ShowError(val title: String, val message: String) : UiEffect()
    data class ShowDialog(val title: String, val message: String, val positiveLabel: String? = null, val negativeLabel: String? = null) : UiEffect()
    data class CopyToClipboard(val text: String, val label: String) : UiEffect()
    data class ShareContent(val title: String, val url: String) : UiEffect()
}
