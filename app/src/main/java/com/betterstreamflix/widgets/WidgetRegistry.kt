package com.betterstreamflix.widgets

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

/**
 * Widget registry — manages widget registration and discovery.
 */
object WidgetRegistry {

    private val registeredWidgets = mutableListOf<WidgetRegistration>()

    data class WidgetRegistration(
        val className: String,
        val displayName: String,
        val description: String,
        val widgetClass: Class<out android.appwidget.AppWidgetProvider>,
    )

    /**
     * Register all app widgets.
     */
    fun registerAll() {
        registeredWidgets.clear()
        registeredWidgets.add(
            WidgetRegistration(
                className = "ContinueWatchingWidget",
                displayName = "Continue Watching",
                description = "Shows your recently watched content",
                widgetClass = ContinueWatchingWidget::class.java,
            ),
        )
        registeredWidgets.add(
            WidgetRegistration(
                className = "FavoritesWidget",
                displayName = "Favorites",
                description = "Quick access to your favorite content",
                widgetClass = FavoritesWidget::class.java,
            ),
        )
    }

    /**
     * Get all registered widgets.
     */
    fun getRegisteredWidgets(): List<WidgetRegistration> = registeredWidgets.toList()

    /**
     * Get a widget registration by class name.
     */
    fun getWidgetByClassName(className: String): WidgetRegistration? {
        return registeredWidgets.find { it.className == className }
    }

    /**
     * Check if any widgets are active for this app.
     */
    fun getActiveWidgetIds(context: Context): Map<String, IntArray> {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val result = mutableMapOf<String, IntArray>()

        registeredWidgets.forEach { widget ->
            val ids = appWidgetManager.getAppWidgetIds(
                ComponentName(context, widget.widgetClass),
            )
            if (ids.isNotEmpty()) {
                result[widget.className] = ids
            }
        }

        return result
    }

    /**
     * Get total active widget count.
     */
    fun getActiveWidgetCount(context: Context): Int {
        return getActiveWidgetIds(context).values.sumOf { it.size }
    }
}
