package com.betterstreamflix.tv

import android.content.Context
import android.view.View
import android.view.animation.AnimationUtils
import androidx.core.view.isInvisible
import androidx.core.view.isVisible

/**
 * TV onboarding helper — provides first-run onboarding for TV users
 * with navigation instructions.
 */
object TvOnboardingHelper {

    private const val PREFS_NAME = "tv_onboarding"
    private const val KEY_COMPLETED = "onboarding_completed"

    /**
     * Check if onboarding has been completed.
     */
    fun isOnboardingCompleted(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_COMPLETED, false)
    }

    /**
     * Mark onboarding as completed.
     */
    fun completeOnboarding(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_COMPLETED, true).apply()
    }

    /**
     * Get onboarding steps for TV.
     */
    fun getOnboardingSteps(): List<OnboardingStep> {
        return listOf(
            OnboardingStep(
                title = "Welcome to BetterStreamflix",
                description = "Your streaming hub for movies and TV shows",
                iconResId = android.R.drawable.ic_media_play,
            ),
            OnboardingStep(
                title = "Navigation",
                description = "Use the D-pad to navigate. Press OK to select.",
                iconResId = android.R.drawable.ic_menu_directions,
            ),
            OnboardingStep(
                title = "Search",
                description = "Press the microphone button to search with your voice",
                iconResId = android.R.drawable.ic_btn_speak_now,
            ),
            OnboardingStep(
                title = "Playback",
                description = "Press OK to pause. Use left/right to seek.",
                iconResId = android.R.drawable.ic_media_ff,
            ),
            OnboardingStep(
                title = "Settings",
                description = "Press the gear icon to customize your experience",
                iconResId = android.R.drawable.ic_menu_preferences,
            ),
        )
    }

    data class OnboardingStep(
        val title: String,
        val description: String,
        val iconResId: Int,
    )
}
