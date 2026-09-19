package com.dskja.betterstreamflix.platform.simkl

/**
 * Pure progress → scrobble action mapping (mirrors Trakt; ≥80% = watched).
 */
object SimklScrobbler {
    enum class Action { START, PAUSE, STOP, NONE }

    fun decide(
        previousProgress: Double,
        currentProgress: Double,
        isPlaying: Boolean,
        alreadyStarted: Boolean,
    ): Action {
        val progress = currentProgress.coerceIn(0.0, 100.0)
        val jumped = alreadyStarted && kotlin.math.abs(progress - previousProgress) >= 15.0
        if (progress >= 80.0) return Action.STOP
        if (!isPlaying) {
            return if (alreadyStarted) Action.PAUSE else Action.NONE
        }
        if (!alreadyStarted && progress >= 1.0) return Action.START
        // Significant seek while playing → re-assert start so Simkl stays accurate.
        if (jumped && isPlaying) return Action.START
        return Action.NONE
    }
}
