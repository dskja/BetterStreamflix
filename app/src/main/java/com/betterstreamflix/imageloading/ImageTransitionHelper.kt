package com.betterstreamflix.imageloading

import android.widget.ImageView

/**
 * Image transition helper — provides smooth crossfade transitions
 * when loading images into ImageViews.
 */
object ImageTransitionHelper {

    /**
     * Crossfade from current image to new image.
     */
    fun crossfade(imageView: ImageView, newBitmap: android.graphics.Bitmap, durationMs: Long = 300L) {
        val oldDrawable = imageView.drawable
            ?: android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT)
        val newDrawable = android.graphics.drawable.BitmapDrawable(imageView.resources, newBitmap)

        val transitionDrawable = android.graphics.drawable.TransitionDrawable(
            arrayOf(oldDrawable, newDrawable)
        )

        imageView.setImageDrawable(transitionDrawable)
        transitionDrawable.startTransition(durationMs.toInt())
    }

    /**
     * Fade in a new image.
     */
    fun fadeInImage(imageView: ImageView, bitmap: android.graphics.Bitmap, durationMs: Long = 300L) {
        imageView.alpha = 0f
        imageView.setImageBitmap(bitmap)
        imageView.animate().alpha(1f).setDuration(durationMs).start()
    }

    /**
     * Fade out current image.
     */
    fun fadeOutImage(imageView: ImageView, durationMs: Long = 200L, onEnd: () -> Unit = {}) {
        imageView.animate()
            .alpha(0f)
            .setDuration(durationMs)
            .withEndAction { onEnd() }
            .start()
    }
}
