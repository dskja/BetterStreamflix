package com.dskja.betterstreamflix.player

/**
 * Unified playback failover policy for mobile + TV.
 * Prefer the next hoster first; after servers are exhausted, retry once with software decoding.
 */
object PlaybackFailover {

    sealed class Action {
        data class TryNextServer(val nextIndex: Int) : Action()
        data object RetrySoftwareDecoder : Action()
        data object GiveUp : Action()
    }

    fun decide(
        currentServerIndex: Int,
        serverCount: Int,
        playbackAlreadyStarted: Boolean,
        softwareDecoderAlreadyEnabled: Boolean,
        allowMidPlaybackFailover: Boolean = false,
    ): Action {
        if (playbackAlreadyStarted && !allowMidPlaybackFailover) {
            // Mid-play URI clear on TV looks like a crash-to-home; stop cascading.
            return if (!softwareDecoderAlreadyEnabled) Action.RetrySoftwareDecoder else Action.GiveUp
        }
        val next = currentServerIndex + 1
        if (next in 0 until serverCount) {
            return Action.TryNextServer(next)
        }
        if (!softwareDecoderAlreadyEnabled) {
            return Action.RetrySoftwareDecoder
        }
        return Action.GiveUp
    }
}
