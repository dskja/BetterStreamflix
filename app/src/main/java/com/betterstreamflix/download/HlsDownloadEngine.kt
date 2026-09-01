package com.betterstreamflix.download

import android.content.Context
import android.net.Uri
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.DownloadRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@UnstableApi
class HlsDownloadEngine(private val context: Context) {

    suspend fun download(
        url: String,
        downloadId: String,
        title: String,
        onProgress: (percent: Int, downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): Result<String> = withContext(Dispatchers.IO) {
        if (StreamTypeDetector.isDrmProtected(url)) {
            return@withContext Result.failure(IllegalStateException("DRM-protected streams cannot be downloaded"))
        }
        runCatching {
            val request = DownloadRequest.Builder(downloadId, Uri.parse(url))
                .setMimeType(MimeTypes.APPLICATION_M3U8)
                .setData(title.toByteArray())
                .build()
            Media3OfflineDownloads.requireManager(context).addDownload(request)
            StreamflixDownloadService.start(context)
            val mediaPath = OfflineMediaPaths.forDownload(downloadId)
            onProgress(0, 0, 0)
            mediaPath
        }
    }
}
