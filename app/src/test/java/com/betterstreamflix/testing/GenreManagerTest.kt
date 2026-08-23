package com.betterstreamflix.testing

import com.betterstreamflix.metadata.GenreManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Tests for GenreManager normalization.
 */
class GenreManagerTest {

    @Test
    fun `normalizeGenre maps English action`() {
        assertEquals("Action", GenreManager.normalizeGenre("Action"))
    }

    @Test
    fun `normalizeGenre maps German Aktion`() {
        assertEquals("Action", GenreManager.normalizeGenre("Aktion"))
    }

    @Test
    fun `normalizeGenre maps Sci-Fi`() {
        assertEquals("Science Fiction", GenreManager.normalizeGenre("Sci-Fi"))
    }

    @Test
    fun `normalizeGenre maps case-insensitive`() {
        assertEquals("Comedy", GenreManager.normalizeGenre("comedy"))
    }

    @Test
    fun `normalizeGenre returns null for unknown`() {
        assertNull(GenreManager.normalizeGenre("Unknown Genre"))
    }

    @Test
    fun `extractGenres deduplicates`() {
        val genres = GenreManager.extractGenres(listOf("Action", "Aktion", "Comedy", "Komödie"))
        assertEquals(2, genres.size)
        assertEquals("Action", genres[0])
        assertEquals("Comedy", genres[1])
    }

    @Test
    fun `extractGenres sorts alphabetically`() {
        val genres = GenreManager.extractGenres(listOf("Horror", "Action", "Drama"))
        assertEquals(listOf("Action", "Drama", "Horror"), genres)
    }

    @Test
    fun `extractGenres filters unknowns`() {
        val genres = GenreManager.extractGenres(listOf("Action", "Unknown", "Drama"))
        assertEquals(2, genres.size)
    }
}
