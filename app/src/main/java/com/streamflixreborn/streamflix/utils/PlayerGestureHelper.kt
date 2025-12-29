package com.streamflixreborn.streamflix.utils

import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.ProgressBar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

class PlayerGestureHelper(
    private val context: Context, 
    private val playerView: View,
    private val brightnessBar: ProgressBar,
    private val volumeBar: ProgressBar
) {

    private val gestureDetector: GestureDetector
    private val audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    init {
        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                val deltaX = e2.x - (e1?.x ?: 0f)
                val deltaY = e2.y - (e1?.y ?: 0f)

                if (abs(deltaX) > abs(deltaY)) {
                    // Horizontal scroll
                } else {
                    // Vertical scroll
                    if ((e1?.x ?: 0f) < playerView.width / 2) {
                        handleBrightness(deltaY)
                    } else {
                        handleVolume(deltaY)
                    }
                }
                return true
            }
        })

        playerView.setOnTouchListener { _, event ->
            val consumed = gestureDetector.onTouchEvent(event)
            if (event.action == MotionEvent.ACTION_UP) {
                hideBars()
            }
            consumed
        }
    }

    private fun handleBrightness(deltaY: Float) {
        brightnessBar.visibility = View.VISIBLE
        volumeBar.visibility = View.GONE

        val window = (context as android.app.Activity).window
        val layoutParams = window.attributes
        
        var newBrightness = layoutParams.screenBrightness
        if (newBrightness < 0) {
            newBrightness = try {
                Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
            } catch (e: Settings.SettingNotFoundException) {
                0.5f
            }
        }

        newBrightness -= deltaY / playerView.height
        if (newBrightness < 0f) newBrightness = 0f
        if (newBrightness > 1f) newBrightness = 1f

        layoutParams.screenBrightness = newBrightness
        window.attributes = layoutParams
        brightnessBar.progress = (newBrightness * 100).toInt()
    }

    private fun handleVolume(deltaY: Float) {
        volumeBar.visibility = View.VISIBLE
        brightnessBar.visibility = View.GONE

        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        var newVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()

        newVolume -= deltaY / playerView.height * maxVolume
        if (newVolume < 0) newVolume = 0f
        if (newVolume > maxVolume) newVolume = maxVolume.toFloat()

        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume.toInt(), 0)
        volumeBar.progress = (newVolume / maxVolume * 100).toInt()
    }

    private fun hideBars() {
        CoroutineScope(Dispatchers.Main).launch {
            delay(500)
            brightnessBar.visibility = View.GONE
            volumeBar.visibility = View.GONE
        }
    }
}
