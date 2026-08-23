package com.betterstreamflix.imageloading

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * Image disk cache — disk-based image cache with LRU eviction.
 */
class ImageDiskCache(
    private val cacheDir: File,
    private val maxSizeBytes: Long = 50L * 1024 * 1024,
) {
    init {
        cacheDir.mkdirs()
    }

    /**
     * Get a bitmap from disk cache.
     */
    fun get(key: String): Bitmap? {
        val file = getFile(key)
        if (!file.exists()) return null
        return BitmapFactory.decodeFile(file.absolutePath)
    }

    /**
     * Put a bitmap into disk cache.
     */
    fun put(key: String, bitmap: Bitmap, format: Bitmap.CompressFormat = Bitmap.CompressFormat.WEBP, quality: Int = 80) {
        val file = getFile(key)
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(format, quality, out)
            }
        } catch (e: Exception) {
            file.delete()
        }
        trimIfNeeded()
    }

    /**
     * Remove a bitmap from disk cache.
     */
    fun remove(key: String) {
        getFile(key).delete()
    }

    /**
     * Clear the entire disk cache.
     */
    fun clear() {
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    /**
     * Get the current cache size.
     */
    fun size(): Long {
        return cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    /**
     * Check if a key exists in cache.
     */
    fun contains(key: String): Boolean = getFile(key).exists()

    /**
     * Get all cached file count.
     */
    fun count(): Int = cacheDir.listFiles()?.size ?: 0

    /**
     * Trim cache to max size by deleting oldest files.
     */
    fun trimIfNeeded() {
        var totalSize = size()
        if (totalSize <= maxSizeBytes) return

        val files = cacheDir.listFiles()?.sortedBy { it.lastModified() } ?: return
        for (file in files) {
            if (totalSize <= maxSizeBytes) break
            totalSize -= file.length()
            file.delete()
        }
    }

    private fun getFile(key: String): File {
        val hash = hashKey(key)
        return File(cacheDir, hash)
    }

    private fun hashKey(key: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(key.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }.take(32)
    }
}
