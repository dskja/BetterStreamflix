package com.dskja.betterstreamflix.utils

/**
 * Off-by-default Nova Obsidian mobile shell — 2026 cinema redesign.
 * Experimental layouts keep the same view IDs so existing ViewBindings can `.bind(view)`.
 */
object ExperimentalMobileDesign {
    fun enabled(): Boolean = UserPreferences.experimentalNewAppDesign

    fun layout(defaultRes: Int, experimentalRes: Int): Int =
        if (enabled()) experimentalRes else defaultRes
}
