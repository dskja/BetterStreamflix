package com.betterstreamflix.download

import android.content.Context
import android.os.Environment
import java.io.File

/**
 * Manages storage locations and accounting for downloaded content.
 */
object DownloadStorageManager {

    fun getDownloadDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "BetterStreamflix")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getDownloadFilePath(
        context: Context,
        videoId: String,
        title: String,
        extension: String = "mp4",
    ): File {
        val safeTitle = title.replace(Regex("[^a-zA-Z0-9-_]"), "_").take(50)
        return File(getDownloadDir(context), "${videoId}_${safeTitle}.$extension")
    }

    /** Bytes under the public Movies/BetterStreamflix tree. */
    fun getExternalDownloadSize(context: Context): Long = directorySize(getDownloadDir(context))

    /** Bytes under internal filesDir/downloads (progressive HTTP). */
    fun getInternalDownloadSize(context: Context): Long =
        DownloadFileManager.getTotalDownloadSize(context)

    /** Bytes under Media3 offline SimpleCache. */
    fun getMedia3CacheSize(context: Context): Long =
        directorySize(Media3OfflineDownloads.cacheDir(context))

    /**
     * Total offline footprint across all download storage roots.
     */
    fun getDownloadSize(context: Context): Long {
        return getExternalDownloadSize(context) +
            getInternalDownloadSize(context) +
            getMedia3CacheSize(context)
    }

    fun getAvailableSpace(context: Context): Long = getDownloadDir(context).usableSpace

    fun hasEnoughSpace(context: Context, requiredBytes: Long): Boolean =
        getAvailableSpace(context) > requiredBytes

    fun deleteDownload(file: File): Boolean = file.exists() && file.delete()

    fun formatSize(bytes: Long): String =
        com.betterstreamflix.utils.DiskCacheManager.formatSize(bytes)

    fun formatSizeMb(bytes: Long): Long = (bytes / (1024L * 1024L)).coerceAtLeast(0L)

    private fun directorySize(dir: File): Long {
        if (!dir.exists()) return 0L
        return runCatching {
            dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }.getOrDefault(0L)
    }
}
