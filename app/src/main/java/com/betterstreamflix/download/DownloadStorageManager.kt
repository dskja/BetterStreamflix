package com.betterstreamflix.download

import android.content.Context
import android.os.Environment
import java.io.File

/**
 * Manages storage locations for downloaded content.
 */
object DownloadStorageManager {

    /**
     * Get the download directory for the app.
     */
    fun getDownloadDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "BetterStreamflix")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Get a download file path for a video.
     */
    fun getDownloadFilePath(context: Context, videoId: String, title: String, extension: String = "mp4"): File {
        val safeTitle = title.replace(Regex("[^a-zA-Z0-9-_]"), "_").take(50)
        return File(getDownloadDir(context), "${videoId}_${safeTitle}.$extension")
    }

    /**
     * Get the total size of all downloads.
     */
    fun getDownloadSize(context: Context): Long {
        val dir = getDownloadDir(context)
        if (!dir.exists()) return 0
        return dir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
    }

    /**
     * Delete a downloaded file.
     */
    fun deleteDownload(file: File): Boolean {
        return file.exists() && file.delete()
    }

    /**
     * Check if there's enough free space for a download.
     */
    fun hasEnoughSpace(context: Context, requiredBytes: Long): Boolean {
        val dir = getDownloadDir(context)
        val freeSpace = dir.usableSpace
        return freeSpace > requiredBytes
    }

    /**
     * Get available storage space.
     */
    fun getAvailableSpace(context: Context): Long {
        val dir = getDownloadDir(context)
        return dir.usableSpace
    }

    /**
     * Format storage size for display.
     */
    fun formatSize(bytes: Long): String {
        return com.betterstreamflix.utils.DiskCacheManager.formatSize(bytes)
    }
}
