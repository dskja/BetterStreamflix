package com.betterstreamflix.utils

import android.content.Context
import java.io.File

/**
 * Disk cache manager for images, subtitles, and temporary files.
 * Provides size tracking and automatic cleanup.
 */
object DiskCacheManager {

    /**
     * Get the cache directory for a specific type.
     */
    fun getCacheDir(context: Context, type: String): File {
        val dir = File(context.cacheDir, type)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Get the size of a cache directory in bytes.
     */
    fun getCacheSize(dir: File): Long {
        if (!dir.exists()) return 0
        return dir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
    }

    /**
     * Clear a cache directory.
     */
    fun clearCache(dir: File) {
        if (!dir.exists()) return
        dir.walkTopDown().filter { it.isFile }.forEach { it.delete() }
    }

    /**
     * Clear cache files older than the specified age.
     */
    fun clearOldFiles(dir: File, maxAgeMs: Long) {
        if (!dir.exists()) return
        val now = System.currentTimeMillis()
        dir.walkTopDown().filter { it.isFile }.forEach { file ->
            if (now - file.lastModified() > maxAgeMs) {
                file.delete()
            }
        }
    }

    /**
     * Get total cache size for all cache directories.
     */
    fun getTotalCacheSize(context: Context): Long {
        return getCacheSize(context.cacheDir)
    }

    /**
     * Clear all caches.
     */
    fun clearAllCaches(context: Context) {
        clearCache(context.cacheDir)
    }

    /**
     * Format bytes to human-readable string.
     */
    fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "${bytes} B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }
}
