package com.betterstreamflix.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Smoke test for UserPreferences Key enum.
 * Validates that all expected preference keys exist and are accessible.
 */
class UserPreferencesSmokeTest {

    @Test
    fun `all critical preference keys exist`() {
        val expectedKeys = listOf(
            "PROVIDER_CACHE",
            "SELECTED_PROVIDER",
            "APP_LAYOUT",
            "DEFAULT_PROVIDER_LANGUAGE",
            "DEFAULT_PROVIDER_REGION",
        )

        // Verify Key enum has entries
        val keyFields = UserPreferences.Key::class.java.declaredFields
            .filter { java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .map { it.name }

        expectedKeys.forEach { expected ->
            assertTrue("Key.$expected should exist", keyFields.contains(expected))
        }
    }

    @Test
    fun `provider support map is not empty`() {
        val providers = com.betterstreamflix.providers.Provider.Companion.providers
        assertNotNull(providers)
        assertTrue("Provider map should not be empty", providers.isNotEmpty())
        assertTrue("Should have at least 30 providers", providers.size >= 30)
    }

    @Test
    fun `provider support functions work`() {
        val providers = com.betterstreamflix.providers.Provider.Companion.providers
        val firstProvider = providers.keys.first()
        val supportsMovies = com.betterstreamflix.providers.Provider.Companion.supportsMovies(firstProvider)
        val supportsTvShows = com.betterstreamflix.providers.Provider.Companion.supportsTvShows(firstProvider)
        // At least one of them should be true
        assertTrue("Provider should support movies or TV shows", supportsMovies || supportsTvShows)
    }

    @Test
    fun `findByName returns null for unknown provider`() {
        val result = com.betterstreamflix.providers.Provider.Companion.findByName("NonExistentProvider12345")
        assertEquals(null, result)
    }

    @Test
    fun `findByName returns provider for known name`() {
        val providers = com.betterstreamflix.providers.Provider.Companion.providers
        val firstProvider = providers.keys.first()
        val found = com.betterstreamflix.providers.Provider.Companion.findByName(firstProvider.name)
        assertNotNull("findByName should find existing provider", found)
        assertEquals(firstProvider.name, found!!.name)
    }
}
