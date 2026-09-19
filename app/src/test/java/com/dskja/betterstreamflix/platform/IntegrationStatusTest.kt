package com.dskja.betterstreamflix.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IntegrationStatusTest {

    @Test
    fun levelHealthFlags() {
        assertTrue(IntegrationStatus.Snapshot(IntegrationStatus.Level.READY).isHealthy)
        assertTrue(IntegrationStatus.Snapshot(IntegrationStatus.Level.SIGNED_IN).isHealthy)
        assertFalse(IntegrationStatus.Snapshot(IntegrationStatus.Level.DISABLED).isHealthy)
        assertFalse(IntegrationStatus.Snapshot(IntegrationStatus.Level.NOT_CONFIGURED).isHealthy)
        assertFalse(IntegrationStatus.Snapshot(IntegrationStatus.Level.UNAVAILABLE).isHealthy)
    }

    @Test
    fun unknownScreenIsNotConfigured() {
        assertEquals(
            IntegrationStatus.Level.NOT_CONFIGURED,
            IntegrationStatus.forScreen("screen_unknown").level,
        )
    }

    @Test
    fun platformHubCardKeysMatchSettingsScreens() {
        val expected = setOf(
            "screen_platform_trakt",
            "screen_platform_jellyfin",
            "screen_platform_plex",
            "screen_platform_debrid",
            "screen_platform_simkl",
            "screen_platform_subtitles",
            "screen_platform_player",
            "screen_platform_plugins",
        )
        val actual = com.dskja.betterstreamflix.fragments.settings.PlatformHubCategories
            .cards()
            .map { it.screenKey }
            .toSet()
        assertEquals(expected, actual)
    }
}
