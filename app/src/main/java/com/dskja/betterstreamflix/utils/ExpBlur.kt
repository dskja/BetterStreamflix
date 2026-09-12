package com.dskja.betterstreamflix.utils

import android.app.Activity
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.ViewGroup
import eightbitlab.com.blurview.BlurView
import eightbitlab.com.blurview.RenderScriptBlur

/**
 * Lumina glass helper: wires a [BlurView] to blur the activity content behind
 * it. Degrades gracefully — if setup fails the static background stays.
 */
object ExpBlur {

    fun applyTo(blurView: BlurView, root: ViewGroup, radius: Float = 14f) {
        try {
            val activity = blurView.context as? Activity ?: return
            val decorView = activity.window.decorView
            val windowBackground: Drawable = decorView.background
                ?: ColorDrawable(android.graphics.Color.BLACK)
            blurView.setupWith(root, RenderScriptBlur(blurView.context))
                .setFrameClearDrawable(windowBackground)
                .setBlurRadius(radius)
        } catch (_: Throwable) {
            // Static glass background remains — blur is progressive enhancement.
        }
    }
}
