package com.streamflixreborn.streamflix.utils

import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.view.GestureDetector
import android.view.InputDevice
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.media3.ui.PlayerView
import com.streamflixreborn.streamflix.ui.PlayerMobileView
import com.streamflixreborn.streamflix.ui.PlayerTvView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

class PlayerGestureHelper(
    private val context: Context, 
    private val playerView: PlayerView,
    private val brightnessLayout: View,
    private val brightnessBar: ProgressBar,
    private val brightnessText: TextView,
    private val volumeLayout: View,
    private val volumeBar: ProgressBar,
    private val volumeText: TextView
) {

    private val gestureDetector: GestureDetector
    private val scaleGestureDetector: ScaleGestureDetector
    private val audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var hideJob: Job? = null
    
    private val sensitivity = 1.2f
    private var isScrolling = false
    private var isScaling = false
    private var currentVolumeFloat = 0f
    private var maxVolume = 0

    init {
        maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

        // Rilevatore per il Pinch-to-Zoom (Dita)
        scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val videoView = playerView.videoSurfaceView ?: return false
                isScaling = true
                
                // Applichiamo lo zoom in tempo reale (come per la TV)
                videoView.scaleX *= detector.scaleFactor
                videoView.scaleY *= detector.scaleFactor
                
                // Limiti minimi e massimi per evitare di perdere il video
                if (videoView.scaleX < 0.25f) videoView.scaleX = 0.25f
                if (videoView.scaleY < 0.25f) videoView.scaleY = 0.25f
                if (videoView.scaleX > 4.0f) videoView.scaleX = 4.0f
                if (videoView.scaleY > 4.0f) videoView.scaleY = 4.0f
                
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                isScaling = false
            }
        })

        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                isScrolling = false
                currentVolumeFloat = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
                return true 
            }

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                if (e1 == null || isScaling) return false
                if (e1.y < 100) return false

                if (!isScrolling) {
                    val deltaX = e2.x - e1.x
                    val deltaY = e2.y - e1.y
                    if (abs(deltaY) > abs(deltaX) && abs(deltaY) > 10) {
                        isScrolling = true
                    } else {
                        return false
                    }
                }

                if (e1.x < playerView.width / 2) {
                    handleBrightness(distanceY / playerView.height)
                } else {
                    handleVolume(distanceY / playerView.height)
                }
                
                return true
            }
            
            override fun onDoubleTap(e: MotionEvent): Boolean {
                val videoView = playerView.videoSurfaceView ?: return false
                videoView.scaleX = 1.0f
                videoView.scaleY = 1.0f
                return true
            }
        })

        playerView.setOnTouchListener { _, event ->
            // Abilitiamo le gesture solo se l'input proviene da un dispositivo di puntamento (Touch, Mouse, AirMouse)
            val isPointing = (event.source and InputDevice.SOURCE_CLASS_POINTER) != 0
            if (!isPointing) return@setOnTouchListener false

            // Controllo Zoom Manuale universale (sia per Mobile che per TV)
            val isManualZoom = when (playerView) {
                is PlayerMobileView -> playerView.isManualZoomEnabled
                is PlayerTvView -> playerView.isManualZoomEnabled
                else -> false
            }
            if (isManualZoom) return@setOnTouchListener false

            if (!UserPreferences.playerGestures) return@setOnTouchListener false
            
            scaleGestureDetector.onTouchEvent(event)
            
            if (!isScaling) {
                gestureDetector.onTouchEvent(event)
            }
            
            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                isScrolling = false
                isScaling = false
                hideBars()
            }
            
            isScrolling || isScaling
        }
    }

    private fun handleBrightness(delta: Float) {
        hideJob?.cancel()
        brightnessLayout.visibility = View.VISIBLE
        volumeLayout.visibility = View.GONE

        val window = (context as? android.app.Activity)?.window ?: return
        val layoutParams = window.attributes
        
        var currentBrightness = layoutParams.screenBrightness
        if (currentBrightness < 0) {
            currentBrightness = try {
                Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
            } catch (e: Settings.SettingNotFoundException) {
                0.5f
            }
        }

        var newBrightness = currentBrightness + (delta / sensitivity)
        if (newBrightness < 0f) newBrightness = 0f
        if (newBrightness > 1f) newBrightness = 1f

        layoutParams.screenBrightness = newBrightness
        window.attributes = layoutParams
        
        val progress = (newBrightness * 100).toInt()
        brightnessBar.progress = progress
        brightnessText.text = "$progress%"
    }

    private fun handleVolume(delta: Float) {
        hideJob?.cancel()
        volumeLayout.visibility = View.VISIBLE
        brightnessLayout.visibility = View.GONE

        val volumeChange = (delta / sensitivity) * maxVolume
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
