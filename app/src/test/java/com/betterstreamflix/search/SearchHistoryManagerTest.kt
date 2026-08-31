package com.betterstreamflix.search

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SearchHistoryManagerTest {

    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        SearchHistoryManager.clearHistory(context)
    }

    @Test
    fun addAndRetrieveHistory() {
        SearchHistoryManager.addSearch(context, "Matrix", 3)
        SearchHistoryManager.addSearch(context, "Inception", 1)
        val history = SearchHistoryManager.getHistory(context)
        assertEquals(2, history.size)
        assertEquals("Inception", history.first().query)
    }

    @Test
    fun suggestionsFilterPartialQuery() {
        SearchHistoryManager.addSearch(context, "Breaking Bad", 1)
        SearchHistoryManager.addSearch(context, "Better Call Saul", 1)
        val suggestions = SearchHistoryManager.getSuggestions(context, "break")
        assertTrue(suggestions.any { it.contains("Breaking", ignoreCase = true) })
    }
}
