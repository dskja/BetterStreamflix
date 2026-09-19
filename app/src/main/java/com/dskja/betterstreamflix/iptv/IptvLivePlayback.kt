package com.dskja.betterstreamflix.iptv

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/**
 * Shared live-playback helpers for IPTV (Media3 live window + go-to-live).
 */
object IptvLivePlayback {

    /** Target distance behind the live edge — stable on most HLS IPTV feeds. */
    const val TARGET_OFFSET_MS = 3_500L
    const val MIN_OFFSET_MS = 1_500L
    const val MAX_OFFSET_MS = 12_000L

    fun liveConfiguration(): MediaItem.LiveConfiguration =
        MediaItem.LiveConfiguration.Builder()
            .setTargetOffsetMs(TARGET_OFFSET_MS)
            .setMinOffsetMs(MIN_OFFSET_MS)
            .setMaxOffsetMs(MAX_OFFSET_MS)
            .setMaxPlaybackSpeed(1.04f)
            .build()

    /**
     * True when the playhead is meaningfully behind the live edge
     * (user paused / scrubbed into the DVR window).
     */
    @OptIn(UnstableApi::class)
    fun isBehindLiveEdge(player: Player, thresholdMs: Long = 2_500L): Boolean {
        if (!player.isCurrentMediaItemLive) return false
        val liveOffset = player.currentLiveOffset
        if (liveOffset == androidx.media3.common.C.TIME_UNSET) return false
        return liveOffset > TARGET_OFFSET_MS + thresholdMs
    }

    /** Jump to the configured live edge (or default live position). */
    @OptIn(UnstableApi::class)
    fun seekToLiveEdge(player: Player) {
        if (!player.isCurrentMediaItemLive) return
        val liveConfig = player.currentMediaItem?.liveConfiguration
        val target = liveConfig?.targetOffsetMs?.takeIf { it > 0 } ?: TARGET_OFFSET_MS
        val duration = player.duration
        if (duration != androidx.media3.common.C.TIME_UNSET && duration > 0) {
            val seekPos = (duration - target).coerceAtLeast(0L)
            player.seekTo(seekPos)
        } else {
            player.seekToDefaultPosition()
        }
        if (!player.isPlaying) player.play()
    }
}
