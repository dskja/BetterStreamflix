package com.betterstreamflix.data

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PaginationHelperTest {

    @Test
    fun `initial state should allow loading more`() {
        val helper = PaginationHelper(pageSize = 20)
        assertTrue(helper.canLoadMore())
        assertEquals(1, helper.getCurrentPage())
    }

    @Test
    fun `reset should go back to page 1`() = runTest {
        val helper = PaginationHelper(pageSize = 20)
        val page = helper.startLoading()
        helper.pageLoaded(20)
        assertEquals(2, helper.getCurrentPage())
        helper.reset()
        assertEquals(1, helper.getCurrentPage())
    }

    @Test
    fun `pageLoaded with less than pageSize should set hasMore false`() = runTest {
        val helper = PaginationHelper(pageSize = 20)
        helper.startLoading()
        helper.pageLoaded(10)
        assertTrue(!helper.canLoadMore())
    }

    @Test
    fun `pageLoaded with full pageSize should increment page`() = runTest {
        val helper = PaginationHelper(pageSize = 20)
        helper.startLoading()
        helper.pageLoaded(20)
        assertEquals(2, helper.getCurrentPage())
        assertTrue(helper.canLoadMore())
    }

    @Test
    fun `pageFailed should reset loading state`() = runTest {
        val helper = PaginationHelper(pageSize = 20)
        helper.startLoading()
        assertTrue(helper.isLoading())
        helper.pageFailed()
        assertTrue(!helper.isLoading())
    }

    @Test
    fun `shouldPrefetch should return true when near end`() = runTest {
        val helper = PaginationHelper(pageSize = 20, prefetchDistance = 5)
        helper.startLoading()
        helper.pageLoaded(20)
        assertTrue(helper.shouldPrefetch(10, 40, 30))
    }

    @Test
    fun `shouldPrefetch should return false when not near end`() = runTest {
        val helper = PaginationHelper(pageSize = 20, prefetchDistance = 5)
        helper.startLoading()
        helper.pageLoaded(20)
        assertTrue(!helper.shouldPrefetch(5, 100, 0))
    }
}
