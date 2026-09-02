package com.betterstreamflix.download

import android.content.Context
import android.util.Log
import com.betterstreamflix.utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Durable local artwork for offline Downloads UI.
 */
object DownloadArtworkStore {

    private const val TAG = "DownloadArtworkStore"
    private const val ARTWORK_DIR = "artwork"

    fun artworkFile(context: Context, downloadId: String): File =
        File(File(DownloadFileManager.getDownloadDir(context), ARTWORK_DIR).apply { mkdirs() }, "$downloadId.jpg")

    fun delete(context: Context, downloadId: String) {
        runCatching { artworkFile(context, downloadId).delete() }
    }

    fun isLocalPath(url: String): Boolean =
        url.startsWith("/") || url.startsWith("file:")

    /** Coil model: [File] for durable local paths, otherwise the remote URL string. */
    fun coilModel(url: String?): Any? {
        val source = url?.takeIf { it.isNotBlank() } ?: return null
        if (!isLocalPath(source)) return source
        val file = File(source.removePrefix("file://"))
        return file.takeIf { it.exists() }
    }

    fun needsRemoteFetch(url: String?): Boolean {
        val source = url?.takeIf { it.isNotBlank() } ?: return false
        if (!isLocalPath(source)) return true
        return !File(source.removePrefix("file://")).exists()
    }

    /**
     * If [remoteOrLocalUrl] is already a local file path, returns it.
     * Otherwise downloads the remote image into durable app storage and returns the absolute path.
     */
    suspend fun cache(
        context: Context,
        downloadId: String,
        remoteOrLocalUrl: String?,
    ): String? = withContext(Dispatchers.IO) {
        val source = remoteOrLocalUrl?.takeIf { it.isNotBlank() } ?: return@withContext null
        if (isLocalPath(source)) {
            val existing = File(source.removePrefix("file://"))
            if (existing.exists() && existing.length() > 0L) return@withContext existing.absolutePath
        }
        val dest = artworkFile(context, downloadId)
        if (dest.exists() && dest.length() > 0L) return@withContext dest.absolutePath
        if (isLocalPath(source)) return@withContext null
        runCatching {
            val connection = (URL(source).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = Constants.NETWORK_TIMEOUT_MS
                readTimeout = Constants.NETWORK_TIMEOUT_MS
                setRequestProperty("User-Agent", Constants.USER_AGENT)
                instanceFollowRedirects = true
            }
            try {
                val code = connection.responseCode
                if (code !in 200..299) {
                    Log.w(TAG, "artwork HTTP $code for $downloadId")
                    return@withContext null
                }
                connection.inputStream.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                dest.absolutePath.takeIf { dest.length() > 0L }
            } finally {
                connection.disconnect()
            }
        }.onFailure { e ->
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(TAG, "artwork cache failed for $downloadId", e)
            runCatching { dest.delete() }
        }.getOrNull()
    }
}
