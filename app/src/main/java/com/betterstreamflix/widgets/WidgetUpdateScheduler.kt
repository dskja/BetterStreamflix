package com.betterstreamflix.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/**
 * Widget update scheduler — manages widget update scheduling
 * and periodic refresh.
 */
object WidgetUpdateScheduler {

    private const val WORK_NAME = "widget_update"

    /**
     * Schedule a one-shot WorkManager update for all widgets.
     */
    fun scheduleUpdate(context: Context) {
        val request = OneTimeWorkRequestBuilder<WidgetUpdateWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    /**
     * Schedule periodic widget updates.
     */
    fun scheduleUpdates(context: Context) {
        scheduleUpdate(context)
    }

    /**
     * Update all widgets immediately.
     */
    fun updateAllWidgets(context: Context) {
        ContinueWatchingWidget.updateAllWidgets(context)
        FavoritesWidget.updateAllWidgets(context)
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
