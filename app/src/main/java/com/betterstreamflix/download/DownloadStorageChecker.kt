package com.betterstreamflix.download

import android.content.Context
import android.os.StatFs
import java.io.File

/**
 * Download storage checker — checks available storage before
 * starting downloads and manages storage cleanup.
 */
object DownloadStorageChecker {

    /**
     * Get available storage in bytes.
     */
    fun getAvailableStorage(context: Context): Long {
        val filesDir = context.filesDir
        val stat = StatFs(filesDir.path)
        return stat.availableBytes
    }

    /**
     * Get total storage in bytes.
     */
    fun getTotalStorage(context: Context): Long {
        val filesDir = context.filesDir
        val stat = StatFs(filesDir.path)
        return stat.totalBytes
    }

    /**
     * Check if there's enough storage for a download.
     */
    fun hasEnoughStorage(context: Context, requiredBytes: Long): Boolean {
        return getAvailableStorage(context) > requiredBytes * 1.1 // 10% buffer
    }

    /**
     * Get the size of downloaded content.
     */
    fun getDownloadedSize(context: Context): Long {
        val downloadDir = File(context.filesDir, "downloads")
        if (!downloadDir.exists()) return 0
        return downloadDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    /**
     * Format storage size for display.
     */
    fun formatSize(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format("%.1f GB", bytes / 1024.0 / 1024 / 1024)
            bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / 1024.0 / 1024)
            bytes >= 1024 -> String.format("%.1f KB", bytes / 1024.0)
            else -> "$bytes B"
        }
    }

    /**
     * Get storage usage percentage.
     */
    fun getStorageUsagePercent(context: Context): Int {
        val total = getTotalStorage(context)
        if (total == 0L) return 0
        val used = total - getAvailableStorage(context)
        return ((used.toDouble() / total) * 100).toInt()
    }

    /**
     * Check if storage is critically low.
     */
    fun isStorageLow(context: Context): Boolean {
        return getAvailableStorage(context) < 100 * 1024 * 1024 // Less than 100MB
    }

    /**
     * Check if storage is critically full.
     */
    fun isStorageCritical(context: Context): Boolean {
        return getAvailableStorage(context) < 50 * 1024 * 1024 // Less than 50MB
    }

    /**
     * Get a storage warning message.
     */
    fun getStorageWarning(context: Context): String? {
        return when {
            isStorageCritical(context) -> "Storage is critically low. Free up space to continue downloading."
            isStorageLow(context) -> "Storage is running low. Consider cleaning up old downloads."
            else -> null
        }
    }
}
