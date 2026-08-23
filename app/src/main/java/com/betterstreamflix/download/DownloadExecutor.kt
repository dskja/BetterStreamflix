package com.betterstreamflix.download

import android.content.Context
import com.betterstreamflix.utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Download executor — performs the actual file download with progress tracking.
 */
class DownloadExecutor(private val context: Context) {

    /**
     * Download a file with progress updates.
     */
    suspend fun download(
        url: String,
        outputFile: File,
        onProgress: (percent: Int, downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): Result<File> {
        return withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = Constants.NETWORK_TIMEOUT_MS
                    readTimeout = Constants.NETWORK_TIMEOUT_MS
                    setRequestProperty("User-Agent", Constants.USER_AGENT)
                }

                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    return@withContext Result.failure(Exception("HTTP $responseCode"))
                }

                val totalBytes = connection.contentLengthLong
                var downloadedBytes = 0L

                connection.inputStream.use { input ->
                    FileOutputStream(outputFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            val percent = if (totalBytes > 0) (downloadedBytes * 100 / totalBytes).toInt() else 0
                            onProgress(percent, downloadedBytes, totalBytes)
                        }
                    }
                }

                Result.success(outputFile)
            } catch (e: Exception) {
                outputFile.delete()
                Result.failure(e)
            } finally {
                connection?.disconnect()
            }
        }
    }
}
