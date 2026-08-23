package com.betterstreamflix.widgets

import android.content.Context
import android.content.Intent
import androidx.core.content.edit

/**
 * Quick actions helper — provides quick action shortcuts for
 * launcher long-press menu.
 */
object QuickActionsHelper {

    private const val PREFS_NAME = "quick_actions"
    private const val KEY_ENABLED = "quick_actions_enabled"

    /**
     * Get quick action shortcuts.
     */
    fun getQuickActions(): List<QuickAction> {
        return listOf(
            QuickAction(
                id = "continue_watching",
                label = "Continue Watching",
                iconRes = android.R.drawable.ic_media_play,
                action = "continue_watching",
            ),
            QuickAction(
                id = "search",
                label = "Search",
                iconRes = android.R.drawable.ic_menu_search,
                action = "search",
            ),
            QuickAction(
                id = "favorites",
                label = "Favorites",
                iconRes = android.R.drawable.btn_star_big_on,
                action = "favorites",
            ),
            QuickAction(
                id = "downloads",
                label = "Downloads",
                iconRes = android.R.drawable.stat_sys_download,
                action = "downloads",
            ),
        )
    }

    /**
     * Check if quick actions are enabled.
     */
    fun isEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, true)
    }

    /**
     * Set quick actions enabled.
     */
    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, enabled).apply()
    }

    /**
     * Get intent for a quick action.
     */
    fun getActionIntent(context: Context, action: String): Intent {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: Intent()
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        intent.putExtra("quick_action", action)
        return intent
    }

    data class QuickAction(
        val id: String,
        val label: String,
        val iconRes: Int,
        val action: String,
    )
}
