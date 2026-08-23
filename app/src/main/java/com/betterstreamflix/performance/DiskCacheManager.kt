package com.betterstreamflix.performance

import android.content.Context
import android.os.StatFs
import java.io.File

/**
 * Disk cache manager — manages disk-based cache with size limits
 * and automatic eviction.
 */
object DiskCacheManager {

    private const val DEFAULT_MAX_CACHE_SIZE_MB = 200L

    /**
     * Get the cache directory.
     */
    fun getCacheDir(context: Context): File {
        return File(context.cacheDir, "betterstreamflix_cache").apply {
            if (!exists()) mkdirs()
        }
    }

    /**
     * Get current cache size in bytes.
     */
    fun getCacheSize(context: Context): Long {
        val cacheDir = getCacheDir(context)
        return calculateDirSize(cacheDir)
    }

    /**
     * Get available disk space in bytes.
     */
    fun getAvailableDiskSpace(context: Context): Long {
        val cacheDir = getCacheDir(context)
        val stat = StatFs(cacheDir.path)
        return stat.availableBlocksLong * stat.blockSizeLong
    }

    /**
     * Clear the entire cache.
     */
    fun clearCache(context: Context) {
        val cacheDir = getCacheDir(context)
        cacheDir.listFiles()?.forEach { it.deleteRecursively() }
    }

    /**
     * Trim cache to max size, oldest files first.
     */
    fun trimCache(context: Context, maxSizeMb: Long = DEFAULT_MAX_CACHE_SIZE_MB) {
        val cacheDir = getCacheDir(context)
        val maxSizeBytes = maxSizeMb * 1024 * 1024
        var currentSize = calculateDirSize(cacheDir)

        if (currentSize <= maxSizeBytes) return

        val files = cacheDir.walkTopDown()
            .filter { it.isFile }
            .sortedBy { it.lastModified() }
            .toList()

        for (file in files) {
            if (currentSize <= maxSizeBytes) break
            val size = file.length()
            if (file.delete()) {
                currentSize -= size
            }
        }
    }

    /**
     * Check if cache should be trimmed.
     */
    fun shouldTrimCache(context: Context, thresholdMb: Long = DEFAULT_MAX_CACHE_SIZE_MB): Boolean {
        return getCacheSize(context) > thresholdMb * 1024 * 1024
    }

    /**
     * Get cache size formatted as string.
     */
    fun getCacheSizeFormatted(context: Context): String {
        val bytes = getCacheSize(context)
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1) String.format("%.1f MB", mb)
        else String.format("%.1f KB", bytes / 1024.0)
    }

    private fun calculateDirSize(dir: File): Long {
        if (!dir.exists()) return 0
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}
