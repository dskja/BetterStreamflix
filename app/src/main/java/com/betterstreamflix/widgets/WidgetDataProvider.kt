package com.betterstreamflix.widgets

import android.content.Context
import android.graphics.Bitmap
import android.widget.RemoteViews
import com.betterstreamflix.database.AppDataRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Widget data provider — provides data for widget display including
 * continue watching items and favorites.
 */
object WidgetDataProvider {

    /**
     * Get continue watching items for widget.
     */
    fun getContinueWatchingItems(context: Context, maxItems: Int = 5): List<WidgetItem> {
        return runBlocking {
            AppDataRepository(context).getContinueWatching().first()
                .take(maxItems)
                .map {
                    WidgetItem(
                        id = it.videoId,
                        title = it.title,
                        subtitle = it.providerName,
                        posterUrl = it.thumbnailUrl,
                        progressPercent = it.progressPercent.toInt(),
                        intentAction = "continue",
                    )
                }
        }
    }

    /**
     * Get favorite items for widget.
     */
    fun getFavoriteItems(context: Context, maxItems: Int = 5): List<WidgetItem> {
        return runBlocking {
            AppDataRepository(context).getFavorites().first()
                .take(maxItems)
                .map {
                    WidgetItem(
                        id = it.videoId,
                        title = it.title,
                        subtitle = it.providerName,
                        posterUrl = it.thumbnailUrl,
                        progressPercent = 0,
                        intentAction = "favorites",
                    )
                }
        }
    }

    /**
     * Get trending items for widget.
     */
    fun getTrendingItems(context: Context, maxItems: Int = 5): List<WidgetItem> {
        // In real implementation, would fetch trending content
        return emptyList()
    }

    /**
     * Populate a RemoteViews with widget items.
     */
    fun populateRemoteViews(
        views: RemoteViews,
        items: List<WidgetItem>,
        titleViewId: Int,
        subtitleViewId: Int,
    ) {
        if (items.isEmpty()) {
            views.setTextViewText(titleViewId, "No content available")
            views.setTextViewText(subtitleViewId, "Watch something to see it here")
            return
        }

        val firstItem = items.firstOrNull() ?: return
        views.setTextViewText(titleViewId, firstItem.title)
        views.setTextViewText(subtitleViewId, firstItem.subtitle)
    }

    data class WidgetItem(
        val id: String,
        val title: String,
        val subtitle: String,
        val posterUrl: String?,
        val progressPercent: Int,
        val intentAction: String,
    )
}

