package com.betterstreamflix.player.advanced

import androidx.media3.exoplayer.ExoPlayer

/**
 * Picture-in-picture helper — manages PiP mode transitions and
 * aspect ratio for the PiP window.
 */
object PipHelper {

    /**
     * Get the optimal aspect ratio for PiP based on video dimensions.
     */
    fun calculatePipAspectRatio(videoWidth: Int, videoHeight: Int): Float {
        if (videoWidth <= 0 || videoHeight <= 0) return 16f / 9f
        val ratio = videoWidth.toFloat() / videoHeight.toFloat()
        // Clamp to reasonable PiP bounds
        return ratio.coerceIn(0.5f, 2.39f)
    }

    /**
     * Check if PiP is supported on this device.
     */
    fun isPipSupported(): Boolean {
        return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O
    }

    /**
     * Get PiP parameters builder.
     */
    fun buildPipParams(
        aspectRatio: Float,
        seamlessResize: Boolean = true,
    ): android.app.PictureInPictureParams {
        val builder = android.app.PictureInPictureParams.Builder()
            .setAspectRatio(aspectRatio)

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            builder.setSeamlessResizeEnabled(seamlessResize)
        }

        return builder.build()
    }

    /**
     * Configure player for PiP mode.
     */
    fun configurePlayerForPip(player: ExoPlayer) {
        player.playWhenReady = true
        player.volume = 0f // Mute in PiP by default
    }

    /**
     * Restore player from PiP mode.
     */
    fun restorePlayerFromPip(player: ExoPlayer, previousVolume: Float) {
        player.volume = previousVolume
    }
}
