package com.dskja.betterstreamflix.utils

import android.graphics.Outline
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageView
import com.dskja.betterstreamflix.R

/**
 * Off-by-default Ember mobile shell — nocturnal cinema redesign with amber spotlight.
 * Experimental layouts keep the same view IDs so existing ViewBindings can `.bind(view)`.
 */
object ExperimentalMobileDesign {
    fun enabled(): Boolean = UserPreferences.experimentalNewAppDesign

    fun layout(defaultRes: Int, experimentalRes: Int): Int =
        if (enabled()) experimentalRes else defaultRes

    /** Soft-round poster chrome for Ember list/grid tiles. */
    fun stylePoster(imageView: ImageView) {
        if (!enabled()) return
        val radius = imageView.resources.getDimension(R.dimen.exp_poster_radius)
        imageView.clipToOutline = true
        imageView.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width.coerceAtLeast(0), view.height.coerceAtLeast(0), radius)
            }
        }
        imageView.invalidateOutline()
    }
}
