package com.betterstreamflix.testing

import com.betterstreamflix.metadata.ContentSortHelper
import com.betterstreamflix.metadata.ContentSortHelper.SortMode
import com.betterstreamflix.metadata.ContentSortHelper.SortSelectors
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for ContentSortHelper.
 */
class ContentSortHelperTest {

    data class TestItem(
        val title: String,
        val date: String,
        val rating: Double,
        val watchCount: Int,
        val lastWatched: Long,
    )

    private val items = listOf(
        TestItem("Zebra", "2020-01-01", 7.0, 10, 1000),
        TestItem("Apple", "2021-01-01", 9.0, 50, 2000),
        TestItem("Banana", "2019-01-01", 8.0, 30, 3000),
    )

    private val selectors = SortSelectors<TestItem>(
        title = { it.title },
        date = { it.date },
        rating = { it.rating },
        watchCount = { it.watchCount },
        lastWatched = { it.lastWatched },
    )

    @Test
    fun `sort by title ascending`() {
        val sorted = ContentSortHelper.sort(items, SortMode.TITLE_ASC, selectors)
        assertEquals("Apple", sorted[0].title)
        assertEquals("Banana", sorted[1].title)
        assertEquals("Zebra", sorted[2].title)
    }

    @Test
    fun `sort by title descending`() {
        val sorted = ContentSortHelper.sort(items, SortMode.TITLE_DESC, selectors)
        assertEquals("Zebra", sorted[0].title)
    }

    @Test
    fun `sort by rating highest`() {
        val sorted = ContentSortHelper.sort(items, SortMode.RATING_HIGHEST, selectors)
        assertEquals(9.0, sorted[0].rating, 0.001)
    }

    @Test
    fun `sort by date newest`() {
        val sorted = ContentSortHelper.sort(items, SortMode.DATE_NEWEST, selectors)
        assertEquals("2021-01-01", sorted[0].date)
    }

    @Test
    fun `sort by most watched`() {
        val sorted = ContentSortHelper.sort(items, SortMode.MOST_WATCHED, selectors)
        assertEquals(50, sorted[0].watchCount)
    }

    @Test
    fun `sort by recently watched`() {
        val sorted = ContentSortHelper.sort(items, SortMode.RECENTLY_WATCHED, selectors)
        assertEquals(3000, sorted[0].lastWatched)
    }
}
