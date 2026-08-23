package com.betterstreamflix.imageloading

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.edit

/**
 * Image prefetch manager — prefetches images for smoother scrolling
 * and pre-loading content posters.
 */
object ImagePrefetchManager {

    private const val PREFS_NAME = "image_prefetch"
    private const val KEY_PREFETCHED = "prefetched_urls"
    private val prefetchQueue = mutableListOf<String>()
    private var maxQueueSize: Int = 20

    /**
     * Add URLs to the prefetch queue.
     */
    fun addToQueue(urls: List<String>) {
        synchronized(prefetchQueue) {
            urls.forEach { url ->
                if (url !in prefetchQueue && !isPrefetched(url)) {
                    prefetchQueue.add(url)
                }
            }
            if (prefetchQueue.size > maxQueueSize) {
                prefetchQueue.subList(0, prefetchQueue.size - maxQueueSize).clear()
            }
        }
    }

    /**
     * Process the prefetch queue.
     */
    fun processQueue(): List<kotlinx.coroutines.Job> {
        val jobs = mutableListOf<kotlinx.coroutines.Job>()
        synchronized(prefetchQueue) {
            while (prefetchQueue.isNotEmpty()) {
                val url = prefetchQueue.removeAt(0)
                jobs.add(ImageLoader.preload(url))
            }
        }
        return jobs
    }

    /**
     * Check if a URL has been prefetched.
     */
    fun isPrefetched(url: String): Boolean {
        return ImageCacheManager.getFromMemory(url) != null || ImageCacheManager.getFromDisk(url) != null
    }

    /**
     * Prefetch a list of URLs immediately.
     */
    fun prefetchImmediately(urls: List<String>): List<kotlinx.coroutines.Job> {
        return urls.map { ImageLoader.preload(it) }
    }

    /**
     * Clear the prefetch queue.
     */
    fun clearQueue() {
        synchronized(prefetchQueue) { prefetchQueue.clear() }
    }

    /**
     * Set the maximum queue size.
     */
    fun setMaxQueueSize(size: Int) {
        maxQueueSize = size
    }

    /**
     * Get the current queue size.
     */
    fun getQueueSize(): Int = synchronized(prefetchQueue) { prefetchQueue.size }
}
