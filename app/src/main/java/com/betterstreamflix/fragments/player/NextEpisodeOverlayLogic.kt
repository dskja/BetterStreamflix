package com.betterstreamflix.fragments.player

/**
 * Shared timing/threshold logic for the next-episode overlay on mobile and TV.
 * Keeps UI bindings platform-specific while aligning when to prefetch/show.
 */
object NextEpisodeOverlayLogic {
    const val PREFETCH_THRESHOLD_MS = 60_000L
    const val OVERLAY_MIN_THRESHOLD_MS = 30_000L

    fun overlayThresholdMs(autoplayBufferSeconds: Long): Long =
        maxOf(OVERLAY_MIN_THRESHOLD_MS, autoplayBufferSeconds * 1000L)

    /** Prefetch when remaining time is at or below the prefetch window. */
    fun shouldPrefetchNext(remainingMs: Long): Boolean =
        remainingMs <= PREFETCH_THRESHOLD_MS

    /**
     * Show overlay when a next episode is known, playback isn't finished,
     * and remaining time is within the overlay threshold.
     */
    fun shouldShowOverlay(
        hasNextEpisode: Boolean,
        remainingMs: Long,
        autoplayBufferSeconds: Long,
        dismissed: Boolean,
    ): Boolean {
        if (dismissed || !hasNextEpisode) return false
        if (remainingMs <= 0L) return false
        return remainingMs <= overlayThresholdMs(autoplayBufferSeconds)
    }

    fun countdownSeconds(remainingMs: Long): Int =
        ((remainingMs + 999L) / 1000L).toInt().coerceAtLeast(0)
}
