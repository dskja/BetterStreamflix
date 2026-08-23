package com.betterstreamflix.imageloading

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.PaintDrawable
import android.widget.ImageView

/**
 * Image placeholder generator — generates colored placeholder
 * images for content without posters.
 */
object ImagePlaceholderGenerator {

    private val placeholderColors = intArrayOf(
        Color.parseColor("#2E4053"),
        Color.parseColor("#1B4F72"),
        Color.parseColor("#641E16"),
        Color.parseColor("#4A235A"),
        Color.parseColor("#0E6251"),
        Color.parseColor("#7D6608"),
        Color.parseColor("#1A5276"),
        Color.parseColor("#512E5F"),
    )

    /**
     * Generate a placeholder for a title.
     */
    fun generate(title: String, width: Int = 200, height: Int = 300): Bitmap {
        val colorIndex = (title.hashCode() and 0x7FFFFFFF) % placeholderColors.size
        val bgColor = placeholderColors[colorIndex]

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(bgColor)

        // Draw gradient overlay
        val gradient = LinearGradient(
            0f, 0f, 0f, height.toFloat(),
            intArrayOf(Color.TRANSPARENT, Color.parseColor("#40000000")),
            null,
            Shader.TileMode.CLAMP,
        )
        val paint = android.graphics.Paint().apply { shader = gradient }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        // Draw first letter
        val firstLetter = title.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        val textPaint = android.graphics.Paint().apply {
            color = Color.WHITE
            textSize = width * 0.4f
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val x = width / 2f
        val y = height / 2f - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText(firstLetter, x, y, textPaint)

        return bitmap
    }

    /**
     * Set a placeholder on an ImageView.
     */
    fun setPlaceholder(imageView: ImageView, title: String) {
        val bitmap = generate(title, imageView.width.coerceAtLeast(100), imageView.height.coerceAtLeast(100))
        imageView.setImageBitmap(bitmap)
    }

    /**
     * Get a deterministic color for a title.
     */
    fun getColorForTitle(title: String): Int {
        val colorIndex = (title.hashCode() and 0x7FFFFFFF) % placeholderColors.size
        return placeholderColors[colorIndex]
    }

    /**
     * Generate a gradient background drawable.
     */
    fun generateGradientDrawable(title: String): GradientDrawable {
        val color = getColorForTitle(title)
        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(color, darkenColor(color, 0.7f)),
        )
    }

    private fun darkenColor(color: Int, factor: Float): Int {
        val r = (Color.red(color) * factor).toInt()
        val g = (Color.green(color) * factor).toInt()
        val b = (Color.blue(color) * factor).toInt()
        return Color.rgb(r, g, b)
    }
}
