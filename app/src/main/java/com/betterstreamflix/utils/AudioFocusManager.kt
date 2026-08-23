package com.betterstreamflix.utils

import android.content.Context
import android.media.AudioManager
import androidx.media3.exoplayer.ExoPlayer

/**
 * Audio focus manager — handles acquiring and releasing audio focus
 * for proper behavior with other apps playing audio.
 */
class AudioFocusManager(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var player: ExoPlayer? = null

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                player?.pause()
                player?.playWhenReady = false
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                player?.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                player?.volume = 0.3f
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                player?.volume = 1f
                player?.play()
            }
        }
    }

    /**
     * Request audio focus and bind to a player.
     */
    fun requestFocus(player: ExoPlayer): Boolean {
        this.player = player
        val result = audioManager?.requestAudioFocus(
            audioFocusListener,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN,
        ) ?: AudioManager.AUDIOFOCUS_REQUEST_FAILED
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    /**
     * Release audio focus.
     */
    fun releaseFocus() {
        audioManager?.abandonAudioFocus(audioFocusListener)
        player = null
    }
}

