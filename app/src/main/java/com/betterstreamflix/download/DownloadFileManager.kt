package com.betterstreamflix.download

import android.content.Context
import java.io.File

/**
 * Download file manager — manages downloaded files on disk
 * including naming, organization, and cleanup.
 */
object DownloadFileManager {

    private const val DOWNLOAD_DIR = "downloads"
    private const val TEMP_DIR = "downloads_temp"

    /**
     * Get the downloads directory.
     */
    fun getDownloadDir(context: Context): File {
        return File(context.filesDir, DOWNLOAD_DIR).apply { mkdirs() }
    }

    /**
     * Get the temp downloads directory.
     */
    fun getTempDir(context: Context): File {
        return File(context.cacheDir, TEMP_DIR).apply { mkdirs() }
    }

    /**
     * Get the file path for a downloaded content.
     */
    fun getDownloadFile(context: Context, downloadId: String, extension: String = ".mp4"): File {
        return File(getDownloadDir(context), "$downloadId$extension")
    }

    /**
     * Get the temp file for an in-progress download.
     */
    fun getTempFile(context: Context, downloadId: String, extension: String = ".tmp"): File {
        return File(getTempDir(context), "$downloadId$extension")
    }

    /**
     * Move a temp file to the final download location.
     */
    fun finalizeDownload(context: Context, downloadId: String, extension: String): File? {
        val tempFile = getTempFile(context, downloadId)
        val finalFile = getDownloadFile(context, downloadId, extension)
        return if (tempFile.renameTo(finalFile)) finalFile else null
    }

    /**
     * Delete a downloaded file.
     */
    fun deleteDownload(context: Context, downloadId: String, extension: String = ".mp4"): Boolean {
        return getDownloadFile(context, downloadId, extension).delete()
    }

    /**
     * Delete a temp file.
     */
    fun deleteTempFile(context: Context, downloadId: String): Boolean {
        return getTempFile(context, downloadId).delete()
    }

    /**
     * Get all downloaded files.
     */
    fun getAllDownloads(context: Context): List<File> {
        return getDownloadDir(context).listFiles()?.toList() ?: emptyList()
    }

    /**
     * Get total size of all downloads.
     */
    fun getTotalDownloadSize(context: Context): Long {
        return getDownloadDir(context).walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    /**
     * Clean up orphaned temp files.
     */
    fun cleanupTempFiles(context: Context) {
        getTempDir(context).listFiles()?.forEach { file ->
            // Delete temp files older than 24 hours
            if (System.currentTimeMillis() - file.lastModified() > 24 * 60 * 60 * 1000) {
                file.delete()
            }
        }
    }

    /**
     * Clear all downloads.
     */
    fun clearAllDownloads(context: Context) {
        getDownloadDir(context).deleteRecursively()
        getDownloadDir(context).mkdirs()
    }

    /**
     * Check if a download file exists.
     */
    fun downloadExists(context: Context, downloadId: String, extension: String = ".mp4"): Boolean {
        return getDownloadFile(context, downloadId, extension).exists()
    }

    /**
     * Get file size for a download.
     */
    fun getDownloadSize(context: Context, downloadId: String, extension: String = ".mp4"): Long {
        val file = getDownloadFile(context, downloadId, extension)
        return if (file.exists()) file.length() else 0
    }
}
