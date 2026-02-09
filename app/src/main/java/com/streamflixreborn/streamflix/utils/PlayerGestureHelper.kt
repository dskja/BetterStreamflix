package com.streamflixreborn.streamflix.utils

import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

class PlayerGestureHelper(
    private val context: Context, 
    private val playerView: View,
    private val brightnessLayout: View,
    private val brightnessBar: ProgressBar,
    private val brightnessText: TextView,
    private val volumeLayout: View,
    private val volumeBar: ProgressBar,
    private val volumeText: TextView
) {

    private val gestureDetector: GestureDetector
    private val audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var hideJob: Job? = null
    
    // Sensitivity factor: higher means less sensitive (requires more movement)
    private val sensitivity = 1.2f

    // Internal state to track values during a single gesture to avoid rounding errors
    private var isScrolling = false
    private var currentVolumeFloat = 0f
    private var maxVolume = 0

    init {
        maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                isScrolling = false
                currentVolumeFloat = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                return true 
            }

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                if (e1 == null) return false
                
                // Avoid triggers near the top edge to prevent interference with notification shade
                if (e1.y < 100) return false

                val deltaX = e2.x - e1.x
                val deltaY = e2.y - e1.y

                if (!isScrolling) {
                    // Check if the scroll is primarily vertical and significant
                    if (abs(deltaY) > abs(deltaX) && abs(deltaY) > 10) {
                        isScrolling = true
                    } else {
                        return false
                    }
                }

                // Vertical scroll
                if (e1.x < playerView.width / 2) {
                    handleBrightness(distanceY)
                } else {
                    handleVolume(distanceY)
                }
                
                return true
            }
        })

        playerView.setOnTouchListener { _, event ->
            if (!UserPreferences.playerGestures) return@setOnTouchListener false
            
            gestureDetector.onTouchEvent(event)
            
            val result = isScrolling
            
            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                isScrolling = false
                hideBars()
            }
            
            // If we are scrolling, we consume the event. 
            // If not (e.g., it's a tap), we return false so PlayerView can show controls.
            result
        }
    }

    private fun handleBrightness(distanceY: Float) {
        hideJob?.cancel()
        brightnessLayout.visibility = View.VISIBLE
        volumeLayout.visibility = View.GONE

        val window = (context as android.app.Activity).window
        val layoutParams = window.attributes
        
        var currentBrightness = layoutParams.screenBrightness
        if (currentBrightness < 0) {
            currentBrightness = try {
                Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
            } catch (e: Settings.SettingNotFoundException) {
                0.5f
            }
        }

        // distanceY is positive when scrolling up
        var newBrightness = currentBrightness + (distanceY / (playerView.height * sensitivity))
        if (newBrightness < 0f) newBrightness = 0f
        if (newBrightness > 1f) newBrightness = 1f

        layoutParams.screenBrightness = newBrightness
        window.attributes = layoutParams
        
        val progress = (newBrightness * 100).toInt()
        brightnessBar.progress = progress
        brightnessText.text = "$progress%"
    }

    private fun handleVolume(distanceY: Float) {
        hideJob?.cancel()
        volumeLayout.visibility = View.VISIBLE
        brightnessLayout.visibility = View.GONE

        // distanceY is positive when scrolling up
        val volumeChange = (distanceY / (playerView.height * sensitivity)) * maxVolume
        currentVolumeFloat += volumeChange
        
        if (currentVolumeFloat < 0f) currentVolumeFloat = 0f
        if (currentVolumeFloat > maxVolume.toFloat()) currentVolumeFloat = maxVolume.toFloat()

        val newVolume = currentVolumeFloat.toInt()
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
        
        val progress = (currentVolumeFloat / maxVolume * 100).toInt()
        volumeBar.progress = progress
        volumeText.text = "$progress%"
    }

    private fun hideBars() {
        hideJob?.cancel()
        hideJob = CoroutineScope(Dispatchers.Main).launch {
            delay(1000)
            brightnessLayout.visibility = View.GONE
            volumeLayout.visibility = View.GONE
        }
    }
}
