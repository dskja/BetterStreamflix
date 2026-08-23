package com.betterstreamflix.ui

import android.content.Context
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.ScaleAnimation

/**
 * View animation helper — provides common animations for UI elements.
 */
object ViewAnimationHelper {

    /**
     * Fade in a view.
     */
    fun fadeIn(view: View, duration: Long = 300, onEnd: (() -> Unit)? = null) {
        view.visibility = View.VISIBLE
        val anim = AlphaAnimation(0f, 1f).apply {
            this.duration = duration
            interpolator = AccelerateDecelerateInterpolator()
            setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(a: Animation?) {}
                override fun onAnimationEnd(a: Animation?) { onEnd?.invoke() }
                override fun onAnimationRepeat(a: Animation?) {}
            })
        }
        view.startAnimation(anim)
    }

    /**
     * Fade out a view.
     */
    fun fadeOut(view: View, duration: Long = 300, onEnd: (() -> Unit)? = null) {
        val anim = AlphaAnimation(1f, 0f).apply {
            this.duration = duration
            interpolator = AccelerateDecelerateInterpolator()
            setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(a: Animation?) {}
                override fun onAnimationEnd(a: Animation?) {
                    view.visibility = View.GONE
                    onEnd?.invoke()
                }
                override fun onAnimationRepeat(a: Animation?) {}
            })
        }
        view.startAnimation(anim)
    }

    /**
     * Scale up a view (e.g., for focus on TV).
     */
    fun scaleUp(view: View, scale: Float = 1.1f, duration: Long = 200) {
        val anim = ScaleAnimation(
            1f, scale, 1f, scale,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f,
        ).apply {
            this.duration = duration
            fillAfter = true
            interpolator = AccelerateDecelerateInterpolator()
        }
        view.startAnimation(anim)
    }

    /**
     * Scale down a view to normal.
     */
    fun scaleDown(view: View, duration: Long = 200) {
        val anim = ScaleAnimation(
            view.scaleX, 1f, view.scaleY, 1f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f,
        ).apply {
            this.duration = duration
            fillAfter = true
            interpolator = AccelerateDecelerateInterpolator()
        }
        view.startAnimation(anim)
    }

    /**
     * Toggle visibility with fade.
     */
    fun toggleVisibility(view: View, duration: Long = 300) {
        if (view.visibility == View.VISIBLE) fadeOut(view, duration) else fadeIn(view, duration)
    }
}
