package com.betterstreamflix.widgets

import android.content.Context
import androidx.core.content.edit

/**
 * Widget state manager — tracks widget state including last update
 * time and data freshness.
 */
object WidgetStateManager {

    private const val PREFS_NAME = "widget_state"

    /**
     * Record widget update.
     */
    fun recordUpdate(context: Context, widgetId: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putLong("${widgetId}_last_update", System.currentTimeMillis())
            .apply()
    }

    /**
     * Get last update time for a widget.
     */
    fun getLastUpdateTime(context: Context, widgetId: Int): Long {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getLong("${widgetId}_last_update", 0)
    }

    /**
     * Check if widget data is stale.
     */
    fun isDataStale(context: Context, widgetId: Int, maxAgeMs: Long): Boolean {
        val lastUpdate = getLastUpdateTime(context, widgetId)
        if (lastUpdate == 0L) return true
        return System.currentTimeMillis() - lastUpdate > maxAgeMs
    }

    /**
     * Mark widget as needing update.
     */
    fun markNeedsUpdate(context: Context, widgetId: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean("${widgetId}_needs_update", true)
            .apply()
    }

    /**
     * Check if widget needs update.
     */
    fun needsUpdate(context: Context, widgetId: Int): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("${widgetId}_needs_update", false)
    }

    /**
     * Clear update flag.
     */
    fun clearUpdateFlag(context: Context, widgetId: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean("${widgetId}_needs_update", false)
            .apply()
    }

    /**
     * Remove all state for a widget.
     */
    fun removeWidget(context: Context, widgetId: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            remove("${widgetId}_last_update")
            remove("${widgetId}_needs_update")
        }
    }

    /**
     * Clear all widget state.
     */
    fun clearAll(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit { clear() }
    }
}
