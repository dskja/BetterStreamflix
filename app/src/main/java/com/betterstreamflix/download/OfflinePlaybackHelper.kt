package com.betterstreamflix.download

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource

/**
 * Offline playback helper — sets up ExoPlayer for local file playback.
 */
object OfflinePlaybackHelper {

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

    fun isPlayable(filePath: String): Boolean {
        val file = java.io.File(filePath)
        return file.exists() && file.length() > 0
    }

    fun getLocalUri(filePath: String): Uri = Uri.fromFile(java.io.File(filePath))

    fun playLocal(context: Context, task: DownloadManager.DownloadTask) {
        playLocal(context, task.filePath, task.title)
    }

    fun playLocal(context: Context, filePath: String, title: String) {
        if (!isPlayable(filePath)) return
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(getLocalUri(filePath), "video/*")
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(android.content.Intent.EXTRA_TITLE, title)
        }
        runCatching { context.startActivity(intent) }
    }
}
