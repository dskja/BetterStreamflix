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
    )

    fun build(
        context: Context,
        dataSourceFactory: DataSource.Factory,
        options: Options,
    ): ExoPlayer {
        SubtitleOffset.reset()
        val constrained = DeviceCapabilities.shouldUseConstrainedPlayback(context)
        val maxBufferMs = when {
            options.extraBuffering && constrained -> 90_000
            options.extraBuffering -> 300_000
            else -> DefaultLoadControl.DEFAULT_MAX_BUFFER_MS
        }
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                maxBufferMs,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
            )
            .build()

        val renderersFactory = SubtitleOffsetRenderersFactory(context).apply {
            // Always enable decoder fallback on modern devices and constrained TVs.
            // Silent hardware decoder failures on Xiaomi/Fire OS often crash to launcher.
            if (
                Build.VERSION.SDK_INT > Build.VERSION_CODES.N_MR1 ||
                options.softwareDecoder ||
                constrained
            ) {
                setEnableDecoderFallback(true)
            }
            if (options.softwareDecoder) {
                setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
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
                .setContentType(C.AUDIO_CONTENT_TYPE_UNKNOWN)
                .build(),
            /* handleAudioFocus= */ false,
        )

        var params = player.trackSelectionParameters.buildUpon()
        val lang = UserPreferences.currentProvider?.language?.substringBefore("-")
        ProviderAudioLanguage.preferredAudioLanguages(lang)?.let { codes ->
            params = params.setPreferredAudioLanguages(*codes)
        }
        if (options.preferStereoAudio || constrained) {
            params = params.setMaxAudioChannelCount(2)
        }
        player.trackSelectionParameters = params.build()
        return player
    }
}
