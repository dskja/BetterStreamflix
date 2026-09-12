package com.dskja.betterstreamflix.utils

import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.view.View
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette

/**
 * Lumina "ambient light": extracts a vibrant swatch from hero artwork and
 * paints it as a soft radial glow behind a target view.
 */
object ExpAmbientGlow {

    fun apply(bitmap: Bitmap?, target: View) {
        if (bitmap == null || bitmap.isRecycled) return
        Palette.from(bitmap).generate { palette ->
            val swatch = palette?.vibrantSwatch
                ?: palette?.lightVibrantSwatch
                ?: palette?.mutedSwatch
                ?: palette?.dominantSwatch
                ?: return@generate
            applyColor(swatch.rgb, target)
        }
    }

    fun applyColor(color: Int, target: View) {
        target.post {
            val radius = (maxOf(target.width, target.height) / 2f).coerceAtLeast(1f)
            target.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                gradientType = GradientDrawable.RADIAL_GRADIENT
                gradientRadius = radius
                colors = intArrayOf(
                    ColorUtils.setAlphaComponent(color, 0x59),
                    ColorUtils.setAlphaComponent(color, 0x22),
                    ColorUtils.setAlphaComponent(color, 0x00),
                )
            }
            target.alpha = 0f
            target.visibility = View.VISIBLE
            target.animate().alpha(1f).setDuration(600).start()
        }
    }
}
