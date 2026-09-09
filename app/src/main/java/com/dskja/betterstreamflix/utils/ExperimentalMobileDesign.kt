package com.dskja.betterstreamflix.utils

/**
 * Toggles the off-by-default experimental mobile shell redesign.
 * Layouts keep the same view IDs so existing ViewBindings can `.bind(view)`.
 */
object ExperimentalMobileDesign {
    fun enabled(): Boolean = UserPreferences.experimentalNewAppDesign

    fun layout(defaultRes: Int, experimentalRes: Int): Int =
        if (enabled()) experimentalRes else defaultRes
}
