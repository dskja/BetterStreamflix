package com.betterstreamflix.player.advanced

import androidx.media3.exoplayer.ExoPlayer

/**
 * Sleep timer — schedules automatic playback stop after a duration.
 */
class SleepTimer {

    private var endTimeMs: Long = 0
    private var isRunning: Boolean = false

    /**
     * Start the sleep timer.
     */
    fun start(durationMinutes: Int) {
        endTimeMs = System.currentTimeMillis() + (durationMinutes * 60 * 1000L)
        isRunning = true
    }

    /**
     * Cancel the sleep timer.
     */
    fun cancel() {
        isRunning = false
        endTimeMs = 0
    }

    /**
     * Check if the timer has expired.
     */
    fun isExpired(): Boolean {
        return isRunning && System.currentTimeMillis() >= endTimeMs
    }

    /**
     * Check if the timer is running.
     */
    fun isActive(): Boolean = isRunning && !isExpired()

    /**
     * Get remaining time in minutes.
     */
    fun getRemainingMinutes(): Int {
        if (!isRunning) return 0
        val remaining = endTimeMs - System.currentTimeMillis()
        return (remaining / (60 * 1000)).toInt().coerceAtLeast(0)
    }

    /**
     * Get remaining time as a formatted string.
     */
    fun getRemainingFormatted(): String {
        val minutes = getRemainingMinutes()
        val hours = minutes / 60
        val mins = minutes % 60
        return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
    }

    /**
     * Check and stop player if timer expired.
     */
    fun checkAndStop(player: ExoPlayer): Boolean {
        if (isExpired()) {
            player.pause()
            isRunning = false
            return true
        }
        return false
    }

    companion object {
        val PRESET_DURATIONS = listOf(15, 30, 45, 60, 90, 120)
    }
}
