package com.streamflixreborn.streamflix.ui

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import android.widget.Toast
import androidx.media3.common.Player
import androidx.media3.ui.PlayerControlView
import androidx.media3.ui.PlayerView
import com.streamflixreborn.streamflix.R
import java.util.Locale

class PlayerTvView @JvmOverloads constructor(
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

    fun enterManualZoomMode() {
        player?.pause()
        isManualZoomEnabled = true
        showZoomToast(context.getString(R.string.player_manual_zoom_hint), Toast.LENGTH_LONG)
    }

    fun exitManualZoomMode() {
        isManualZoomEnabled = false
        videoSurfaceView?.apply {
            scaleX = 1f
            scaleY = 1f
        }
        zoomToast?.cancel()
        player?.play()
    }

    private fun showZoomToast(message: String, duration: Int = Toast.LENGTH_SHORT) {
        zoomToast?.cancel()
        zoomToast = Toast.makeText(context, message, duration)
        zoomToast?.show()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (isManualZoomEnabled) {
            if (event.action == KeyEvent.ACTION_DOWN) {
                // RIPRISTINATA LOGICA ORIGINALE: Scaliamo solo il videoSurfaceView.
                // Grazie all'uso di texture_view nel layout, ora le modifiche sono visibili in tempo reale anche in pausa.
                val videoView = videoSurfaceView ?: return true
                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        videoView.scaleY += 0.01f
                        showZoomToast("Zoom Y: ${String.format(Locale.US, "%.2f", videoView.scaleY)}")
                    }
                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        videoView.scaleY -= 0.01f
                        showZoomToast("Zoom Y: ${String.format(Locale.US, "%.2f", videoView.scaleY)}")
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        videoView.scaleX -= 0.01f
                        showZoomToast("Zoom X: ${String.format(Locale.US, "%.2f", videoView.scaleX)}")
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        videoView.scaleX += 0.01f
                        showZoomToast("Zoom X: ${String.format(Locale.US, "%.2f", videoView.scaleX)}")
                    }
                    KeyEvent.KEYCODE_BACK -> {
                        exitManualZoomMode()
                    }
                }
            }
            return true
        }

        val player = player ?: return super.dispatchKeyEvent(event)

        if (player.isCommandAvailable(Player.COMMAND_GET_CURRENT_MEDIA_ITEM) && player.isPlayingAd) {
            return super.dispatchKeyEvent(event)
        }

        if (controller.isVisible) return super.dispatchKeyEvent(event)

        return when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    player.seekTo(player.currentPosition - 10_000)
                }
                true
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    player.seekTo(player.currentPosition + 10_000)
                }
                true
            }

            else -> super.dispatchKeyEvent(event)
        }
    }
}
