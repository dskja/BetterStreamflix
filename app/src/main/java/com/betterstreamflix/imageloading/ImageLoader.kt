package com.betterstreamflix.imageloading

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.widget.ImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Image loader — coroutine-based image loading with caching,
 * placeholders, and error handling.
 */
object ImageLoader {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val pendingJobs = java.util.concurrent.ConcurrentHashMap<String, Job>()

    /**
     * Load an image into an ImageView.
     */
    fun load(
        imageView: ImageView,
        url: String?,
        placeholderColor: Int = Color.parseColor("#1a1a1a"),
        errorColor: Int = Color.parseColor("#333333"),
        cornerRadius: Float = 0f,
        circular: Boolean = false,
    ): Job {
        // Cancel any pending load for this imageView
        val key = imageView.hashCode().toString()
        pendingJobs[key]?.cancel()

        // Set placeholder
        imageView.setImageDrawable(ColorDrawable(placeholderColor))

        if (url.isNullOrBlank()) {
            imageView.setImageDrawable(ColorDrawable(errorColor))
            return Job()
        }

        // Check memory cache
        ImageCacheManager.getFromMemory(url)?.let { bitmap ->
            applyTransformations(imageView, bitmap, cornerRadius, circular)
            return Job()
        }

        val job = scope.launch {
            try {
                val bitmap = fetchBitmap(url)
                if (bitmap != null) {
                    ImageCacheManager.put(url, bitmap)
                    withContext(Dispatchers.Main) {
                        if (imageView.drawable !is ColorDrawable || (imageView.drawable as ColorDrawable).color != placeholderColor) return@withContext
                        applyTransformations(imageView, bitmap, cornerRadius, circular)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        imageView.setImageDrawable(ColorDrawable(errorColor))
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    imageView.setImageDrawable(ColorDrawable(errorColor))
                }
            } finally {
                pendingJobs.remove(key)
            }
        }

        pendingJobs[key] = job
        return job
    }

    /**
     * Preload an image into cache.
     */
    fun preload(url: String): Job {
        return scope.launch {
            if (ImageCacheManager.getFromMemory(url) == null) {
                val bitmap = fetchBitmap(url)
                bitmap?.let { ImageCacheManager.put(url, it) }
            }
        }
    }

    /**
     * Cancel all pending loads.
     */
    fun cancelAll() {
        pendingJobs.values.forEach { it.cancel() }
        pendingJobs.clear()
    }

    /**
     * Clear the image cache.
     */
    fun clearCache() {
        ImageCacheManager.clear()
    }

    private suspend fun fetchBitmap(url: String): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                val connection = java.net.URL(url).openConnection()
                connection.connectTimeout = 10_000
                connection.readTimeout = 15_000
                connection.getInputStream().use { input ->
                    android.graphics.BitmapFactory.decodeStream(input)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                null
            }
        }
    }

    private fun applyTransformations(imageView: ImageView, bitmap: Bitmap, cornerRadius: Float, circular: Boolean) {
        val transformed = when {
            circular -> ImageProcessor.toCircular(bitmap)
            cornerRadius > 0 -> ImageProcessor.toRounded(bitmap, cornerRadius)
            else -> bitmap
        }
        imageView.setImageBitmap(transformed)
    }
}
