package com.streamflixreborn.streamflix.utils

import android.content.Context
import android.util.Log
import android.webkit.WebView
import com.bumptech.glide.Glide
import java.io.File

object CacheUtils {
    private const val TAG = "CacheUtils"

    fun clearAppCache(context: Context) {
        Log.d(TAG, "Inizio pulizia cache completa...")
        try {
            context.cacheDir?.deleteRecursively()
            Log.d(TAG, "Cache interna eliminata.")
        } catch (e: Exception) {
            Log.e(TAG, "Errore eliminazione cache interna: ${e.message}")
        }

        try {
            Glide.get(context).clearMemory()
            Thread {
                try {
                    Glide.get(context).clearDiskCache()
                    Log.d(TAG, "Cache Glide eliminata.")
                } catch (e: Exception) {
                    Log.e(TAG, "Errore eliminazione cache Glide: ${e.message}")
                }
            }.start()
        } catch (e: Exception) {
            Log.e(TAG, "Errore Glide: ${e.message}")
        }

        try {
            WebView(context).apply {
                clearCache(true)
                destroy()
            }
            Log.d(TAG, "Cache WebView eliminata.")
        } catch (e: Exception) {
            Log.e(TAG, "Errore WebView: ${e.message}")
        }
    }

    fun getCacheSize(context: Context): Long {
        var size: Long = 0
        try {
            size += getFolderSize(context.cacheDir)
            size += getFolderSize(context.externalCacheDir)
        } catch (_: Exception) {
        }
        return size
    }

    private fun getFolderSize(file: File?): Long {
        if (file == null || !file.exists()) return 0
        if (!file.isDirectory) return file.length()

        var size: Long = 0
        val files = file.listFiles()
        if (files != null) {
            for (f in files) {
                size += if (f.isDirectory) getFolderSize(f) else f.length()
            }
        }
        return size
    }

    fun autoClearIfNeeded(context: Context, thresholdMb: Long = 50) {
        val currentSize = getCacheSize(context)
        val thresholdBytes = thresholdMb * 1024 * 1024
        val currentMb = currentSize / (1024 * 1024)
        
        Log.d(TAG, "Controllo cache: Attuale = ${currentMb}MB, Soglia = ${thresholdMb}MB")
        
        if (currentSize > thresholdBytes) {
            Log.i(TAG, "Soglia superata! Avvio pulizia automatica...")
            clearAppCache(context)
        } else {
            Log.d(TAG, "Soglia non raggiunta. Nessuna pulizia necessaria.")
        }
    }
}
