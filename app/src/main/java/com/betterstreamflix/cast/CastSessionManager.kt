package com.betterstreamflix.cast

/**
 * Cast session manager — manages the lifecycle of a casting session
 * including media control and state tracking.
 */
object CastSessionManager {

    private var session: CastSession? = null

    data class CastSession(
        val device: CastManager.CastDevice,
        val contentId: String,
        val title: String,
        val positionMs: Long,
        val durationMs: Long,
        val isPlaying: Boolean,
        val volume: Float,
    )

    /**
     * Start a cast session.
     */
    fun startSession(device: CastManager.CastDevice, contentId: String, title: String): CastSession {
        val newSession = CastSession(
            device = device,
            contentId = contentId,
            title = title,
            positionMs = 0,
            durationMs = 0,
            isPlaying = true,
            volume = 1.0f,
        )
        session = newSession
        return newSession
    }

    /**
     * End the current session.
     */
    fun endSession() {
        session = null
    }

    /**
     * Get the current session.
     */
    fun getCurrentSession(): CastSession? = session

    /**
     * Update playback position.
     */
    fun updatePosition(positionMs: Long) {
        session = session?.copy(positionMs = positionMs)
    }

    /**
     * Update playing state.
     */
    fun updatePlayingState(isPlaying: Boolean) {
        session = session?.copy(isPlaying = isPlaying)
    }

    /**
     * Update volume.
     */
    fun updateVolume(volume: Float) {
        session = session?.copy(volume = volume.coerceIn(0f, 1f))
    }

    /**
     * Update duration.
     */
    fun updateDuration(durationMs: Long) {
        session = session?.copy(durationMs = durationMs)
    }

    /**
     * Check if a session is active.
     */
    fun hasActiveSession(): Boolean = session != null

    /**
     * Play.
     */
    fun play() {
        updatePlayingState(true)
    }

    /**
     * Pause.
     */
    fun pause() {
        updatePlayingState(false)
    }

    /**
     * Seek to position.
     */
    fun seekTo(positionMs: Long) {
        updatePosition(positionMs)
    }

    /**
     * Get session progress percentage.
     */
    fun getProgressPercent(): Int {
        val s = session ?: return 0
        if (s.durationMs <= 0) return 0
        return ((s.positionMs.toDouble() / s.durationMs) * 100).toInt()
    }
}
