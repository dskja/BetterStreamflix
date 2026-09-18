package com.dskja.betterstreamflix.platform.trakt

/**
 * Pure progress → scrobble action mapping (unit-testable without network).
 */
object TraktScrobbler {
    enum class Action { START, PAUSE, STOP, NONE }

    fun decide(
        previousProgress: Double,
        currentProgress: Double,
        isPlaying: Boolean,
        alreadyStarted: Boolean,
    ): Action {
        val progress = currentProgress.coerceIn(0.0, 100.0)
        if (progress >= 80.0) return Action.STOP
        if (!isPlaying) {
            return if (alreadyStarted) Action.PAUSE else Action.NONE
        }
        if (!alreadyStarted && progress >= 1.0) return Action.START
        // previousProgress reserved for future “significant jump” heuristics
        @Suppress("UNUSED_EXPRESSION")
        previousProgress
        return Action.NONE
    }
}
