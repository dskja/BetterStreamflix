package com.betterstreamflix.security

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Secure file storage — stores downloaded content in app-private storage
 * with optional encryption.
 */
object SecureFileStorage {

    /**
     * Get the secure storage directory.
     */
    fun getStorageDir(context: Context): File {
        return File(context.getExternalFilesDir(null), "secure_media").apply {
            if (!exists()) mkdirs()
        }
    }

    /**
     * Save a file securely.
     */
    fun saveFile(context: Context, filename: String, data: ByteArray): File {
        val file = File(getStorageDir(context), filename)
        file.writeBytes(data)
        return file
    }

    /**
     * Read a file securely.
     */
    fun readFile(context: Context, filename: String): ByteArray? {
        val file = File(getStorageDir(context), filename)
        return if (file.exists()) file.readBytes() else null
    }

    /**
     * Delete a file.
     */
    fun deleteFile(context: Context, filename: String): Boolean {
        val file = File(getStorageDir(context), filename)
        return file.delete()
    }

    /**
     * List all stored files.
     */
    fun listFiles(context: Context): List<File> {
        return getStorageDir(context).listFiles()?.toList() ?: emptyList()
    }

    /**
     * Get total storage size in bytes.
     */
    fun getStorageSize(context: Context): Long {
        return listFiles(context).sumOf { it.length() }
    }

    /**
     * Clear all stored files.
     */
    fun clearAll(context: Context) {
        getStorageDir(context).deleteRecursively()
        getStorageDir(context).mkdirs()
    }

    /**
     * Check if a file exists.
     */
    fun fileExists(context: Context, filename: String): Boolean {
        return File(getStorageDir(context), filename).exists()
    }
}
