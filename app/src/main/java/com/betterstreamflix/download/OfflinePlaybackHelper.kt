package com.betterstreamflix.download

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.DefaultDataSource

/**
 * Offline playback helper — sets up ExoPlayer for local file playback.
 */
object OfflinePlaybackHelper {

    /**
     * Set up ExoPlayer to play a downloaded file.
     */
    fun playDownloadedFile(context: Context, player: ExoPlayer, filePath: String) {
        val file = java.io.File(filePath)
        if (!file.exists()) return

        val uri = Uri.fromFile(file)
        val dataSourceFactory = DefaultDataSource.Factory(context)
        val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(uri))

        player.setMediaSource(mediaSource)
        player.prepare()
    }

    /**
     * Check if a downloaded file exists and is playable.
     */
    fun isPlayable(filePath: String): Boolean {
        val file = java.io.File(filePath)
        return file.exists() && file.length() > 0
    }

    /**
     * Get the local URI for a downloaded file.
     */
    fun getLocalUri(filePath: String): Uri {
        return Uri.fromFile(java.io.File(filePath))
    }
}
