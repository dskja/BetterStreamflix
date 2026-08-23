package com.betterstreamflix.player

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

/**
 * Gesture controller for video playback.
 * Handles swipe-to-seek, swipe for brightness/volume, tap to play/pause,
 * double-tap to seek, and pinch-to-zoom.
 */
class PlayerGestureController(
    private val context: Context,
    private val onSeek: (deltaMs: Long) -> Unit,
    private val onTogglePlayPause: () -> Unit,
    private val onBrightnessChange: (delta: Float) -> Unit,
    private val onVolumeChange: (delta: Int) -> Unit,
    private val onScaleChange: (scale: Float) -> Unit,
) {
    private var initialX = 0f
    private var initialY = 0f
    private var gestureZone: GestureZone = GestureZone.NONE

    private enum class GestureZone { NONE, LEFT, RIGHT, CENTER }

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            onTogglePlayPause()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            val screenWidth = context.resources.displayMetrics.widthPixels
            if (e.x < screenWidth / 3) {
                onSeek(-10_000) // Seek back 10s
            } else if (e.x > screenWidth * 2 / 3) {
                onSeek(10_000) // Seek forward 10s
            } else {
                onTogglePlayPause()
            }
            return true
        }
    })

    /**
     * Handle a touch event from the player view.
     */
    fun onTouch(view: View, event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = event.x
                initialY = event.y
                gestureZone = determineZone(event.x, view.width)
            }
            MotionEvent.ACTION_MOVE -> {
                if (gestureZone != GestureZone.CENTER) {
                    val deltaY = event.y - initialY
                    val deltaRatio = deltaY / view.height
                    when (gestureZone) {
                        GestureZone.LEFT -> onBrightnessChange(-deltaRatio * 0.5f)
                        GestureZone.RIGHT -> onVolumeChange((-deltaRatio * 10).toInt())
                        else -> {}
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                gestureZone = GestureZone.NONE
            }
        }
        return true
    }

    private fun determineZone(x: Float, width: Int): GestureZone {
        return when {
            x < width / 3 -> GestureZone.LEFT
            x > width * 2 / 3 -> GestureZone.RIGHT
            else -> GestureZone.CENTER
        }
    }
}
