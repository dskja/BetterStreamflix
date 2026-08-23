package com.betterstreamflix.analytics

import android.content.Context
import androidx.core.content.edit

/**
 * Usage tracker — tracks feature usage frequency for optimization
 * and UX improvements.
 */
object UsageTracker {

    private const val PREFS_NAME = "usage_tracking"
    private const val KEY_FEATURE_COUNTS = "feature_counts"
    private const val KEY_LAST_USED = "last_used"

    /**
     * Track a feature usage.
     */
    fun trackFeature(context: Context, feature: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val counts = prefs.getString(KEY_FEATURE_COUNTS, "{}") ?: "{}"
        val json = org.json.JSONObject(counts)
        val currentCount = json.optInt(feature, 0)
        json.put(feature, currentCount + 1)

        prefs.edit {
            putString(KEY_FEATURE_COUNTS, json.toString())
            putLong("$KEY_LAST_USED:$feature", System.currentTimeMillis())
        }
    }

    /**
     * Get feature usage count.
     */
    fun getFeatureCount(context: Context, feature: String): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val counts = prefs.getString(KEY_FEATURE_COUNTS, "{}") ?: "{}"
        return try {
            org.json.JSONObject(counts).optInt(feature, 0)
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Get all feature usage counts.
     */
    fun getAllFeatureCounts(context: Context): Map<String, Int> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val counts = prefs.getString(KEY_FEATURE_COUNTS, "{}") ?: "{}"
        return try {
            val json = org.json.JSONObject(counts)
            json.keys().asSequence().associateWith { json.optInt(it, 0) }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /**
     * Get the most used features.
     */
    fun getMostUsedFeatures(context: Context, limit: Int = 10): List<Pair<String, Int>> {
        return getAllFeatureCounts(context)
            .toList()
            .sortedByDescending { it.second }
            .take(limit)
    }

    /**
     * Get last used timestamp for a feature.
     */
    fun getLastUsed(context: Context, feature: String): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong("$KEY_LAST_USED:$feature", 0)
    }

    /**
     * Clear all usage data.
     */
    fun clearAll(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            clear()
        }
    }
}
