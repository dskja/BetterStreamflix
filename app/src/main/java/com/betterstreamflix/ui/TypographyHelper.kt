package com.betterstreamflix.ui

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.widget.TextView

/**
 * Typography helper — provides consistent text sizing and styling
 * following Material Design typography scale.
 */
object TypographyHelper {

    enum class TextScale {
        DISPLAY_LARGE,
        DISPLAY_MEDIUM,
        DISPLAY_SMALL,
        HEADLINE_LARGE,
        HEADLINE_MEDIUM,
        HEADLINE_SMALL,
        TITLE_LARGE,
        TITLE_MEDIUM,
        TITLE_SMALL,
        BODY_LARGE,
        BODY_MEDIUM,
        BODY_SMALL,
        LABEL_LARGE,
        LABEL_MEDIUM,
        LABEL_SMALL,
    }

    data class TextStyle(
        val textSizeSp: Float,
        val lineHeightSp: Float,
        val fontWeight: Int,
        val letterSpacing: Float,
    )

    private val textStyles = mapOf(
        TextScale.DISPLAY_LARGE to TextStyle(57f, 64f, 400, -0.25f),
        TextScale.DISPLAY_MEDIUM to TextStyle(45f, 52f, 400, 0f),
        TextScale.DISPLAY_SMALL to TextStyle(36f, 44f, 400, 0f),
        TextScale.HEADLINE_LARGE to TextStyle(32f, 40f, 400, 0f),
        TextScale.HEADLINE_MEDIUM to TextStyle(28f, 36f, 400, 0f),
        TextScale.HEADLINE_SMALL to TextStyle(24f, 32f, 400, 0f),
        TextScale.TITLE_LARGE to TextStyle(22f, 28f, 400, 0f),
        TextScale.TITLE_MEDIUM to TextStyle(16f, 24f, 500, 0.15f),
        TextScale.TITLE_SMALL to TextStyle(14f, 20f, 500, 0.1f),
        TextScale.BODY_LARGE to TextStyle(16f, 24f, 400, 0.5f),
        TextScale.BODY_MEDIUM to TextStyle(14f, 20f, 400, 0.25f),
        TextScale.BODY_SMALL to TextStyle(12f, 16f, 400, 0.4f),
        TextScale.LABEL_LARGE to TextStyle(14f, 20f, 500, 0.1f),
        TextScale.LABEL_MEDIUM to TextStyle(12f, 16f, 500, 0.5f),
        TextScale.LABEL_SMALL to TextStyle(11f, 16f, 500, 0.5f),
    )

    /**
     * Apply a text style to a TextView.
     */
    fun applyStyle(textView: TextView, scale: TextScale) {
        val style = textStyles[scale] ?: return
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, style.textSizeSp)
        textView.setLineSpacing(style.lineHeightSp - style.textSizeSp, 1f)
        textView.letterSpacing = style.letterSpacing / 100f
        textView.setTypeface(textView.typeface, if (style.fontWeight >= 500) Typeface.BOLD else Typeface.NORMAL)
    }

    /**
     * Get the text style for a scale.
     */
    fun getStyle(scale: TextScale): TextStyle? = textStyles[scale]

    /**
     * Get font size in sp for a scale.
     */
    fun getFontSize(scale: TextScale): Float = textStyles[scale]?.textSizeSp ?: 14f
}
