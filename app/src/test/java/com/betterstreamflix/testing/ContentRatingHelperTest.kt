package com.betterstreamflix.testing

import com.betterstreamflix.metadata.ContentRatingHelper
import com.betterstreamflix.metadata.ContentRatingHelper.ContentRating
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Tests for ContentRatingHelper.
 */
class ContentRatingHelperTest {

    @Test
    fun `parse FSK 12 returns FSK_12`() {
        assertEquals(ContentRating.FSK_12, ContentRatingHelper.parse("FSK 12"))
    }

    @Test
    fun `parse R returns R`() {
        assertEquals(ContentRating.R, ContentRatingHelper.parse("R"))
    }

    @Test
    fun `parse PG-13 returns PG_13`() {
        assertEquals(ContentRating.PG_13, ContentRatingHelper.parse("PG-13"))
    }

    @Test
    fun `parse TV-MA returns TV_MA`() {
        assertEquals(ContentRating.TV_MA, ContentRatingHelper.parse("TV-MA"))
    }

    @Test
    fun `parse null returns UNRATED`() {
        assertEquals(ContentRating.UNRATED, ContentRatingHelper.parse(null))
    }

    @Test
    fun `parse unknown returns UNRATED`() {
        assertEquals(ContentRating.UNRATED, ContentRatingHelper.parse("XYZ"))
    }

    @Test
    fun `isAppropriate FSK 0 for age 5 is true`() {
        assertTrue(ContentRatingHelper.isAppropriate(ContentRating.FSK_0, 5))
    }

    @Test
    fun `isAppropriate FSK 18 for age 15 is false`() {
        assertFalse(ContentRatingHelper.isAppropriate(ContentRating.FSK_18, 15))
    }

    @Test
    fun `isAppropriate FSK 16 for age 16 is true`() {
        assertTrue(ContentRatingHelper.isAppropriate(ContentRating.FSK_16, 16))
    }
}
