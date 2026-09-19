package com.dskja.betterstreamflix.player

import android.content.Context
import android.os.Build
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import com.dskja.betterstreamflix.utils.DeviceCapabilities
import com.dskja.betterstreamflix.utils.ProviderAudioLanguage
import com.dskja.betterstreamflix.utils.SubtitleOffset
import com.dskja.betterstreamflix.utils.SubtitleOffsetRenderersFactory
import com.dskja.betterstreamflix.utils.UserPreferences

/**
 * Shared ExoPlayer construction for mobile + TV so decoder/buffering fixes land once.
 * Addresses Android TV / OEM crashes (e.g. Xiaomi TV) via always-on decoder fallback
 * and constrained buffers on Fire Stick / low-RAM devices.
 */
object PlayerBuilderFactory {

    data class Options(
        val extraBuffering: Boolean = false,
        val softwareDecoder: Boolean = false,
        val seekIncrementsMs: Long? = 10_000L,
        val preferStereoAudio: Boolean = false,
        /** Shorter buffers + quicker start for IPTV / live HLS. */
        val liveOptimized: Boolean = false,
    )

    fun build(
        context: Context,
        dataSourceFactory: DataSource.Factory,
        options: Options,
    ): ExoPlayer {
        SubtitleOffset.reset()
        val constrained = DeviceCapabilities.shouldUseConstrainedPlayback(context)
        val minBufferMs: Int
        val maxBufferMs: Int
        val playbackMs: Int
        val rebufferMs: Int
        when {
            options.liveOptimized && constrained -> {
                minBufferMs = 2_000
                maxBufferMs = 15_000
                playbackMs = 1_000
                rebufferMs = 2_000
            }
            options.liveOptimized -> {
                minBufferMs = 3_000
                maxBufferMs = 25_000
                playbackMs = 1_250
                rebufferMs = 2_500
            }
            options.extraBuffering && constrained -> {
                minBufferMs = DefaultLoadControl.DEFAULT_MIN_BUFFER_MS
                maxBufferMs = 90_000
                playbackMs = DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS
                rebufferMs = DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
            }
            options.extraBuffering -> {
                minBufferMs = DefaultLoadControl.DEFAULT_MIN_BUFFER_MS
                maxBufferMs = 300_000
                playbackMs = DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS
                rebufferMs = DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
            }
            else -> {
                minBufferMs = DefaultLoadControl.DEFAULT_MIN_BUFFER_MS
                maxBufferMs = DefaultLoadControl.DEFAULT_MAX_BUFFER_MS
                playbackMs = DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS
                rebufferMs = DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
            }
        }
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(minBufferMs, maxBufferMs, playbackMs, rebufferMs)
            .build()

        val renderersFactory = SubtitleOffsetRenderersFactory(context).apply {
            val preferSoftware =
                options.softwareDecoder || DeviceCapabilities.shouldPreferSoftwareDecoder(context)
            // Always enable decoder fallback on modern devices and constrained TVs.
            // Silent hardware decoder failures on Xiaomi/Fire OS often crash to launcher.
            if (
                Build.VERSION.SDK_INT > Build.VERSION_CODES.N_MR1 ||
                preferSoftware ||
                constrained
            ) {
                setEnableDecoderFallback(true)
            }
            if (preferSoftware) {
                // ON (not only PREFER) so TV boxes that hard-crash in HW codecs get FFmpeg first.
                setExtensionRendererMode(
                    if (DeviceCapabilities.isLeanbackDevice(context) || options.softwareDecoder) {
                        DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                    } else {
                        DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                    },
                )
            }
        }

        val mediaSourceFactory: MediaSource.Factory = DefaultMediaSourceFactory(dataSourceFactory)
        var builder = ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
        options.seekIncrementsMs?.let { seek ->
            builder = builder
                .setSeekBackIncrementMs(seek)
                .setSeekForwardIncrementMs(seek)
        }
        val player = builder.build()

        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            /* handleAudioFocus= */ true,
        )

        var params = player.trackSelectionParameters.buildUpon()
        val lang = UserPreferences.currentProvider?.language?.substringBefore("-")
        ProviderAudioLanguage.preferredAudioLanguages(lang)?.let { codes ->
            params = params.setPreferredAudioLanguages(*codes)
            params = params.setPreferredTextLanguages(*codes)
        }
        if (options.preferStereoAudio || constrained) {
            params = params.setMaxAudioChannelCount(2)
        }
        player.trackSelectionParameters = params.build()
        return player
    }
}
