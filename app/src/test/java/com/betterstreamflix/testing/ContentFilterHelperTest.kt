package com.betterstreamflix.testing

import com.betterstreamflix.metadata.ContentFilterHelper
import com.betterstreamflix.metadata.ContentFilterHelper.FilterCriteria
import com.betterstreamflix.metadata.ContentFilterHelper.FilterSelectors
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for ContentFilterHelper.
 */
class ContentFilterHelperTest {

    data class TestItem(
        val title: String,
        val genres: List<String>,
        val year: Int,
        val rating: Double,
        val type: String,
        val providerName: String,
    )

    private val items = listOf(
        TestItem("Movie A", listOf("Action", "Drama"), 2020, 8.0, "movie", "Provider1"),
        TestItem("Movie B", listOf("Comedy"), 2021, 6.0, "movie", "Provider2"),
        TestItem("Show C", listOf("Action", "Comedy"), 2019, 7.5, "tv", "Provider1"),
    )

    private val selectors = FilterSelectors<TestItem>(
        genres = { it.genres },
        year = { it.year },
        rating = { it.rating },
        type = { it.type },
        providerName = { it.providerName },
    )

    @Test
    fun `filter by genre Action`() {
        val criteria = FilterCriteria(genres = setOf("Action"))
        val result = ContentFilterHelper.filter(items, criteria, selectors)
        assertEquals(2, result.size)
    }

    @Test
    fun `filter by type movie`() {
        val criteria = FilterCriteria(type = "movie")
        val result = ContentFilterHelper.filter(items, criteria, selectors)
        assertEquals(2, result.size)
    }

    @Test
    fun `filter by provider`() {
        val criteria = FilterCriteria(providerName = "Provider1")
        val result = ContentFilterHelper.filter(items, criteria, selectors)
        assertEquals(2, result.size)
    }

    @Test
    fun `filter by min rating`() {
        val criteria = FilterCriteria(minRating = 7.0)
        val result = ContentFilterHelper.filter(items, criteria, selectors)
        assertEquals(2, result.size)
    }

    @Test
    fun `filter by year range`() {
        val criteria = FilterCriteria(minYear = 2020, maxYear = 2021)
        val result = ContentFilterHelper.filter(items, criteria, selectors)
        assertEquals(2, result.size)
    }

    @Test
    fun `filter with no criteria returns all`() {
        val criteria = FilterCriteria()
        val result = ContentFilterHelper.filter(items, criteria, selectors)
        assertEquals(3, result.size)
    }

    @Test
    fun `filter with multiple criteria`() {
        val criteria = FilterCriteria(genres = setOf("Action"), type = "movie")
        val result = ContentFilterHelper.filter(items, criteria, selectors)
        assertEquals(1, result.size)
        assertEquals("Movie A", result[0].title)
    }
}
