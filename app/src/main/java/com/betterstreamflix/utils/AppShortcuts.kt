package com.betterstreamflix.utils

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import com.betterstreamflix.R
import com.betterstreamflix.activities.main.MainMobileActivity

/**
 * Publishes dynamic app shortcuts (Search / Continue / Favorites) on mobile.
 */
object AppShortcuts {

    fun publish(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
        val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return

        fun shortcut(id: String, label: String, host: String): ShortcutInfo {
            val intent = Intent(context, MainMobileActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = android.net.Uri.parse("betterstreamflix://$host")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            return ShortcutInfo.Builder(context, id)
                .setShortLabel(label)
                .setLongLabel(label)
                .setIcon(Icon.createWithResource(context, R.mipmap.ic_launcher))
                .setIntent(intent)
                .build()
        }

        val shortcuts = listOf(
            shortcut("search", context.getString(R.string.search_input_hint).take(20), "search"),
            shortcut("favorites", context.getString(R.string.main_menu_favorites), "provider"),
            shortcut("home", context.getString(R.string.home_continue_watching), "provider"),
        )
        runCatching { shortcutManager.dynamicShortcuts = shortcuts }
    }
}
