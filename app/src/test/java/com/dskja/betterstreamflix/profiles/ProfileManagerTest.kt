package com.dskja.betterstreamflix.profiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileManagerTest {

    @Test
    fun scopedPrefKey_defaultProfileUsesBaseKey() {
        assertEquals(
            "subtitle_offsets",
            ProfileManager.scopedPrefKeyFor("subtitle_offsets", ProfileManager.DEFAULT_PROFILE_ID),
        )
    }

    @Test
    fun scopedPrefKey_nonDefaultProfileIsNamespaced() {
        assertEquals(
            "subtitle_offsets_p_kids01",
            ProfileManager.scopedPrefKeyFor("subtitle_offsets", "kids01"),
        )
    }

    @Test
    fun hashPin_isDeterministicForSameInput() {
        val first = ProfileManager.hashPin("1234", "profile_a")
        val second = ProfileManager.hashPin("1234", "profile_a")
        assertEquals(first, second)
        assertEquals(64, first.length)
    }

    @Test
    fun hashPin_differsByProfileId() {
        val profileA = ProfileManager.hashPin("1234", "profile_a")
        val profileB = ProfileManager.hashPin("1234", "profile_b")
        assertNotEquals(profileA, profileB)
    }

    @Test
    fun hashPin_differsByPin() {
        val pinA = ProfileManager.hashPin("1234", "profile_a")
        val pinB = ProfileManager.hashPin("5678", "profile_a")
        assertNotEquals(pinA, pinB)
    }

    @Test
    fun avatarKeys_containsExpectedPalette() {
        assertEquals(
            listOf("crimson", "ember", "aurora", "slate", "forest", "ocean", "gold", "rose"),
            ProfileManager.avatarKeys,
        )
    }

    @Test
    fun integrationKeys_containsAllSupportedServices() {
        assertTrue(UserProfile.Integration.ALL.contains(UserProfile.Integration.TRAKT))
        assertTrue(UserProfile.Integration.ALL.contains(UserProfile.Integration.JELLYFIN))
        assertTrue(UserProfile.Integration.ALL.contains(UserProfile.Integration.PLEX))
        assertTrue(UserProfile.Integration.ALL.contains(UserProfile.Integration.DEBRID))
        assertTrue(UserProfile.Integration.ALL.contains(UserProfile.Integration.SIMKL))
        assertTrue(UserProfile.Integration.ALL.contains(UserProfile.Integration.OPENSUBTITLES))
        assertTrue(UserProfile.Integration.ALL.contains(UserProfile.Integration.TMDB))
        assertEquals(7, UserProfile.Integration.ALL.size)
    }

    @Test
    fun defaultProfileId_isStableLiteral() {
        assertEquals("default", ProfileManager.DEFAULT_PROFILE_ID)
        assertEquals("default", ProfileStore.DEFAULT_PROFILE_ID)
    }

    @Test
    fun activeProfileId_fallsBackToDefaultBeforeInit() {
        assertEquals(ProfileManager.DEFAULT_PROFILE_ID, ProfileManager.activeProfileId)
    }

    @Test
    fun userProfile_copyPreservesIntegrationSet() {
        val profile = UserProfile(
            id = "test",
            displayName = "Test",
            avatarKey = "crimson",
            createdAtMillis = 1L,
            updatedAtMillis = 1L,
            enabledIntegrations = setOf(UserProfile.Integration.TRAKT, UserProfile.Integration.PLEX),
        )
        val updated = profile.copy(enabledIntegrations = profile.enabledIntegrations + UserProfile.Integration.TMDB)
        assertTrue(updated.enabledIntegrations.contains(UserProfile.Integration.TMDB))
        assertFalse(updated.enabledIntegrations.contains(UserProfile.Integration.DEBRID))
    }
}
