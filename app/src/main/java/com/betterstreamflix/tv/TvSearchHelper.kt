package com.betterstreamflix.tv

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View
import androidx.core.content.ContextCompat
import androidx.leanback.widget.SearchOrbView
import androidx.leanback.widget.TitleView

/**
 * TV search helper — provides search interface for TV layouts using
 * Leanback search fragment patterns.
 */
object TvSearchHelper {

    /**
     * Check if voice search is available on the device.
     */
    fun isVoiceSearchAvailable(context: Context): Boolean {
        val pm = context.packageManager
        return pm.hasSystemFeature("android.software.voice_recognition")
    }

    /**
     * Get speech recognizer intent.
     */
    fun getVoiceSearchIntent(): android.content.Intent {
        return android.content.Intent(android.content.Intent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Search for content")
        }
    }

    /**
     * Build a search query from voice recognition results.
     */
    fun extractSearchQuery(results: android.content.Intent): String? {
        val matches = results.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
        return matches?.firstOrNull()
    }

    /**
     * Create a search orb colors resource.
     */
    fun getSearchOrbColors(context: Context): SearchOrbView.Colors {
        return SearchOrbView.Colors(
            ContextCompat.getColor(context, android.R.color.holo_blue_dark),
            ContextCompat.getColor(context, android.R.color.holo_blue_light),
            ContextCompat.getColor(context, android.R.color.white),
        )
    }
}
