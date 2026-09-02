package com.betterstreamflix.download

import android.content.Context
import com.betterstreamflix.utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/**
 * Progressive HTTP download executor with cooperative cancel/pause and resume.
 */
class DownloadExecutor(private val context: Context) {

    data class Progress(
        val percent: Int,
        val downloadedBytes: Long,
        val totalBytes: Long,
    )

    /**
     * @param shouldAbort return true to stop (pause/cancel). Partial file is kept on pause,
     * deleted on cancel when [deleteOnAbort] is true.
     */
    suspend fun download(
        url: String,
        outputFile: File,
        shouldAbort: () -> Boolean = { false },
        deleteOnAbort: Boolean = false,
        onProgress: (Progress) -> Unit,
    ): Result<File> {
        return withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                outputFile.parentFile?.mkdirs()
                val existingBytes = outputFile.takeIf { it.exists() }?.length()?.coerceAtLeast(0L) ?: 0L

                connection = (URL(url).openConnection() as? HttpURLConnection)?.apply {
                    requestMethod = "GET"
                    connectTimeout = Constants.NETWORK_TIMEOUT_MS
                    readTimeout = Constants.NETWORK_TIMEOUT_MS
                    setRequestProperty("User-Agent", Constants.USER_AGENT)
                    if (existingBytes > 0L) {
                        setRequestProperty("Range", "bytes=$existingBytes-")
                    }
                } ?: return@withContext Result.failure(Exception("Failed to open HTTP connection"))

                val responseCode = connection.responseCode
                val append = when (responseCode) {
                    206 -> true
                    in 200..299 -> {
                        // Server ignored Range — restart from scratch to avoid corruption.
                        if (existingBytes > 0L) {
                            outputFile.delete()
                        }
                        false
                    }
                    else -> return@withContext Result.failure(Exception("HTTP $responseCode"))
                }

                val contentLength = connection.contentLengthLong.coerceAtLeast(0L)
                val totalBytes = when {
                    append && contentLength > 0L -> existingBytes + contentLength
                    contentLength > 0L -> contentLength
                    else -> 0L
                }
                var downloadedBytes = if (append) existingBytes else 0L

                connection.inputStream.use { input ->
                    FileOutputStream(outputFile, append).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            coroutineContext.ensureActive()
                            if (shouldAbort()) {
                                if (deleteOnAbort) outputFile.delete()
                                return@withContext Result.failure(AbortedException(deleteOnAbort))
                            }
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            val percent = if (totalBytes > 0) {
                                ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                            } else {
                                0
                            }
                            onProgress(Progress(percent, downloadedBytes, totalBytes))
                        }
                    }
                }

                Result.success(outputFile)
            } catch (e: kotlinx.coroutines.CancellationException) {
                outputFile.delete()
                throw e
            } catch (e: AbortedException) {
                Result.failure(e)
            } catch (e: Exception) {
                // Keep partial file on unexpected errors so resume can continue.
                Result.failure(e)
            } finally {
                connection?.disconnect()
            }
        }
    }

    class AbortedException(val deleted: Boolean) : Exception("Download aborted")
}
