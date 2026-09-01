package com.betterstreamflix.download

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.betterstreamflix.activities.OfflinePlayerActivity

/**
 * Offline playback helper — sets up ExoPlayer for local file playback.
 */
@UnstableApi
object OfflinePlaybackHelper {

    fun playDownloadedFile(context: Context, player: ExoPlayer, filePath: String) {
        OfflineMediaPaths.parseDownloadId(filePath)?.let { downloadId ->
            playMedia3Download(context, player, downloadId)
            return
        }

        val file = java.io.File(filePath)
        if (!file.exists()) return

        val uri = Uri.fromFile(file)
        val dataSourceFactory = DefaultDataSource.Factory(context)
        val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(androidx.media3.common.MediaItem.fromUri(uri))

        player.setMediaSource(mediaSource)
        player.prepare()
    }

    fun isPlayable(context: Context, filePath: String): Boolean {
        OfflineMediaPaths.parseDownloadId(filePath)?.let { downloadId ->
            val manager = Media3OfflineDownloads.managerOrNull(context) ?: return false
            val download = manager.downloadIndex.getDownload(downloadId) ?: return false
            return download.state == Download.STATE_COMPLETED ||
                download.state == Download.STATE_DOWNLOADING
        }
        val file = java.io.File(filePath)
        return file.exists() && file.length() > 0
    }

    fun isPlayable(filePath: String): Boolean {
        if (OfflineMediaPaths.parseDownloadId(filePath) != null) {
            return true
        }
        val file = java.io.File(filePath)
        return file.exists() && file.length() > 0
    }

    fun getLocalUri(filePath: String): Uri = Uri.fromFile(java.io.File(filePath))

    fun playLocal(context: Context, task: DownloadManager.DownloadTask) {
        playLocal(context, task.filePath, task.title)
    }

    fun playLocal(context: Context, filePath: String, title: String) {
        OfflineMediaPaths.parseDownloadId(filePath)?.let { downloadId ->
            val intent = Intent(context, OfflinePlayerActivity::class.java).apply {
                putExtra(OfflinePlayerActivity.EXTRA_DOWNLOAD_ID, downloadId)
                putExtra(OfflinePlayerActivity.EXTRA_FILE_PATH, filePath)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(intent) }
            return
        }

        if (!isPlayable(filePath)) return
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(getLocalUri(filePath), "video/*")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(Intent.EXTRA_TITLE, title)
        }
        runCatching { context.startActivity(intent) }
    }

    private fun playMedia3Download(context: Context, player: ExoPlayer, downloadId: String) {
        val manager = Media3OfflineDownloads.managerOrNull(context) ?: return
        val cache = Media3OfflineDownloads.cacheOrNull(context) ?: return
        val download = manager.downloadIndex.getDownload(downloadId) ?: return

        val httpFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setUserAgent(com.betterstreamflix.utils.Constants.USER_AGENT)
        val cacheDataSourceFactory = androidx.media3.datasource.cache.CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(httpFactory)
        val dataSourceFactory = DefaultDataSource.Factory(context, cacheDataSourceFactory)
        val mediaSource = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory)
            .createMediaSource(androidx.media3.common.MediaItem.fromUri(download.request.uri))

        player.setMediaSource(mediaSource)
        player.prepare()
    }
}
