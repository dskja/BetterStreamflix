package com.betterstreamflix.player.advanced

import androidx.media3.exoplayer.ExoPlayer

/**
 * Skip intro/outro manager — detects and allows skipping of
 * intro and outro segments.
 */
object SkipSegmentManager {

    private var introStartMs: Long = 0
    private var introEndMs: Long = 0
    private var outroStartMs: Long = 0
    private var outroEndMs: Long = 0

    /**
     * Set intro segment boundaries.
     */
    fun setIntroSegment(startMs: Long, endMs: Long) {
        introStartMs = startMs
        introEndMs = endMs
    }

    /**
     * Set outro segment boundaries.
     */
    fun setOutroSegment(startMs: Long, endMs: Long) {
        outroStartMs = startMs
        outroEndMs = endMs
    }

    /**
     * Check if currently in intro segment.
     */
    fun isInIntro(currentPositionMs: Long): Boolean {
        return currentPositionMs in introStartMs..introEndMs
    }

    /**
     * Check if currently in outro segment.
     */
    fun isInOutro(currentPositionMs: Long): Boolean {
        return currentPositionMs in outroStartMs..outroEndMs
    }

    /**
     * Get skip target if in a skippable segment.
     */
    fun getSkipTarget(currentPositionMs: Long): Long? {
        return when {
            isInIntro(currentPositionMs) -> introEndMs
            isInOutro(currentPositionMs) -> outroEndMs
            else -> null
        }
    }

    /**
     * Auto-skip intro if enabled.
     */
    fun autoSkipIntro(player: ExoPlayer, enabled: Boolean): Boolean {
        if (!enabled) return false
        val skipTarget = getSkipTarget(player.currentPosition)
        if (skipTarget != null && isInIntro(player.currentPosition)) {
            player.seekTo(skipTarget)
            return true
        }
        return false
    }

    /**
     * Clear all segments.
     */
    fun clear() {
        introStartMs = 0
        introEndMs = 0
        outroStartMs = 0
        outroEndMs = 0
    }
}
