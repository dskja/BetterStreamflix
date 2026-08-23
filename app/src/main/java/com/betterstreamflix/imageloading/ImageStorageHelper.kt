package com.betterstreamflix.imageloading

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * Image storage helper — saves and loads images from app-private
 * storage for posters, backdrops, and thumbnails.
 */
object ImageStorageHelper {

    private const val POSTERS_DIR = "posters"
    private const val BACKDROPS_DIR = "backdrops"
    private const val THUMBNAILS_DIR = "thumbnails"

    /**
     * Save a poster image.
     */
    fun savePoster(context: Context, contentId: String, bitmap: Bitmap): File? {
        return saveImage(context, POSTERS_DIR, contentId, bitmap)
    }

    /**
     * Save a backdrop image.
     */
    fun saveBackdrop(context: Context, contentId: String, bitmap: Bitmap): File? {
        return saveImage(context, BACKDROPS_DIR, contentId, bitmap)
    }

    /**
     * Save a thumbnail image.
     */
    fun saveThumbnail(context: Context, contentId: String, bitmap: Bitmap): File? {
        return saveImage(context, THUMBNAILS_DIR, contentId, bitmap)
    }

    /**
     * Load a poster image.
     */
    fun loadPoster(context: Context, contentId: String): Bitmap? {
        return loadImage(context, POSTERS_DIR, contentId)
    }

    /**
     * Load a backdrop image.
     */
    fun loadBackdrop(context: Context, contentId: String): Bitmap? {
        return loadImage(context, BACKDROPS_DIR, contentId)
    }

    /**
     * Load a thumbnail image.
     */
    fun loadThumbnail(context: Context, contentId: String): Bitmap? {
        return loadImage(context, THUMBNAILS_DIR, contentId)
    }

    /**
     * Delete all images for a content ID.
     */
    fun deleteAllForContent(context: Context, contentId: String) {
        getFile(context, POSTERS_DIR, contentId)?.delete()
        getFile(context, BACKDROPS_DIR, contentId)?.delete()
        getFile(context, THUMBNAILS_DIR, contentId)?.delete()
    }

    /**
     * Get total storage used by images.
     */
    fun getTotalStorageUsed(context: Context): Long {
        var total = 0L
        listOf(POSTERS_DIR, BACKDROPS_DIR, THUMBNAILS_DIR).forEach { dir ->
            val directory = File(context.filesDir, dir)
            if (directory.exists()) {
                total += directory.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            }
        }
        return total
    }

    /**
     * Clear all stored images.
     */
    fun clearAll(context: Context) {
        listOf(POSTERS_DIR, BACKDROPS_DIR, THUMBNAILS_DIR).forEach { dir ->
            File(context.filesDir, dir).deleteRecursively()
        }
    }

    private fun saveImage(context: Context, dir: String, id: String, bitmap: Bitmap): File? {
        val file = getFile(context, dir, id) ?: return null
        file.parentFile?.mkdirs()
        return try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.WEBP, 85, out)
            }
            file
        } catch (e: Exception) {
            null
        }
    }

    private fun loadImage(context: Context, dir: String, id: String): Bitmap? {
        val file = getFile(context, dir, id) ?: return null
        if (!file.exists()) return null
        return BitmapFactory.decodeFile(file.absolutePath)
    }

    private fun getFile(context: Context, dir: String, id: String): File? {
        return try {
            File(context.filesDir, "$dir/$id.webp")
        } catch (e: Exception) {
            null
        }
    }
}
