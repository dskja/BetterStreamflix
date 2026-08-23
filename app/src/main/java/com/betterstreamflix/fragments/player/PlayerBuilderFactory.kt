package com.betterstreamflix.fragments.player

import android.content.Context
import android.os.Build
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import com.betterstreamflix.utils.SubtitleOffsetRenderersFactory
import com.betterstreamflix.utils.UserPreferences

/**
 * Factory for creating and configuring ExoPlayer instances.
 * Extracted from PlayerMobileFragment/PlayerTvFragment to share logic.
 */
object PlayerBuilderFactory {

    /**
     * Build an ExoPlayer with the given configuration.
     */
    fun buildPlayer(
        context: Context,
        dataSourceFactory: DataSource.Factory,
        extraBuffering: Boolean,
        softwareDecoder: Boolean,
    ): ExoPlayer {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                if (extraBuffering) 300_000 else DefaultLoadControl.DEFAULT_MAX_BUFFER_MS,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_MS,
                DefaultLoadControl.DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
            )
            .build()

        val renderersFactory = SubtitleOffsetRenderersFactory(context).apply {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.N_MR1 || softwareDecoder) {
                setEnableDecoderFallback(true)
                if (softwareDecoder) {
                    setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
                }
            }
        }

        return ExoPlayer.Builder(context, renderersFactory)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .setLoadControl(loadControl)
            .build()
    }

    /**
     * Apply common player settings: audio attributes, language preference, quality.
     */
    fun applyPlayerSettings(player: ExoPlayer) {
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            true,
        )

        val lang = UserPreferences.currentProvider?.language?.substringBefore("-")
        val trackParamsBuilder = player.trackSelectionParameters.buildUpon()
        if (lang == "es") {
            trackParamsBuilder.setPreferredAudioLanguage("spa")
        }
        UserPreferences.qualityHeight?.let { savedHeight ->
            trackParamsBuilder.setMaxVideoSize(Int.MAX_VALUE, savedHeight)
        }
        player.trackSelectionParameters = trackParamsBuilder.build()
    }

    /**
     * Create a MediaSession for the player.
     */
    fun createMediaSession(context: Context, player: ExoPlayer): MediaSession {
        return MediaSession.Builder(context, player).build()
    }
}
