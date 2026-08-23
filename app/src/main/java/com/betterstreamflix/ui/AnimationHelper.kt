package com.betterstreamflix.ui

import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.core.view.ViewCompat
import androidx.core.view.ViewPropertyAnimatorCompat

/**
 * Animation helper — provides standardized animations for views
 * with consistent durations and interpolators.
 */
object AnimationHelper {

    const val DURATION_SHORT = 150L
    const val DURATION_MEDIUM = 300L
    const val DURATION_LONG = 500L

    /**
     * Fade in a view.
     */
    fun fadeIn(view: View, duration: Long = DURATION_MEDIUM): ViewPropertyAnimatorCompat {
        view.alpha = 0f
        view.visibility = View.VISIBLE
        return ViewCompat.animate(view).alpha(1f).setDuration(duration)
            .setInterpolator(DecelerateInterpolator())
    }

    /**
     * Fade out a view.
     */
    fun fadeOut(view: View, duration: Long = DURATION_MEDIUM, onEnd: () -> Unit = {}): ViewPropertyAnimatorCompat {
        return ViewCompat.animate(view).alpha(0f).setDuration(duration)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction { onEnd() }
    }

    /**
     * Slide in from bottom.
     */
    fun slideInFromBottom(view: View, distance: Float = 200f, duration: Long = DURATION_MEDIUM): ViewPropertyAnimatorCompat {
        view.translationY = distance
        view.alpha = 0f
        view.visibility = View.VISIBLE
        return ViewCompat.animate(view).translationY(0f).alpha(1f).setDuration(duration)
            .setInterpolator(DecelerateInterpolator())
    }

    /**
     * Slide out to bottom.
     */
    fun slideOutToBottom(view: View, distance: Float = 200f, duration: Long = DURATION_MEDIUM, onEnd: () -> Unit = {}): ViewPropertyAnimatorCompat {
        return ViewCompat.animate(view).translationY(distance).alpha(0f).setDuration(duration)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction { onEnd() }
    }

    /**
     * Scale in (pop animation).
     */
    fun scaleIn(view: View, duration: Long = DURATION_MEDIUM): ViewPropertyAnimatorCompat {
        view.scaleX = 0f
        view.scaleY = 0f
        view.visibility = View.VISIBLE
        return ViewCompat.animate(view).scaleX(1f).scaleY(1f).setDuration(duration)
            .setInterpolator(AccelerateDecelerateInterpolator())
    }

    /**
     * Scale out.
     */
    fun scaleOut(view: View, duration: Long = DURATION_SHORT, onEnd: () -> Unit = {}): ViewPropertyAnimatorCompat {
        return ViewCompat.animate(view).scaleX(0f).scaleY(0f).setDuration(duration)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction { onEnd() }
    }

    /**
     * Shake animation (for errors).
     */
    fun shake(view: View, intensity: Float = 10f) {
        view.animate()
            .translationX(intensity)
            .setDuration(50)
            .withEndAction {
                view.animate().translationX(-intensity).setDuration(50)
                    .withEndAction {
                        view.animate().translationX(intensity / 2).setDuration(50)
                            .withEndAction {
                                view.animate().translationX(0f).setDuration(50).start()
                            }.start()
                    }.start()
            }.start()
    }

    /**
     * Pulse animation (for highlights).
     */
    fun pulse(view: View, scale: Float = 1.05f, duration: Long = 1000L) {
        view.animate()
            .scaleX(scale).scaleY(scale)
            .setDuration(duration / 2)
            .withEndAction {
                view.animate().scaleX(1f).scaleY(1f).setDuration(duration / 2).start()
            }.start()
    }
}
