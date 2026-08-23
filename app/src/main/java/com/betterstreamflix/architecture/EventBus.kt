package com.betterstreamflix.architecture

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Event bus — lightweight in-process event bus using SharedFlow
 * for decoupled communication between components.
 */
object EventBus {

    private val _events = MutableSharedFlow<AppEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<AppEvent> = _events.asSharedFlow()

    /**
     * Emit an event.
     */
    suspend fun emit(event: AppEvent) {
        _events.emit(event)
    }

    /**
     * Try to emit immediately (non-suspending).
     */
    fun tryEmit(event: AppEvent): Boolean {
        return _events.tryEmit(event)
    }
}

/**
 * Base event class for all app events.
 */
sealed class AppEvent {
    data class ContentPlayed(val contentId: String, val title: String) : AppEvent()
    data class ContentFavorited(val contentId: String, val title: String) : AppEvent()
    data class ContentUnfavorited(val contentId: String) : AppEvent()
    data class DownloadStarted(val contentId: String) : AppEvent()
    data class DownloadCompleted(val contentId: String, val filePath: String) : AppEvent()
    data class DownloadFailed(val contentId: String, val error: String) : AppEvent()
    data class ProviderChanged(val providerName: String) : AppEvent()
    data class SettingsChanged(val key: String, val value: Any?) : AppEvent()
    data class ThemeChanged(val isDark: Boolean) : AppEvent()
    data class LanguageChanged(val languageCode: String) : AppEvent()
    data class NetworkStateChanged(val isOnline: Boolean) : AppEvent()
    data class ErrorOccurred(val message: String, val isCritical: Boolean) : AppEvent()
    data object CacheCleared : AppEvent()
    data object HistoryCleared : AppEvent()
}
