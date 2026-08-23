package com.betterstreamflix.imageloading

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.LruCache

/**
 * Image processor — provides image transformation utilities including
 * resizing, cropping, rounding, and blur effects.
 */
object ImageProcessor {

    /**
     * Resize a bitmap to fit within the given dimensions.
     */
    fun resize(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        val ratio = minOf(maxWidth.toFloat() / width, maxHeight.toFloat() / height)
        if (ratio >= 1f) return bitmap

        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /**
     * Crop a bitmap to a specific aspect ratio.
     */
    fun cropToRatio(bitmap: Bitmap, targetRatio: Float): Bitmap {
        val srcWidth = bitmap.width
        val srcHeight = bitmap.height
        val srcRatio = srcWidth.toFloat() / srcHeight

        val (cropWidth, cropHeight, x, y) = if (srcRatio > targetRatio) {
            val cw = (srcHeight * targetRatio).toInt()
            val cx = (srcWidth - cw) / 2
            Quad(cw, srcHeight, cx, 0)
        } else {
            val ch = (srcWidth / targetRatio).toInt()
            val cy = (srcHeight - ch) / 2
            Quad(srcWidth, ch, 0, cy)
        }

        return Bitmap.createBitmap(bitmap, x, y, cropWidth, cropHeight)
    }

    /**
     * Create a circular bitmap from a rectangular one.
     */
    fun toCircular(bitmap: Bitmap): Bitmap {
        val size = minOf(bitmap.width, bitmap.height)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = Rect(0, 0, size, size)

        canvas.drawARGB(0, 0, 0, 0)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, rect, rect, paint)

        return output
    }

    /**
     * Create a rounded rectangle bitmap.
     */
    fun toRounded(bitmap: Bitmap, cornerRadius: Float): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val rect = Rect(0, 0, bitmap.width, bitmap.height)

        canvas.drawARGB(0, 0, 0, 0)
        canvas.drawRoundRect(
            android.graphics.RectF(rect),
            cornerRadius,
            cornerRadius,
            paint,
        )
        paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, rect, rect, paint)

        return output
    }

    /**
     * Apply a color overlay to a bitmap.
     */
    fun applyColorOverlay(bitmap: Bitmap, color: Int, alpha: Float = 0.3f): Bitmap {
        val output = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        val paint = Paint().apply {
            this.color = color
            this.alpha = (alpha * 255).toInt()
        }
        canvas.drawRect(0f, 0f, output.width.toFloat(), output.height.toFloat(), paint)
        return output
    }

    /**
     * Create a placeholder bitmap with a solid color.
     */
    fun createPlaceholder(width: Int, height: Int, color: Int = Color.parseColor("#1a1a1a")): Bitmap {
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            eraseColor(color)
        }
    }

    /**
     * Calculate inSampleSize for BitmapFactory.Options.
     */
    fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height, width) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    private data class Quad(val width: Int, val height: Int, val x: Int, val y: Int)
}
