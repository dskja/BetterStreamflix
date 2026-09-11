package com.dskja.betterstreamflix.download

import android.content.Context
import android.os.Environment
import android.os.StatFs
import com.dskja.betterstreamflix.utils.UserPreferences
import java.io.File

object DownloadStorage {
    private const val DIR_NAME = "downloads"
    private const val PUBLIC_FOLDER = "BetterStreamflix"
    private const val MIN_FREE_BYTES = 500L * 1024L * 1024L

    fun location(): DownloadStorageLocation = UserPreferences.downloadStorageLocation

    fun downloadsDir(context: Context): File {
        val app = context.applicationContext
        val dir = when (location()) {
            DownloadStorageLocation.INTERNAL ->
                File(app.filesDir, DIR_NAME)
            DownloadStorageLocation.APP_EXTERNAL -> {
                val root = app.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                    ?: app.getExternalFilesDir(null)
                    ?: app.filesDir
                File(root, DIR_NAME)
            }
            DownloadStorageLocation.PUBLIC_MOVIES -> {
                val movies = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                File(File(movies, PUBLIC_FOLDER), DIR_NAME)
            }
        }
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun cacheDir(context: Context): File {
        val dir = File(downloadsDir(context), "cache")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun subsDir(context: Context, contentKey: String): File {
        val safe = contentKey.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val dir = File(downloadsDir(context), "subs/$safe")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun absolutePathSummary(context: Context): String =
        downloadsDir(context).absolutePath

    fun usedBytes(context: Context): Long =
        downloadsDir(context).walkTopDown().filter { it.isFile }.sumOf { it.length() }

    fun freeBytes(context: Context): Long {
        return try {
            val path = downloadsDir(context).absolutePath
            val stat = StatFs(path)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (_: Exception) {
            Environment.getDataDirectory().usableSpace
        }
    }

    fun hasEnoughSpace(context: Context, estimatedBytes: Long = 0L): Boolean {
        val needed = maxOf(MIN_FREE_BYTES, estimatedBytes + (100L * 1024L * 1024L))
        return freeBytes(context) >= needed
    }

    fun isOverSoftLimit(context: Context): Boolean {
        val softLimit = UserPreferences.downloadSoftLimitGb.toLong() * 1024L * 1024L * 1024L
        return softLimit > 0 && usedBytes(context) >= softLimit
    }

    fun isLowSpace(context: Context): Boolean =
        freeBytes(context) < MIN_FREE_BYTES || isOverSoftLimit(context)

    fun formatBytes(bytes: Long): String {
        if (bytes < 0) return "—"
        val kb = 1024.0
        val mb = kb * 1024
        val gb = mb * 1024
        return when {
            bytes >= gb -> String.format("%.1f GB", bytes / gb)
            bytes >= mb -> String.format("%.0f MB", bytes / mb)
            bytes >= kb -> String.format("%.0f KB", bytes / kb)
            else -> "$bytes B"
        }
    }

    fun deleteQuietly(file: File?) {
        runCatching {
            if (file == null) return
            if (file.isDirectory) file.deleteRecursively() else file.delete()
        }
    }
}
