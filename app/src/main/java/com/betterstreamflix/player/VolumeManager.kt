package com.betterstreamflix.player

import android.content.Context
import android.media.AudioManager

/**
 * Manages volume control for video playback.
 * Provides smooth volume adjustments and mute toggle.
 */
class VolumeManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var savedVolume: Int = getCurrentVolume()

    /**
     * Get current media volume (0 to maxVolume).
     */
    fun getCurrentVolume(): Int {
        return audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
    }

    /**
     * Get maximum volume.
     */
    fun getMaxVolume(): Int {
        return audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 0
    }

    /**
     * Set volume as a percentage (0 to 100).
     */
    fun setVolumePercent(percent: Int) {
        val max = getMaxVolume()
        val volume = (max * percent / 100).coerceIn(0, max)
        audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
    }

    /**
     * Increase volume by one step.
     */
    fun increaseVolume() {
        val current = getCurrentVolume()
        val max = getMaxVolume()
        if (current < max) {
            audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, current + 1, 0)
        }
    }

    /**
     * Decrease volume by one step.
     */
    fun decreaseVolume() {
        val current = getCurrentVolume()
        if (current > 0) {
            audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, current - 1, 0)
        }
    }

    /**
     * Toggle mute. Returns true if now muted.
     */
    fun toggleMute(): Boolean {
        val current = getCurrentVolume()
        return if (current > 0) {
            savedVolume = current
            audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
            true
        } else {
            audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, savedVolume.coerceAtLeast(1), 0)
            false
        }
    }

    /**
     * Get volume as percentage.
     */
    fun getVolumePercent(): Int {
        val max = getMaxVolume()
        if (max == 0) return 0
        return getCurrentVolume() * 100 / max
    }
}

