package com.betterstreamflix.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import com.betterstreamflix.R
import com.google.android.material.card.MaterialCardView

/**
 * Reusable error view component.
 * Shows an error icon, message, and optional retry button.
 *
 * Usage in layout:
 *   <include layout="@layout/layout_error_state"
 *       android:id="@+id/errorState"
 *       android:visibility="gone" />
 *
 * Usage in code:
 *   ErrorViewHelper.show(errorState, "Failed to load", "Retry") { retry() }
 *   ErrorViewHelper.hide(errorState)
 */
object ErrorViewHelper {

    /**
     * Show an error state view with message and optional retry button.
     */
    fun show(
        container: ViewGroup,
        message: String,
        retryButtonText: String? = null,
        onRetry: (() -> Unit)? = null,
    ) {
        container.isVisible = true
        container.findViewById<TextView>(R.id.tv_error_message)?.text = message
        val retryButton = container.findViewById<Button>(R.id.btn_retry)
        if (retryButtonText != null && onRetry != null) {
            retryButton?.isVisible = true
            retryButton?.text = retryButtonText
            retryButton?.setOnClickListener { onRetry() }
        } else {
            retryButton?.isVisible = false
        }
    }

    /**
     * Hide an error state view.
     */
    fun hide(container: ViewGroup) {
        container.isVisible = false
    }

    /**
     * Create an error view programmatically.
     */
    fun create(context: Context, parent: ViewGroup? = null): View {
        val padding = (24 * context.resources.displayMetrics.density).toInt()
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(padding, padding, padding, padding)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            addView(TextView(context).apply {
                id = R.id.tv_error_message
                textSize = 16f
                gravity = android.view.Gravity.CENTER
            })
            addView(Button(context).apply {
                id = R.id.btn_retry
                text = context.getString(R.string.loading_error_retry)
            })
        }
    }
}
