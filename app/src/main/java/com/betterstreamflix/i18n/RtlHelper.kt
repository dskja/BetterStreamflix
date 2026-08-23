package com.betterstreamflix.i18n

import android.content.Context
import android.view.View
import androidx.core.text.layoutdirection.LocaleLayoutDirection

/**
 * RTL helper — handles right-to-left layout adjustments.
 */
object RtlHelper {

    /**
     * Check if the current locale is RTL.
     */
    fun isRtl(context: Context): Boolean {
        return context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
    }

    /**
     * Get the layout direction for a locale.
     */
    fun getLayoutDirection(languageCode: String): Int {
        return if (LanguageDetector.isRtl(languageCode)) View.LAYOUT_DIRECTION_RTL
        else View.LAYOUT_DIRECTION_LTR
    }

    /**
     * Mirror a horizontal margin for RTL.
     */
    fun mirrorMargin(marginStart: Int, marginEnd: Int, isRtl: Boolean): Pair<Int, Int> {
        return if (isRtl) marginEnd to marginStart else marginStart to marginEnd
    }

    /**
     * Mirror a horizontal padding for RTL.
     */
    fun mirrorPadding(paddingStart: Int, paddingEnd: Int, isRtl: Boolean): Pair<Int, Int> {
        return if (isRtl) paddingEnd to paddingStart else paddingStart to paddingEnd
    }
}
