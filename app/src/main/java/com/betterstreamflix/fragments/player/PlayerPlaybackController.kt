package com.betterstreamflix.fragments.player

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lightweight playback controller extracted for Compose player overlay migration.
 */
class PlayerPlaybackController : ViewModel() {

    data class PlaybackUiState(
        val title: String = "",
        val subtitle: String = "",
        val isPlaying: Boolean = false,
        val positionMs: Long = 0L,
        val durationMs: Long = 0L,
        val isBuffering: Boolean = false,
        val canGoPrevious: Boolean = false,
        val canGoNext: Boolean = false,
        val showSkipIntro: Boolean = false,
        val isLocked: Boolean = false,
    )

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    fun setMetadata(title: String, subtitle: String = "") {
        _state.value = _state.value.copy(title = title, subtitle = subtitle)
    }

    fun updatePosition(positionMs: Long, durationMs: Long) {
        _state.value = _state.value.copy(positionMs = positionMs, durationMs = durationMs)
    }

    fun setPlaying(playing: Boolean) {
        _state.value = _state.value.copy(isPlaying = playing)
    }

    fun setBuffering(buffering: Boolean) {
        _state.value = _state.value.copy(isBuffering = buffering)
    }

    fun setEpisodeNavigation(canGoPrevious: Boolean, canGoNext: Boolean) {
        val current = _state.value
        if (current.canGoPrevious == canGoPrevious && current.canGoNext == canGoNext) return
        _state.value = current.copy(canGoPrevious = canGoPrevious, canGoNext = canGoNext)
    }

    fun setSkipIntroVisible(visible: Boolean) {
        if (_state.value.showSkipIntro == visible) return
        _state.value = _state.value.copy(showSkipIntro = visible)
    }

    fun setLocked(locked: Boolean) {
        if (_state.value.isLocked == locked) return
        _state.value = _state.value.copy(isLocked = locked)
    }
}
