package com.betterstreamflix.analytics

import android.content.Context
import androidx.core.content.edit

/**
 * Provider performance tracker — tracks response times and success
 * rates per provider for diagnostics.
 */
object ProviderPerformanceTracker {

    private const val PREFS_NAME = "provider_performance"

    data class ProviderStats(
        val providerName: String,
        val totalRequests: Int,
        val successfulRequests: Int,
        val failedRequests: Int,
        val averageResponseTimeMs: Long,
        val lastResponseTimeMs: Long,
        val successRate: Float,
    )

    /**
     * Record a successful request.
     */
    fun recordSuccess(context: Context, providerName: String, responseTimeMs: Long) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val total = prefs.getInt("${providerName}_total", 0) + 1
        val success = prefs.getInt("${providerName}_success", 0) + 1
        val totalTime = prefs.getLong("${providerName}_total_time", 0) + responseTimeMs

        prefs.edit {
            putInt("${providerName}_total", total)
            putInt("${providerName}_success", success)
            putLong("${providerName}_total_time", totalTime)
            putLong("${providerName}_last_time", responseTimeMs)
        }
    }

    /**
     * Record a failed request.
     */
    fun recordFailure(context: Context, providerName: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val total = prefs.getInt("${providerName}_total", 0) + 1
        val failed = prefs.getInt("${providerName}_failed", 0) + 1

        prefs.edit {
            putInt("${providerName}_total", total)
            putInt("${providerName}_failed", failed)
        }
    }

    /**
     * Get stats for a provider.
     */
    fun getStats(context: Context, providerName: String): ProviderStats {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val total = prefs.getInt("${providerName}_total", 0)
        val success = prefs.getInt("${providerName}_success", 0)
        val failed = prefs.getInt("${providerName}_failed", 0)
        val totalTime = prefs.getLong("${providerName}_total_time", 0)
        val lastTime = prefs.getLong("${providerName}_last_time", 0)

        return ProviderStats(
            providerName = providerName,
            totalRequests = total,
            successfulRequests = success,
            failedRequests = failed,
            averageResponseTimeMs = if (total > 0) totalTime / total else 0,
            lastResponseTimeMs = lastTime,
            successRate = if (total > 0) success.toFloat() / total else 0f,
        )
    }

    /**
     * Get stats for all providers.
     */
    fun getAllStats(context: Context, providerNames: List<String>): List<ProviderStats> {
        return providerNames.map { getStats(context, it) }
    }

    /**
     * Clear stats for a provider.
     */
    fun clearStats(context: Context, providerName: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit {
            remove("${providerName}_total")
            remove("${providerName}_success")
            remove("${providerName}_failed")
            remove("${providerName}_total_time")
            remove("${providerName}_last_time")
        }
    }

    /**
     * Clear all stats.
     */
    fun clearAll(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { clear() }
    }
}
