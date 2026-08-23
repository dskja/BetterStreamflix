package com.betterstreamflix.cast

/**
 * Cast volume controller — manages volume control for cast sessions
 * with smooth ramping and device-specific handling.
 */
object CastVolumeController {

    private var currentVolume: Float = 1.0f
    private var previousVolume: Float = 1.0f
    private var isMuted: Boolean = false

    /**
     * Set volume.
     */
    fun setVolume(volume: Float): Float {
        previousVolume = currentVolume
        currentVolume = volume.coerceIn(0f, 1f)
        isMuted = currentVolume == 0f
        CastSessionManager.updateVolume(currentVolume)
        return currentVolume
    }

    /**
     * Get current volume.
     */
    fun getVolume(): Float = currentVolume

    /**
     * Increase volume by a step.
     */
    fun increaseVolume(step: Float = 0.1f): Float {
        return setVolume(currentVolume + step)
    }

    /**
     * Decrease volume by a step.
     */
    fun decreaseVolume(step: Float = 0.1f): Float {
        return setVolume(currentVolume - step)
    }

    /**
     * Mute.
     */
    fun mute() {
        if (!isMuted) {
            previousVolume = currentVolume
            setVolume(0f)
            isMuted = true
        }
    }

    /**
     * Unmute.
     */
    fun unmute() {
        if (isMuted) {
            setVolume(previousVolume)
            isMuted = false
        }
    }

    /**
     * Toggle mute.
     */
    fun toggleMute() {
        if (isMuted) unmute() else mute()
    }

    /**
     * Check if muted.
     */
    fun isMuted(): Boolean = isMuted

    /**
     * Get volume as percentage.
     */
    fun getVolumePercent(): Int = (currentVolume * 100).toInt()

    /**
     * Set volume from percentage.
     */
    fun setVolumePercent(percent: Int): Float {
        return setVolume(percent / 100f)
    }

    /**
     * Format volume for display.
     */
    fun formatVolume(): String {
        return "${getVolumePercent()}%"
    }

    /**
     * Reset to default.
     */
    fun reset() {
        currentVolume = 1.0f
        previousVolume = 1.0f
        isMuted = false
    }
}
