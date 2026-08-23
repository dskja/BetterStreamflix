package com.betterstreamflix.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.core.view.ViewCompat

/**
 * Tooltip helper — provides custom tooltips with rich content
 * for UI elements.
 */
object TooltipHelper {

    /**
     * Show a simple tooltip above a view.
     */
    fun showTooltip(
        anchor: View,
        text: String,
        durationMs: Long = 3000L,
    ) {
        val context = anchor.context
        val textView = android.widget.TextView(context).apply {
            setText(text)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#CC333333"))
            setPadding(24, 16, 24, 16)
            textSize = 14f
        }

        val popup = PopupWindow(textView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            isOutsideTouchable = true
            elevation = 8f
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }

        popup.showAsDropDown(anchor, 0, -anchor.height * 2, Gravity.CENTER)

        anchor.postDelayed({ popup.dismiss() }, durationMs)
    }

    /**
     * Show a tooltip at a specific position.
     */
    fun showTooltipAtPosition(
        anchor: View,
        text: String,
        x: Int,
        y: Int,
        durationMs: Long = 3000L,
    ) {
        val context = anchor.context
        val textView = android.widget.TextView(context).apply {
            setText(text)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#CC333333"))
            setPadding(24, 16, 24, 16)
            textSize = 14f
        }

        val popup = PopupWindow(textView, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            isOutsideTouchable = true
            elevation = 8f
        }

        popup.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y)
        anchor.postDelayed({ popup.dismiss() }, durationMs)
    }

    /**
     * Show a long-press tooltip with icon.
     */
    fun showRichTooltip(
        anchor: View,
        title: String,
        message: String,
        iconRes: Int? = null,
    ) {
        val context = anchor.context
        val container = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#E6333333"))
            setPadding(32, 24, 32, 24)
        }

        val titleView = android.widget.TextView(context).apply {
            setText(title)
            setTextColor(Color.WHITE)
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }

        val messageView = android.widget.TextView(context).apply {
            setText(message)
            setTextColor(Color.parseColor("#CCCCCC"))
            textSize = 14f
            setPadding(0, 8, 0, 0)
        }

        container.addView(titleView)
        container.addView(messageView)

        val popup = PopupWindow(container, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            isOutsideTouchable = true
            elevation = 12f
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }

        popup.showAsDropDown(anchor, 0, -anchor.height * 3, Gravity.CENTER)
    }
}
