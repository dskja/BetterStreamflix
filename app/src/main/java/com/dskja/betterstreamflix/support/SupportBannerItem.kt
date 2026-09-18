package com.dskja.betterstreamflix.support

import com.dskja.betterstreamflix.adapters.AppAdapter

/**
 * Compact home-row support card. Independent from startup “never show again”.
 */
data class SupportBannerItem(
    val id: String = "support_banner",
) : AppAdapter.Item {
    override lateinit var itemType: AppAdapter.Type
}
