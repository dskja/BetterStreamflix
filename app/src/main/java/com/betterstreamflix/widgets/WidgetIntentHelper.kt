package com.betterstreamflix.widgets

import android.content.Context
import android.content.Intent

/**
 * Widget intent helper — creates and manages intents for widget
 * interactions.
 */
object WidgetIntentHelper {

    /**
     * Create intent to open a specific content.
     */
    fun createOpenContentIntent(context: Context, contentId: String, providerName: String): Intent {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent()
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        intent.putExtra("action", "open_content")
        intent.putExtra("content_id", contentId)
        intent.putExtra("provider_name", providerName)
        return intent
    }

    /**
     * Create intent to open search.
     */
    fun createSearchIntent(context: Context, query: String? = null): Intent {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent()
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        intent.putExtra("action", "search")
        query?.let { intent.putExtra("query", it) }
        return intent
    }

    /**
     * Create intent to open favorites.
     */
    fun createFavoritesIntent(context: Context): Intent {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent()
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        intent.putExtra("action", "favorites")
        return intent
    }

    /**
     * Create intent to open downloads.
     */
    fun createDownloadsIntent(context: Context): Intent {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent()
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        intent.putExtra("action", "downloads")
        return intent
    }

    /**
     * Create intent to open settings.
     */
    fun createSettingsIntent(context: Context): Intent {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent()
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        intent.putExtra("action", "settings")
        return intent
    }

    /**
     * Parse intent action from a launch intent.
     */
    fun parseAction(intent: Intent): WidgetAction? {
        val action = intent.getStringExtra("action") ?: return null
        return when (action) {
            "open_content" -> WidgetAction.OpenContent(
                contentId = intent.getStringExtra("content_id") ?: return null,
                providerName = intent.getStringExtra("provider_name") ?: return null,
            )
            "search" -> WidgetAction.Search(query = intent.getStringExtra("query"))
            "favorites" -> WidgetAction.Favorites
            "downloads" -> WidgetAction.Downloads
            "settings" -> WidgetAction.Settings
            else -> null
        }
    }

    sealed class WidgetAction {
        data class OpenContent(val contentId: String, val providerName: String) : WidgetAction()
        data class Search(val query: String?) : WidgetAction()
        data object Favorites : WidgetAction()
        data object Downloads : WidgetAction()
        data object Settings : WidgetAction()
    }
}
