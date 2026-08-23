package com.betterstreamflix.performance

import android.os.SystemClock
import java.util.concurrent.ConcurrentHashMap

/**
 * Performance profiler — measures execution time of operations
 * for diagnostics and optimization.
 */
object PerformanceProfiler {

    private val traces = ConcurrentHashMap<String, Long>()
    private val results = ConcurrentHashMap<String, MutableList<Long>>()

    /**
     * Start a trace.
     */
    fun startTrace(key: String) {
        traces[key] = SystemClock.elapsedRealtime()
    }

    /**
     * End a trace and record the duration.
     */
    fun endTrace(key: String): Long {
        val startTime = traces.remove(key) ?: return 0
        val duration = SystemClock.elapsedRealtime() - startTime
        results.getOrPut(key) { mutableListOf() }.add(duration)
        return duration
    }

    /**
     * Measure a block of code.
     */
    inline fun <T> measure(key: String, block: () -> T): T {
        startTrace(key)
        return try {
            block()
        } finally {
            endTrace(key)
        }
    }

    /**
     * Get average duration for a key.
     */
    fun getAverageDuration(key: String): Long {
        val durations = results[key] ?: return 0
        return durations.sum() / durations.size
    }

    /**
     * Get all recorded traces.
     */
    fun getAllTraces(): Map<String, Long> {
        return results.mapValues { it.value.sum() / it.value.size }
    }

    /**
     * Get trace count for a key.
     */
    fun getTraceCount(key: String): Int {
        return results[key]?.size ?: 0
    }

    /**
     * Clear all traces.
     */
    fun clear() {
        traces.clear()
        results.clear()
    }

    /**
     * Get a formatted report.
     */
    fun getReport(): String {
        return buildString {
            appendLine("Performance Report:")
            getAllTraces().forEach { (key, avg) ->
                appendLine("  $key: avg ${avg}ms (${getTraceCount(key)} calls)")
            }
        }
    }
}
