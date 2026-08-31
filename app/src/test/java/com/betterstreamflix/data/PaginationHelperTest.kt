package com.betterstreamflix.data

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class PaginationHelperTest {

    @Test
    fun `initial state should allow loading more`() {
        val helper = PaginationHelper(pageSize = 20)
        assertThat(helper.canLoadMore()).isTrue()
        assertThat(helper.getCurrentPage()).isEqualTo(1)
    }

    @Test
    fun `reset should go back to page 1`() = runTest {
        val helper = PaginationHelper(pageSize = 20)
        val page = helper.startLoading()
        helper.pageLoaded(20)
        assertThat(helper.getCurrentPage()).isEqualTo(2)
        helper.reset()
        assertThat(helper.getCurrentPage()).isEqualTo(1)
    }

    @Test
    fun `pageLoaded with less than pageSize should set hasMore false`() = runTest {
        val helper = PaginationHelper(pageSize = 20)
        helper.startLoading()
        helper.pageLoaded(10)
        assertThat(helper.canLoadMore()).isFalse()
    }

    @Test
    fun `pageLoaded with full pageSize should increment page`() = runTest {
        val helper = PaginationHelper(pageSize = 20)
        helper.startLoading()
        helper.pageLoaded(20)
        assertThat(helper.getCurrentPage()).isEqualTo(2)
        assertThat(helper.canLoadMore()).isTrue()
    }

    @Test
    fun `pageFailed should reset loading state`() = runTest {
        val helper = PaginationHelper(pageSize = 20)
        helper.startLoading()
        assertThat(helper.isLoading()).isTrue()
        helper.pageFailed()
        assertThat(helper.isLoading()).isFalse()
    }

    @Test
    fun `shouldPrefetch should return true when near end`() = runTest {
        val helper = PaginationHelper(pageSize = 20, prefetchDistance = 5)
        helper.startLoading()
        helper.pageLoaded(20)
        assertThat(helper.shouldPrefetch(10, 40, 30)).isTrue()
    }

    @Test
    fun `shouldPrefetch should return false when not near end`() = runTest {
        val helper = PaginationHelper(pageSize = 20, prefetchDistance = 5)
        helper.startLoading()
        helper.pageLoaded(20)
        assertThat(helper.shouldPrefetch(5, 100, 0)).isFalse()
    }
}
