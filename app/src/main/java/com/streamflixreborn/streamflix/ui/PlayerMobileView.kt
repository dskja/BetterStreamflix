package com.streamflixreborn.streamflix.ui

import android.content.Context
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.media3.ui.PlayerControlView
import androidx.media3.ui.PlayerView
import com.streamflixreborn.streamflix.R
import java.util.Locale
import kotlin.math.abs

class PlayerMobileView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : PlayerView(context, attrs, defStyle) {

    val controller: PlayerControlView
        get() = PlayerView::class.java.getDeclaredField("controller").let {
            it.isAccessible = true
            it.get(this) as PlayerControlView
        }

    var isManualZoomEnabled: Boolean = false
        private set

    private var zoomToast: Toast? = null

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (isManualZoomEnabled) {
                val videoView = videoSurfaceView ?: return false
                
                if (abs(distanceX) > abs(distanceY)) {
                    val factor = distanceX / width.toFloat()
                    videoView.scaleX -= factor 
                } else {
                    val factor = distanceY / height.toFloat()
                    videoView.scaleY += factor
                }

                videoView.scaleX = videoView.scaleX.coerceIn(0.25f, 5.0f)
                videoView.scaleY = videoView.scaleY.coerceIn(0.25f, 5.0f)

                showZoomToast("Zoom X: ${String.format(Locale.US, "%.2f", videoView.scaleX)} | Y: ${String.format(Locale.US, "%.2f", videoView.scaleY)}")
                return true
            }
            return false
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (isManualZoomEnabled) {
                val videoView = videoSurfaceView ?: return false
                videoView.scaleX = 1.0f
                videoView.scaleY = 1.0f
                showZoomToast("Zoom Reset: 1.00")
                return true
            }
            return false
        }
        
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (isManualZoomEnabled) {
                // Se l'utente tocca una volta mentre è in zoom manuale, usciamo dalla modalità
                // per permettere la ricomparsa dei controlli standard
                exitManualZoomMode()
                return true
            }
            return false
        }
    })

    fun enterManualZoomMode() {
        player?.pause()
        isManualZoomEnabled = true
        disableAllClipping(this)
        showZoomToast(context.getString(R.string.player_manual_zoom_hint), Toast.LENGTH_LONG)
    }

    fun exitManualZoomMode() {
        if (!isManualZoomEnabled) return
        isManualZoomEnabled = false
        zoomToast?.cancel()
        player?.play()
    }

    private fun disableAllClipping(view: View) {
        var current: View? = view
        while (current != null) {
            (current as? ViewGroup)?.let {
                it.clipChildren = false
                it.clipToPadding = false
            }
            val parent = current.parent
            current = if (parent is View) parent else null
        }
    }

    private fun showZoomToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        zoomToast?.cancel()
        zoomToast = Toast.makeText(context, message, duration)
        zoomToast?.show()
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isManualZoomEnabled) {
            gestureDetector.onTouchEvent(event)
            if (event.action == MotionEvent.ACTION_DOWN) {
                performClick()
            }
            return true
        }
        return super.onTouchEvent(event)
    }
}
