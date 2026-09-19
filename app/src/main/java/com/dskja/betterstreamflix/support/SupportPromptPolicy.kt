package com.dskja.betterstreamflix.support

import com.dskja.betterstreamflix.utils.UserPreferences
import java.util.concurrent.TimeUnit

/**
 * Smart startup / soft-prompt policy for the Support presentation.
 * Never blocks playback; never shows when the user opted out permanently.
 */
object SupportPromptPolicy {

    /** Minimum gap between soft prompts when the user did not opt out. */
    val COOLDOWN_MS: Long = TimeUnit.DAYS.toMillis(5)

    /** Soft cap — after this many shows we stop unless they open Support themselves. */
    const val MAX_SOFT_SHOWS = 8

    fun shouldShowStartup(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (UserPreferences.neverShowSupportOnStart) return false
        if (UserPreferences.supportStartupShowCount >= MAX_SOFT_SHOWS) return false
        val last = UserPreferences.supportStartupLastShownAtMs
        if (last <= 0L) return true
        return nowMs - last >= COOLDOWN_MS
    }

    fun recordShown(nowMs: Long = System.currentTimeMillis()) {
        UserPreferences.supportStartupLastShownAtMs = nowMs
        UserPreferences.supportStartupShowCount =
            (UserPreferences.supportStartupShowCount + 1).coerceAtMost(MAX_SOFT_SHOWS + 1)
    }

    fun remindLater(nowMs: Long = System.currentTimeMillis()) {
        // Push next eligibility to a full cooldown from now without counting as a hard opt-out.
        UserPreferences.supportStartupLastShownAtMs = nowMs
    }

    fun markHubVisited(nowMs: Long = System.currentTimeMillis()) {
        UserPreferences.supportHubLastVisitedAtMs = nowMs
        UserPreferences.supportHubVisitCount = UserPreferences.supportHubVisitCount + 1
    }
}
