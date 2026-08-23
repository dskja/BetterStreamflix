package com.betterstreamflix.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.betterstreamflix.R

/**
 * Reusable empty state view component.
 * Shows an icon/emoji, title, subtitle, and optional action button.
 */
object EmptyStateViewHelper {

    /**
     * Show an empty state view.
     */
    fun show(
        container: ViewGroup,
        title: String,
        subtitle: String? = null,
        actionButtonText: String? = null,
        onAction: (() -> Unit)? = null,
    ) {
        container.isVisible = true
        container.findViewById<TextView>(R.id.tv_empty_title)?.text = title
        container.findViewById<TextView>(R.id.tv_empty_subtitle)?.apply {
            if (subtitle != null) {
                text = subtitle
                isVisible = true
            } else {
                isVisible = false
            }
        }
        val actionButton = container.findViewById<Button>(R.id.btn_empty_action)
        if (actionButtonText != null && onAction != null) {
            actionButton?.isVisible = true
            actionButton?.text = actionButtonText
            actionButton?.setOnClickListener { onAction() }
        } else {
            actionButton?.isVisible = false
        }
    }

    /**
     * Hide an empty state view.
     */
    fun hide(container: ViewGroup) {
        container.isVisible = false
    }

    /**
     * Create an empty state view programmatically.
     */
    fun create(context: Context, parent: ViewGroup? = null): View {
        val padding = (32 * context.resources.displayMetrics.density).toInt()
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setPadding(padding, padding, padding, padding)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            addView(TextView(context).apply {
                id = R.id.tv_empty_title
                textSize = 18f
                gravity = android.view.Gravity.CENTER
            })
            addView(TextView(context).apply {
                id = R.id.tv_empty_subtitle
                textSize = 14f
                gravity = android.view.Gravity.CENTER
                setPadding(0, (8 * context.resources.displayMetrics.density).toInt(), 0, 0)
            })
            addView(Button(context).apply {
                id = R.id.btn_empty_action
            })
        }
    }
}
