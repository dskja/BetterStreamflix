package com.betterstreamflix.widgets

import android.content.Context
import androidx.core.content.edit

/**
 * Widget configuration helper — manages widget configuration
 * including size, content type, and refresh interval.
 */
object WidgetConfigurationHelper {

    private const val PREFS_NAME = "widget_config"

    data class WidgetConfig(
        val widgetId: Int,
        val contentType: WidgetContentType,
        val refreshIntervalMinutes: Int,
        val maxItems: Int,
        val showPoster: Boolean,
    )

    enum class WidgetContentType { CONTINUE_WATCHING, FAVORITES, TRENDING, NEW_RELEASES }

    /**
     * Get widget configuration.
     */
    fun getConfig(context: Context, widgetId: Int): WidgetConfig {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return WidgetConfig(
            widgetId = widgetId,
            contentType = WidgetContentType.entries.find {
                it.name == prefs.getString("${widgetId}_type", WidgetContentType.CONTINUE_WATCHING.name)
            } ?: WidgetContentType.CONTINUE_WATCHING,
            refreshIntervalMinutes = prefs.getInt("${widgetId}_refresh", 30),
            maxItems = prefs.getInt("${widgetId}_max", 5),
            showPoster = prefs.getBoolean("${widgetId}_poster", true),
        )
    }

    /**
     * Set widget configuration.
     */
    fun setConfig(context: Context, config: WidgetConfig) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putString("${config.widgetId}_type", config.contentType.name)
            putInt("${config.widgetId}_refresh", config.refreshIntervalMinutes)
            putInt("${config.widgetId}_max", config.maxItems)
            putBoolean("${config.widgetId}_poster", config.showPoster)
        }
    }

    /**
     * Delete widget configuration.
     */
    fun deleteConfig(context: Context, widgetId: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            remove("${widgetId}_type")
            remove("${widgetId}_refresh")
            remove("${widgetId}_max")
            remove("${widgetId}_poster")
        }
    }

    /**
     * Get refresh interval as milliseconds.
     */
    fun getRefreshIntervalMs(config: WidgetConfig): Long {
        return config.refreshIntervalMinutes * 60 * 1000L
    }
}
