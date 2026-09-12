package com.dskja.betterstreamflix.utils

import android.annotation.SuppressLint
import android.provider.Settings
import android.view.MotionEvent
import android.view.View

/**
 * Experimental shell press feedback: subtle scale-down on touch, springy
 * release. Respects the system animator-duration scale (reduced motion).
 */
object ExpPressEffects {

    @SuppressLint("ClickableViewAccessibility")
    fun View.applyExpPress() {
        if (Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        ) return
        setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> v.animate()
                    .scaleX(0.96f).scaleY(0.96f)
                    .setDuration(110)
                    .start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate()
                    .scaleX(1f).scaleY(1f)
                    .setDuration(200)
                    .start()
            }
            false
        }
    }
}
