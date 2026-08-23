package com.betterstreamflix.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.transition.ChangeBounds
import androidx.transition.ChangeTransform
import androidx.transition.Fade
import androidx.transition.Scene
import androidx.transition.TransitionManager
import androidx.transition.TransitionSet

/**
 * Transition helper — manages shared element transitions and
 * scene transitions between screens.
 */
object TransitionHelper {

    /**
     * Start a delayed fade transition.
     */
    fun fadeTransition(viewGroup: ViewGroup, duration: Long = 300L) {
        val fade = Fade().apply { this.duration = duration }
        TransitionManager.beginDelayedTransition(viewGroup, fade)
    }

    /**
     * Start a delayed transition with change bounds.
     */
    fun changeBoundsTransition(viewGroup: ViewGroup, duration: Long = 300L) {
        val transition = ChangeBounds().apply {
            this.duration = duration
            addInterpolator(android.view.animation.AccelerateDecelerateInterpolator())
        }
        TransitionManager.beginDelayedTransition(viewGroup, transition)
    }

    /**
     * Start a combined transition.
     */
    fun combinedTransition(viewGroup: ViewGroup, duration: Long = 400L) {
        val transition = TransitionSet().apply {
            addTransition(ChangeBounds())
            addTransition(ChangeTransform())
            addTransition(Fade(Fade.IN))
            this.duration = duration
            ordering = TransitionSet.ORDERING_TOGETHER
        }
        TransitionManager.beginDelayedTransition(viewGroup, transition)
    }

    /**
     * Add a shared element name to a view.
     */
    fun addSharedElement(view: View, transitionName: String) {
        ViewCompat.setTransitionName(view, transitionName)
    }

    /**
     * Scale transition for card expansion.
     */
    fun scaleTransition(viewGroup: ViewGroup, duration: Long = 350L) {
        val transition = TransitionSet().apply {
            addTransition(ChangeBounds())
            addTransition(ChangeTransform())
            this.duration = duration
        }
        TransitionManager.beginDelayedTransition(viewGroup, transition)
    }
}
