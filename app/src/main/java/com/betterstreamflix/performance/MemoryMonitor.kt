package com.betterstreamflix.performance

import android.app.ActivityManager
import android.content.Context
import android.os.Debug

/**
 * Memory monitor — tracks app memory usage and triggers cleanup
 * when memory pressure is high.
 */
object MemoryMonitor {

    private const val MEMORY_THRESHOLD_PERCENT = 80

    /**
     * Check if the app is under memory pressure.
     */
    fun isUnderMemoryPressure(context: Context): Boolean {
        val usage = getMemoryUsagePercent(context)
        return usage > MEMORY_THRESHOLD_PERCENT
    }

    /**
     * Get current memory usage as a percentage.
     */
    fun getMemoryUsagePercent(context: Context): Int {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return 0

        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val runtime = Runtime.getRuntime()
        val usedMem = runtime.totalMemory() - runtime.freeMemory()
        val maxMem = runtime.maxMemory()

        return ((usedMem.toFloat() / maxMem.toFloat()) * 100).toInt()
    }

    /**
     * Get used memory in MB.
     */
    fun getUsedMemoryMb(): Int {
        val runtime = Runtime.getRuntime()
        val usedBytes = runtime.totalMemory() - runtime.freeMemory()
        return (usedBytes / (1024 * 1024)).toInt()
    }

    /**
     * Get max heap size in MB.
     */
    fun getMaxHeapMb(): Int {
        return (Runtime.getRuntime().maxMemory() / (1024 * 1024)).toInt()
    }

    /**
     * Get native heap allocated size.
     */
    fun getNativeHeapAllocatedMb(): Int {
        return (Debug.getNativeHeapAllocatedSize() / (1024 * 1024)).toInt()
    }

    /**
     * Trigger garbage collection (use sparingly).
     */
    fun triggerGc() {
        System.gc()
    }

    /**
     * Get a memory report string for diagnostics.
     */
    fun getMemoryReport(context: Context): String {
        return buildString {
            appendLine("Memory Report:")
            appendLine("  Used: ${getUsedMemoryMb()} MB / ${getMaxHeapMb()} MB (${getMemoryUsagePercent(context)}%)")
            appendLine("  Native: ${getNativeHeapAllocatedMb()} MB")
            appendLine("  Under pressure: ${isUnderMemoryPressure(context)}")
        }
    }
}
