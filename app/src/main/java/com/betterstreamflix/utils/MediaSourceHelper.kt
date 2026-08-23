package com.betterstreamflix.utils

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaItem.SubtitleConfiguration
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import androidx.media3.datasource.DefaultDataSource

/**
 * Helper for building ExoPlayer media sources with subtitle support.
 */
object MediaSourceHelper {

    /**
     * Build a media source with optional subtitle tracks.
     */
    fun buildMediaSourceWithSubtitles(
        context: Context,
        videoUrl: String,
        subtitleUrls: List<SubtitleTrack> = emptyList(),
    ): MediaSource {
        val dataSourceFactory = DefaultDataSource.Factory(context)

        val videoSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(videoUrl))

        if (subtitleUrls.isEmpty()) return videoSource

        val subtitleSources = subtitleUrls.map { track ->
            val subtitleConfig = SubtitleConfiguration.Builder(Uri.parse(track.url))
                .setMimeType(track.mimeType)
                .setLanguage(track.language)
                .setSelectionFlags(track.selectionFlags)
                .build()

            SingleSampleMediaSource.Factory(dataSourceFactory)
                .createMediaSource(subtitleConfig, 0L)
        }

        return MergingMediaSource(videoSource, *subtitleSources.toTypedArray())
    }
}

/**
 * A subtitle track configuration.
 */
data class SubtitleTrack(
    val url: String,
    val language: String,
    val mimeType: String = "text/vtt",
    val selectionFlags: Int = 0,
)
