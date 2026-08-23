package com.betterstreamflix.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

/**
 * Widget update scheduler — manages widget update scheduling
 * and periodic refresh.
 */
object WidgetUpdateScheduler {

    /**
     * Schedule periodic widget updates.
     */
    fun scheduleUpdates(context: Context) {
        // In real implementation, would use WorkManager to schedule periodic updates
        updateAllWidgets(context)
    }

    /**
     * Update all widgets immediately.
     */
    fun updateAllWidgets(context: Context) {
        ContinueWatchingWidget.updateAllWidgets(context)
        FavoritesWidget.updateAllWidgets(context)
    }

    /**
     * Update widgets for a specific content type.
     */
    fun updateWidgetsForContent(context: Context, contentType: WidgetConfigurationHelper.WidgetContentType) {
        when (contentType) {
            WidgetConfigurationHelper.WidgetContentType.CONTINUE_WATCHING -> {
                ContinueWatchingWidget.updateAllWidgets(context)
            }
            WidgetConfigurationHelper.WidgetContentType.FAVORITES -> {
                FavoritesWidget.updateAllWidgets(context)
            }
            else -> updateAllWidgets(context)
        }
    }

    /**
     * Check if any widgets are active.
     */
    fun hasActiveWidgets(context: Context): Boolean {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val continueWatchingIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, ContinueWatchingWidget::class.java),
        )
        val favoritesIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, FavoritesWidget::class.java),
        )
        return continueWatchingIds.isNotEmpty() || favoritesIds.isNotEmpty()
    }

    /**
     * Get count of active widgets.
     */
    fun getActiveWidgetCount(context: Context): Int {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val continueWatchingIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, ContinueWatchingWidget::class.java),
        )
        val favoritesIds = appWidgetManager.getAppWidgetIds(
            ComponentName(context, FavoritesWidget::class.java),
        )
        return continueWatchingIds.size + favoritesIds.size
    }
}
