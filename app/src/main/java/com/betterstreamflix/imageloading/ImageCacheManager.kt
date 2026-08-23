package com.betterstreamflix.imageloading

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import java.io.File

/**
 * Image cache manager — manages memory and disk caches for images
 * with size-based eviction.
 */
object ImageCacheManager {

    private const val DISK_CACHE_DIR = "image_cache"
    private const val DEFAULT_DISK_CACHE_SIZE = 50L * 1024 * 1024 // 50MB

    private var memoryCache: LruCache<String, Bitmap>? = null
    private var diskCacheDir: File? = null
    private var maxDiskCacheSize: Long = DEFAULT_DISK_CACHE_SIZE

    /**
     * Initialize the image cache.
     */
    fun initialize(context: Context, memoryFraction: Float = 0.15f, diskSizeMb: Long = 50) {
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSize = (maxMemory * memoryFraction).toInt()

        memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
            override fun sizeOf(key: String, value: Bitmap): Int {
                return value.byteCount / 1024
            }
        }

        diskCacheDir = File(context.cacheDir, DISK_CACHE_DIR).apply { mkdirs() }
        maxDiskCacheSize = diskSizeMb * 1024 * 1024
    }

    /**
     * Get a bitmap from memory cache.
     */
    fun getFromMemory(key: String): Bitmap? = memoryCache?.get(key)

    /**
     * Put a bitmap into memory cache.
     */
    fun putToMemory(key: String, bitmap: Bitmap) {
        memoryCache?.put(key, bitmap)
    }

    /**
     * Get a bitmap from disk cache.
     */
    fun getFromDisk(key: String): Bitmap? {
        val file = getDiskCacheFile(key) ?: return null
        if (!file.exists()) return null
        return android.graphics.BitmapFactory.decodeFile(file.absolutePath)
    }

    /**
     * Put a bitmap into disk cache.
     */
    fun putToDisk(key: String, bitmap: Bitmap) {
        val file = getDiskCacheFile(key) ?: return
        try {
            file.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.WEBP, 80, out)
            }
        } catch (e: Exception) {
            // Ignore write errors
        }
    }

    /**
     * Get from both caches.
     */
    fun get(key: String): Bitmap? {
        return getFromMemory(key) ?: getFromDisk(key)?.also { putToMemory(key, it) }
    }

    /**
     * Put to both caches.
     */
    fun put(key: String, bitmap: Bitmap) {
        putToMemory(key, bitmap)
        putToDisk(key, bitmap)
    }

    /**
     * Remove from cache.
     */
    fun remove(key: String) {
        memoryCache?.remove(key)
        getDiskCacheFile(key)?.delete()
    }

    /**
     * Clear all caches.
     */
    fun clear() {
        memoryCache?.evictAll()
        diskCacheDir?.deleteRecursively()
        diskCacheDir?.mkdirs()
    }

    /**
     * Get disk cache size.
     */
    fun getDiskCacheSize(): Long {
        val dir = diskCacheDir ?: return 0
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    /**
     * Trim disk cache to max size.
     */
    fun trimDiskCache() {
        val dir = diskCacheDir ?: return
        val files = dir.listFiles()?.sortedBy { it.lastModified() } ?: return
        var totalSize = files.sumOf { it.length() }
        for (file in files) {
            if (totalSize <= maxDiskCacheSize) break
            totalSize -= file.length()
            file.delete()
        }
    }

    private fun getDiskCacheFile(key: String): File? {
        val dir = diskCacheDir ?: return null
        val safeKey = key.hashCode().toString()
        return File(dir, safeKey)
    }
}
