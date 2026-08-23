package com.betterstreamflix.utils

import android.util.Log
import com.betterstreamflix.BuildConfig
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Performance monitor for tracking operation durations.
 * Only active in debug builds.
 */
object PerformanceMonitor {

    private data class TimingEntry(
        val tag: String,
        val durationMs: Long,
        val timestamp: Long = System.currentTimeMillis(),
    )

    private val timings = ConcurrentLinkedQueue<TimingEntry>()
    private const val MAX_ENTRIES = 200

    /**
     * Track the duration of a block.
     */
    inline fun <T> track(tag: String, block: () -> T): T {
        val start = if (BuildConfig.DEBUG) System.nanoTime() else 0L
        val result = block()
        if (BuildConfig.DEBUG) {
            val durationMs = (System.nanoTime() - start) / 1_000_000
            logTiming(tag, durationMs)
        }
        return result
    }

    /**
     * Track the duration of a suspend block.
     */
    suspend inline fun <T> trackSuspend(tag: String, crossinline block: suspend () -> T): T {
        val start = if (BuildConfig.DEBUG) System.nanoTime() else 0L
        val result = block()
        if (BuildConfig.DEBUG) {
            val durationMs = (System.nanoTime() - start) / 1_000_000
            logTiming(tag, durationMs)
        }
        return result
    }

    /**
     * Log a timing entry.
     */
    fun logTiming(tag: String, durationMs: Long) {
        if (durationMs > 500) {
            Log.w("PerfMonitor", "Slow operation: $tag took ${durationMs}ms")
        } else if (BuildConfig.DEBUG) {
            Log.d("PerfMonitor", "$tag: ${durationMs}ms")
        }
        timings.add(TimingEntry(tag, durationMs))
        while (timings.size > MAX_ENTRIES) timings.poll()
    }

    /**
     * Get all recorded timings.
     */
    fun getTimings(): List<Pair<String, Long>> {
        return timings.map { it.tag to it.durationMs }
    }

    /**
     * Get average duration for a tag.
     */
    fun getAverageDuration(tag: String): Double {
        val matching = timings.filter { it.tag == tag }
        if (matching.isEmpty()) return 0.0
        return matching.map { it.durationMs }.average()
    }

    /**
     * Clear all timings.
     */
    fun clear() = timings.clear()
}
