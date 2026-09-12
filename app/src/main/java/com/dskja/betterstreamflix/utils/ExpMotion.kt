package com.dskja.betterstreamflix.utils

import android.content.Context
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import android.view.animation.LayoutAnimationController
import android.view.animation.OvershootInterpolator
import androidx.recyclerview.widget.RecyclerView
import com.dskja.betterstreamflix.R

/**
 * Shared motion helpers for the experimental mobile shell. Every animation is
 * a no-op (or instant) when the experiment is off or the user has disabled
 * animations via the system animator-duration scale.
 */
object ExpMotion {

    fun reduceMotion(context: Context): Boolean =
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f

    private fun View.motionAllowed(): Boolean =
        ExperimentalMobileDesign.enabled() && !reduceMotion(context)

    /** Fade a view out and mark it GONE afterwards (loading screens). */
    fun fadeOutAndHide(view: View, duration: Long = 240) {
        if (!view.motionAllowed()) {
            view.visibility = View.GONE
            return
        }
        if (view.visibility != View.VISIBLE) {
            view.visibility = View.GONE
            return
        }
        view.animate().cancel()
        view.animate()
            .alpha(0f)
            .setDuration(duration)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                view.visibility = View.GONE
                view.alpha = 1f
            }
            .start()
    }

    /** Mark a view VISIBLE with a soft fade-in (loading screens, overlays). */
    fun fadeInAndShow(view: View, duration: Long = 280) {
        if (!view.motionAllowed()) {
            view.visibility = View.VISIBLE
            return
        }
        if (view.visibility == View.VISIBLE && view.alpha == 1f) return
        view.animate().cancel()
        view.alpha = 0f
        view.visibility = View.VISIBLE
        view.animate()
            .alpha(1f)
            .setDuration(duration)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    /** Small scale-pop used when badges/ribbons appear on cards. */
    fun popIn(view: View) {
        if (!view.motionAllowed()) return
        view.animate().cancel()
        view.scaleX = 0.55f
        view.scaleY = 0.55f
        view.alpha = 0f
        view.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(180)
            .setInterpolator(OvershootInterpolator(2.4f))
            .start()
    }

    /** Fragment-enter motion: soft fade + slight rise. */
    fun enterScreen(root: View, duration: Long = 260) {
        if (!root.motionAllowed()) return
        root.animate().cancel()
        root.alpha = 0f
        root.translationY = 16f * root.resources.displayMetrics.density
        root.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(duration)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    /**
     * One-shot staggered fade-in when a RecyclerView receives its first items.
     * Registers on the current adapter; defers a frame if none is attached yet.
     */
    fun staggerFirstFill(rv: RecyclerView, attempts: Int = 0) {
        if (!rv.motionAllowed()) return
        val adapter = rv.adapter
        if (adapter == null) {
            if (attempts < 10) {
                rv.postDelayed({ staggerFirstFill(rv, attempts + 1) }, 48)
            }
            return
        }
        rv.layoutAnimation = LayoutAnimationController(
            AnimationUtils.loadAnimation(rv.context, R.anim.exp_item_fade_in)
        ).apply { delay = 0.12f }
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            private var fired = false

            private fun fire() {
                if (fired) return
                fired = true
                rv.scheduleLayoutAnimation()
                runCatching { adapter.unregisterAdapterDataObserver(this) }
            }

            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) = fire()
            override fun onChanged() = fire()
        })
    }

    /** Load + start an XML animation, honouring reduced-motion. */
    fun startAnimation(view: View, animRes: Int) {
        if (!view.motionAllowed()) return
        view.startAnimation(AnimationUtils.loadAnimation(view.context, animRes))
    }

    /** Light haptic tick for primary actions. */
    fun hapticTap(view: View) {
        if (!ExperimentalMobileDesign.enabled()) return
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }
}
