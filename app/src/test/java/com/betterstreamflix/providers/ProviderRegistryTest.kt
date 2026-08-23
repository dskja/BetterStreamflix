package com.betterstreamflix.providers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Validates that every registered provider in the providers map
 * has correct interface implementation metadata.
 */
class ProviderRegistryTest {

    @Test
    fun `all providers have unique names`() {
        val names = Provider.Companion.providers.keys.map { it.name }
        val uniqueNames = names.toSet()
        assertEquals(
            "Provider names should be unique. Duplicates: ${names.groupingBy { it }.eachCount().filter { it.value > 1 }}",
            names.size,
            uniqueNames.size
        )
    }

    @Test
    fun `all providers have non-empty baseUrl`() {
        Provider.Companion.providers.keys.forEach { provider ->
            assertTrue(
                "Provider ${provider.name} should have non-empty baseUrl",
                provider.baseUrl.isNotEmpty()
            )
        }
    }

    @Test
    fun `all providers have non-empty logo`() {
        Provider.Companion.providers.keys.forEach { provider ->
            assertTrue(
                "Provider ${provider.name} should have non-empty logo",
                provider.logo.isNotEmpty()
            )
        }
    }

    @Test
    fun `all providers have non-empty language`() {
        Provider.Companion.providers.keys.forEach { provider ->
            assertTrue(
                "Provider ${provider.name} should have non-empty language",
                provider.language.isNotEmpty()
            )
        }
    }

    @Test
    fun `at least one provider supports movies`() {
        val movieProviders = Provider.Companion.providers.entries
            .filter { it.value.movies }
            .map { it.key.name }
        assertTrue("At least one provider should support movies", movieProviders.isNotEmpty())
    }

    @Test
    fun `at least one provider supports tv shows`() {
        val tvProviders = Provider.Companion.providers.entries
            .filter { it.value.tvShows }
            .map { it.key.name }
        assertTrue("At least one provider should support TV shows", tvProviders.isNotEmpty())
    }

    @Test
    fun `findByName works for all registered providers`() {
        Provider.Companion.providers.keys.forEach { provider ->
            val found = Provider.Companion.findByName(provider.name)
            assertNotNull("findByName should find ${provider.name}", found)
            assertEquals(provider.name, found!!.name)
        }
    }
}
