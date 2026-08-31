package com.betterstreamflix.download

import android.content.Context
import android.net.Uri
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.DownloadRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@UnstableApi
class DashDownloadEngine(private val context: Context) {

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
            val request = DownloadRequest.Builder(downloadId, Uri.parse(url))
                .setMimeType(MimeTypes.APPLICATION_MPD)
                .setData(title.toByteArray())
                .build()
            Media3OfflineDownloads.requireManager(context).addDownload(request)
            StreamflixDownloadService.start(context)
            outputDir.mkdirs()
            val marker = File(outputDir, "$downloadId.mpd")
            marker.writeText(url)
            onProgress(0, 0, 0)
            marker
        }
    }
}
