package com.betterstreamflix.security

import android.content.Context
import androidx.core.content.edit

/**
 * Content lock — manages per-content or per-section locking
 * with PIN protection for parental controls.
 */
object ContentLockManager {

    private const val PREFS_NAME = "content_lock"

    /**
     * Lock a specific content item.
     */
    fun lockContent(context: Context, contentId: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putBoolean("locked_$contentId", true)
        }
    }

    /**
     * Unlock a specific content item.
     */
    fun unlockContent(context: Context, contentId: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putBoolean("locked_$contentId", false)
        }
    }

    /**
     * Check if content is locked.
     */
    fun isContentLocked(context: Context, contentId: String): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("locked_$contentId", false)
    }

    /**
     * Lock all content above a certain age rating.
     */
    fun lockByAgeRating(context: Context, maxAge: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putInt("max_age_rating", maxAge)
            putBoolean("age_lock_enabled", true)
        }
    }

    /**
     * Disable age-based content locking.
     */
    fun disableAgeLock(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putBoolean("age_lock_enabled", false)
        }
    }

    /**
     * Check if age-based locking is enabled.
     */
    fun isAgeLockEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("age_lock_enabled", false)
    }

    /**
     * Get the max age rating for locked content.
     */
    fun getMaxAgeRating(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt("max_age_rating", 0)
    }

    /**
     * Check if content should be locked based on age rating.
     */
    fun shouldLockByAge(context: Context, contentAgeRating: Int): Boolean {
        if (!isAgeLockEnabled(context)) return false
        return contentAgeRating > getMaxAgeRating(context)
    }

    /**
     * Get all locked content IDs.
     */
    fun getLockedContentIds(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.all.keys
            .filter { it.startsWith("locked_") && prefs.getBoolean(it, false) }
            .map { it.removePrefix("locked_") }
            .toSet()
    }
}
