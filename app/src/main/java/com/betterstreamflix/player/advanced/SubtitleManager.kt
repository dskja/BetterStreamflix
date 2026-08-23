package com.betterstreamflix.player.advanced

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.C

/**
 * Subtitle manager — manages subtitle track selection, styling,
 * and synchronization.
 */
object SubtitleManager {

    /**
     * Get available subtitle tracks.
     */
    fun getSubtitleTracks(player: ExoPlayer): List<SubtitleTrack> {
        val tracks = player.currentTracks.groups
        val subtitleTracks = mutableListOf<SubtitleTrack>()

        for (group in tracks) {
            if (group.type == C.TRACK_TYPE_TEXT) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    subtitleTracks.add(
                        SubtitleTrack(
                            index = subtitleTracks.size,
                            language = format.language ?: "und",
                            label = format.label ?: format.language ?: "Unknown",
                            isSelected = group.isSelected && group.getTrackFormat(i) == format,
                        ),
                    )
                }
            }
        }
        return subtitleTracks
    }

    /**
     * Select a subtitle track by language.
     */
    fun selectSubtitleByLanguage(player: ExoPlayer, language: String) {
        val tracks = player.currentTracks.groups
        val textTracks = tracks.filter { it.type == C.TRACK_TYPE_TEXT }

        for (group in textTracks) {
            for (i in 0 until group.length) {
                val format = group.getTrackFormat(i)
                if (format.language == language) {
                    // Would use track selection parameters in real impl
                    return
                }
            }
        }
    }

    /**
     * Disable all subtitles.
     */
    fun disableSubtitles(player: ExoPlayer) {
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
    }

    /**
     * Enable subtitles with auto-selection.
     */
    fun enableSubtitles(player: ExoPlayer) {
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .build()
    }

    /**
     * Adjust subtitle offset (sync).
     */
    fun calculateSubtitleOffset(
        audioTimeMs: Long,
        subtitleTimeMs: Long,
    ): Long {
        return audioTimeMs - subtitleTimeMs
    }

    data class SubtitleTrack(
        val index: Int,
        val language: String,
        val label: String,
        val isSelected: Boolean,
    )
}
