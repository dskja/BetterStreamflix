package com.betterstreamflix.download

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * DASH download engine — stores manifest for offline playback bootstrap.
 */
class DashDownloadEngine(private val context: Context) {

    private val httpEngine = DownloadExecutor(context)

    suspend fun download(
        url: String,
        outputDir: File,
        title: String,
        onProgress: (percent: Int, downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        if (StreamTypeDetector.isDrmProtected(url)) {
            return@withContext Result.failure(IllegalStateException("DRM-protected streams cannot be downloaded"))
        }
        val outputFile = File(outputDir, "${title.hashCode()}.mpd")
        httpEngine.download(url, outputFile) { percent, downloaded, total ->
            onProgress(percent, downloaded, total)
        }
    }
}
