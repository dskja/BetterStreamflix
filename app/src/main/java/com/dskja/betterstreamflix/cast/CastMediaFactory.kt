package com.dskja.betterstreamflix.cast

import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import com.dskja.betterstreamflix.models.Video
import com.dskja.betterstreamflix.utils.UserPreferences
import com.dskja.betterstreamflix.utils.setMediaServerId

/** Builds Cast-ready [MediaItem]s with title, artwork, queue metadata, and header-aware proxied URIs. */
object CastMediaFactory {

    fun posterUri(videoType: Video.Type): Uri? {
        val raw = when (videoType) {
            is Video.Type.Movie -> videoType.poster.takeIf { it.isNotBlank() }
            is Video.Type.Episode -> {
                videoType.poster?.takeIf { it.isNotBlank() }
                    ?: videoType.tvShow.poster?.takeIf { it.isNotBlank() }
                    ?: videoType.tvShow.banner?.takeIf { it.isNotBlank() }
            }
        } ?: return null
        return runCatching { raw.toUri() }.getOrNull()
    }

    fun buildMetadata(
        title: String,
        subtitle: String,
        videoType: Video.Type,
        serverId: String?,
    ): MediaMetadata {
        val builder = MediaMetadata.Builder()
            .setTitle(title)
            .setSubtitle(subtitle.takeIf { it.isNotBlank() })
            .setDisplayTitle(title)
            .setArtist(subtitle.takeIf { it.isNotBlank() })
            .setMediaType(
                when (videoType) {
                    is Video.Type.Movie -> MediaMetadata.MEDIA_TYPE_MOVIE
                    is Video.Type.Episode -> MediaMetadata.MEDIA_TYPE_TV_SHOW
                }
            )
        when (videoType) {
            is Video.Type.Episode -> {
                builder.setAlbumTitle(videoType.tvShow.title.takeIf { it.isNotBlank() })
                builder.setTrackNumber(videoType.season.number)
                builder.setDiscNumber(videoType.number)
            }
            is Video.Type.Movie -> Unit
        }
        posterUri(videoType)?.let { builder.setArtworkUri(it) }
        if (!serverId.isNullOrBlank()) {
            builder.setMediaServerId(serverId)
        }
        return builder.build()
    }

    fun resolveCastUri(
        source: String,
        headers: Map<String, String>,
        extractedFallback: String? = null,
    ): String {
        val candidate = when {
            source.startsWith("data:", ignoreCase = true) -> extractedFallback ?: source
            else -> source
        }
        if (candidate.startsWith("data:", ignoreCase = true)) {
            return candidate
        }
        return CastPlaybackHub.wrapForCast(candidate, headers)
    }

    fun mediaItemForCast(
        source: String,
        mimeType: String?,
        headers: Map<String, String>,
        metadata: MediaMetadata,
        subtitleConfigurations: List<MediaItem.SubtitleConfiguration>,
        extractedFallback: String? = null,
        live: Boolean = false,
    ): MediaItem {
        val castUri = resolveCastUri(source, headers, extractedFallback)
        val subs = if (UserPreferences.castSubtitlesEnabled) {
            subtitleConfigurations
        } else {
            emptyList()
        }
        val builder = MediaItem.Builder()
            .setUri(castUri.toUri())
            .setMimeType(mimeType ?: guessMime(castUri))
            .setMediaMetadata(metadata)
            .setSubtitleConfigurations(subs)
        if (live) {
            builder.setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setMaxPlaybackSpeed(1.02f)
                    .build()
            )
        }
        return builder.build()
    }

    fun queueItemForCast(
        source: String,
        mimeType: String?,
        headers: Map<String, String>,
        metadata: MediaMetadata,
        subtitleConfigurations: List<MediaItem.SubtitleConfiguration> = emptyList(),
        extractedFallback: String? = null,
    ): MediaItem = mediaItemForCast(
        source = source,
        mimeType = mimeType,
        headers = headers,
        metadata = metadata,
        subtitleConfigurations = subtitleConfigurations,
        extractedFallback = extractedFallback,
        live = false,
    )

    private fun guessMime(url: String): String {
        val path = url.substringBefore('?').lowercase()
        return when {
            path.endsWith(".m3u8") -> MimeTypes.APPLICATION_M3U8
            path.endsWith(".mpd") -> MimeTypes.APPLICATION_MPD
            path.endsWith(".mp4") -> MimeTypes.VIDEO_MP4
            path.endsWith(".mkv") -> MimeTypes.VIDEO_MATROSKA
            else -> MimeTypes.APPLICATION_M3U8
        }
    }
}
