package com.betterstreamflix.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * Continue watching widget — shows recently watched content on the
 * home screen for quick resume.
 */
class ContinueWatchingWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { widgetId ->
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    companion object {
        /**
         * Update a single widget instance.
         */
        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, android.R.layout.simple_list_item_2)

            // Set title
            views.setTextViewText(android.R.id.text1, "Continue Watching")
            views.setTextViewText(android.R.id.text2, "Tap to open BetterStreamflix")

            // Set click intent to open app
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(android.R.id.text1, pendingIntent)

            appWidgetManager.updateAppWidget(widgetId, views)
        }

        /**
         * Update all widgets.
         */
        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val widgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, ContinueWatchingWidget::class.java),
            )
            widgetIds.forEach { updateWidget(context, appWidgetManager, it) }
        }
    }
}
