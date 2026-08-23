package com.betterstreamflix.notifications

import android.content.Context
import androidx.core.content.edit

/**
 * New content notifier — checks for new content on providers and
 * notifies the user.
 */
object NewContentNotifier {

    private const val PREFS_NAME = "new_content_seen"

    /**
     * Check for new content since last check.
     */
    fun getNewContentIds(
        context: Context,
        allContentIds: List<String>,
    ): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val seenIds = prefs.getStringSet("seen_ids", emptySet()) ?: emptySet()
        return allContentIds.filter { it !in seenIds }
    }

    /**
     * Mark content IDs as seen.
     */
    fun markContentAsSeen(context: Context, contentIds: List<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getStringSet("seen_ids", emptySet()) ?: emptySet()
        prefs.edit {
            putStringSet("seen_ids", existing + contentIds)
        }
    }

    /**
     * Check if new content notification should be shown.
     */
    fun shouldNotify(
        context: Context,
        newContentCount: Int,
        minNewItems: Int = 5,
    ): Boolean {
        return newContentCount >= minNewItems &&
            NotificationPreferences.isNewContentNotificationsEnabled(context)
    }

    /**
     * Clear seen content IDs.
     */
    fun clearSeenContent(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            remove("seen_ids")
        }
    }
}
