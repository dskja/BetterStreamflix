package com.betterstreamflix.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import com.betterstreamflix.R

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
            val views = RemoteViews(context.packageName, R.layout.widget_favorites)
            val items = WidgetDataProvider.getFavoriteItems(context, maxItems = 1)
            val title = items.firstOrNull()?.title
                ?: context.getString(R.string.main_menu_favorites)
            val subtitle = items.firstOrNull()?.subtitle
                ?: context.getString(R.string.widget_favorites_empty)
            views.setTextViewText(R.id.widget_favorites_title, title)
            views.setTextViewText(R.id.widget_favorites_subtitle, subtitle)

            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val pendingIntent = PendingIntent.getActivity(
                context, 1, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_favorites_title, pendingIntent)

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
