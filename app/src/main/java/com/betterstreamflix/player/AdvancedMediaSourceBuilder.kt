package com.betterstreamflix.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaItem.SubtitleConfiguration
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import androidx.media3.exoplayer.upstream.DefaultDataSource
import androidx.media3.exoplayer.upstream.DefaultHttpDataSource

/**
 * Advanced media source builder with support for:
 * - DASH, HLS, Progressive, and SmoothStreaming
 * - Multiple subtitle tracks
 * - Custom headers and cookies
 * - Adaptive quality
 */
object AdvancedMediaSourceBuilder {

    /**
     * Build a media source for a video URL with optional subtitles.
     */
    fun build(
        context: Context,
        videoUrl: String,
        subtitles: List<SubtitleConfig> = emptyList(),
        headers: Map<String, String> = emptyMap(),
        cookies: List<String> = emptyList(),
    ): androidx.media3.exoplayer.source.MediaSource {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(com.betterstreamflix.utils.Constants.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .apply {
                if (headers.isNotEmpty()) setDefaultRequestProperties(headers)
                if (cookies.isNotEmpty()) {
                    val cookieHeader = "Cookie: ${cookies.joinToString("; ")}"
                    setDefaultRequestProperties(mapOf("Cookie" to cookies.joinToString("; ")))
                }
            }

        val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)

        val mediaItem = MediaItem.Builder()
            .setUri(videoUrl)
            .apply {
                if (subtitles.isNotEmpty()) {
                    setSubtitleConfigurations(
                        subtitles.map { sub ->
                            SubtitleConfiguration.Builder(Uri.parse(sub.url))
                                .setMimeType(sub.mimeType)
                                .setLanguage(sub.language)
                                .setLabel(sub.label)
                                .build()
                        }
                    )
                }
            }
            .build()

        // Use the appropriate media source factory based on URL scheme
        return when {
            videoUrl.endsWith(".mpd") -> {
                androidx.media3.exoplayer.dash.DashMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(mediaItem)
            }
            videoUrl.endsWith(".m3u8") || videoUrl.contains("m3u8") -> {
                androidx.media3.exoplayer.hls.HlsMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(mediaItem)
            }
            else -> {
                ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(mediaItem)
            }
        }
    }
}

/**
 * Subtitle configuration for media sources.
 */
data class SubtitleConfig(
    val url: String,
    val language: String,
    val label: String = language,
    val mimeType: String = "text/vtt",
)
