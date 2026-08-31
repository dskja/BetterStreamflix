package com.betterstreamflix.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * Favorites widget — shows favorite content on the home screen.
 */
class FavoritesWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { widgetId ->
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    companion object {
        fun updateWidget(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, android.R.layout.simple_list_item_2)
            val items = WidgetDataProvider.getFavoriteItems(context, maxItems = 1)
            val title = items.firstOrNull()?.title ?: "Favorites"
            val subtitle = items.firstOrNull()?.subtitle ?: "Tap to view your favorites"
            views.setTextViewText(android.R.id.text1, title)
            views.setTextViewText(android.R.id.text2, subtitle)

            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val pendingIntent = PendingIntent.getActivity(
                context, 1, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(android.R.id.text1, pendingIntent)

            appWidgetManager.updateAppWidget(widgetId, views)
        }

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val widgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, FavoritesWidget::class.java),
            )
            widgetIds.forEach { updateWidget(context, appWidgetManager, it) }
        }
    }
}
