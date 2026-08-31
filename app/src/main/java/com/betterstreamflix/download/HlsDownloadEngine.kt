package com.betterstreamflix.download

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.offline.DownloadHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

@UnstableApi
class HlsDownloadEngine(private val context: Context) {

    suspend fun download(
        url: String,
        outputDir: File,
        downloadId: String,
        title: String,
        onProgress: (percent: Int, downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        if (StreamTypeDetector.isDrmProtected(url)) {
            return@withContext Result.failure(IllegalStateException("DRM-protected streams cannot be downloaded"))
        }
        runCatching {
            val mediaItem = MediaItem.Builder()
                .setUri(url)
                .setMimeType(MimeTypes.APPLICATION_M3U8)
                .setMediaId(downloadId)
                .setTag(title)
                .build()
            val dataSourceFactory = DefaultHttpDataSource.Factory()
            val helper = DownloadHelper.forMediaItem(context, mediaItem, dataSourceFactory, null)
            val request = suspendCancellableCoroutine { cont ->
                helper.prepare(object : DownloadHelper.Callback {
                    override fun onPrepared(helper: DownloadHelper) {
                        try {
                            val downloadRequest: DownloadRequest = helper.getDownloadRequest(downloadId)
                            cont.resume(downloadRequest)
                        } catch (e: Exception) {
                            cont.resumeWith(Result.failure(e))
                        } finally {
                            helper.release()
                        }
                    }

                    override fun onPrepareError(helper: DownloadHelper, e: java.io.IOException) {
                        helper.release()
                        cont.resumeWith(Result.failure(e))
                    }
                })
            }.getOrThrow()

            val manager = Media3OfflineDownloads.requireManager(context)
            manager.addDownload(request)
            StreamflixDownloadService.start(context)

            outputDir.mkdirs()
            val marker = File(outputDir, "$downloadId.m3u8")
            marker.writeText(request.uri.toString())
            onProgress(0, 0, 0)
            marker
        }
    }
}
