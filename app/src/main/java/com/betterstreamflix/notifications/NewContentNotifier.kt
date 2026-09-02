package com.betterstreamflix.notifications

import android.content.Context
import androidx.core.content.edit

/**
 * New content notifier — checks for new content on providers and
 * notifies the user.
 */
object NewContentNotifier {

    private const val PREFS_NAME = "new_content_seen"
    private const val MAX_SEEN_IDS = 2000

    fun scopedId(providerName: String, contentId: String): String = "$providerName:$contentId"

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
        if (contentIds.isEmpty()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getStringSet("seen_ids", emptySet()) ?: emptySet()
        val merged = (existing + contentIds).toList()
        val pruned = if (merged.size > MAX_SEEN_IDS) {
            merged.takeLast(MAX_SEEN_IDS).toSet()
        } else {
            merged.toSet()
        }
        prefs.edit {
            putStringSet("seen_ids", pruned)
        }
    }

    /**
     * Check if new content notification should be shown.
     */
    fun shouldNotify(
        context: Context,
        newContentCount: Int,
        minNewItems: Int = 1,
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
