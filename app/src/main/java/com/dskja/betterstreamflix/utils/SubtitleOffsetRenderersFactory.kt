package com.dskja.betterstreamflix.utils

import android.content.Context
import android.os.Looper
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ForwardingRenderer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.text.TextRenderer

/** Runtime subtitle timing shared by the player settings and text renderer. */
object SubtitleOffset {
    @Volatile
    var offsetMs: Long = 0

    @Volatile
    private var videoKey: String? = null

    fun reset() {
        offsetMs = 0
        videoKey = null
    }

    /** Loads the previously saved offset for [key] (per-video persistence). */
    fun restore(context: Context, key: String) {
        videoKey = key
        offsetMs = prefs(context).getLong(key, 0L)
    }

    /** Saves the current offset under the key set by [restore]. */
    fun persist(context: Context) {
        val key = videoKey ?: return
        prefs(context).edit().putLong(key, offsetMs).apply()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("subtitle_offsets", Context.MODE_PRIVATE)
}

/**
 * Builds the normal Media3 renderers, but presents subtitle cues against an adjusted clock.
 * A positive offset delays captions and a negative offset advances them.
 *
 * Only [render] is shifted. [enable]/[resetPosition] must keep the real media timeline so
 * ExoPlayer's sample reading stays aligned with audio/video (otherwise seeks and 0.0s feel late).
 */
@UnstableApi
class SubtitleOffsetRenderersFactory(context: Context) : DefaultRenderersFactory(context) {
    override fun buildTextRenderers(
        context: Context,
        output: TextOutput,
        outputLooper: Looper,
        extensionRendererMode: Int,
        out: ArrayList<Renderer>,
    ) {
        val textRenderer = TextRenderer(output, outputLooper)
        out.add(object : ForwardingRenderer(textRenderer) {
            private fun adjustedPositionUs(positionUs: Long): Long {
                val offsetUs = SubtitleOffset.offsetMs * 1_000L
                if (offsetUs == 0L) return positionUs
                return positionUs - offsetUs
            }

            override fun render(positionUs: Long, elapsedRealtimeUs: Long) {
                super.render(adjustedPositionUs(positionUs), elapsedRealtimeUs)
            }
        })
    }
}
