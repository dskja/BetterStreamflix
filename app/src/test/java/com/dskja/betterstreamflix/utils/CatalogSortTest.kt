package com.dskja.betterstreamflix.utils

import com.dskja.betterstreamflix.models.Movie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class CatalogSortTest {

    @Test
    fun lastReleaseSortsNewestFirst() {
        val older = Movie(id = "1", title = "Old").apply {
            released = Calendar.getInstance().apply { set(2010, 0, 1) }
        }
        val newer = Movie(id = "2", title = "New").apply {
            released = Calendar.getInstance().apply { set(2024, 5, 1) }
        }
        val unsorted = listOf(older, newer)
        val sorted = CatalogSort.movies(unsorted, CatalogSortMode.LAST_RELEASE)
        assertEquals(listOf("2", "1"), sorted.map { it.id })
    }

    @Test
    fun defaultModePreservesOrder() {
        val a = Movie(id = "a", title = "A")
        val b = Movie(id = "b", title = "B")
        val sorted = CatalogSort.movies(listOf(a, b), CatalogSortMode.DEFAULT)
        assertEquals(listOf("a", "b"), sorted.map { it.id })
        assertTrue(CatalogSortMode.fromKey("last_release") == CatalogSortMode.LAST_RELEASE)
    }
}
