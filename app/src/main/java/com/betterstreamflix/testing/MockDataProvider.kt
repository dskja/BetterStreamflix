package com.betterstreamflix.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Mock data provider — provides mock data for testing and
 * preview purposes.
 */
object MockDataProvider {

    /**
     * Get mock trending content.
     */
    fun getMockTrending(): List<TestFixtures.TestContentItem> {
        return listOf(
            TestFixtures.createContentItem("trend_1", "The Great Adventure", "movie"),
            TestFixtures.createContentItem("trend_2", "Mystery Island", "movie"),
            TestFixtures.createContentItem("trend_3", "City of Shadows", "tv"),
            TestFixtures.createContentItem("trend_4", "Last Stand", "movie"),
            TestFixtures.createContentItem("trend_5", "Beyond the Horizon", "tv"),
        )
    }

    /**
     * Get mock favorites.
     */
    fun getMockFavorites(): List<TestFixtures.TestContentItem> {
        return listOf(
            TestFixtures.createContentItem("fav_1", "Favorite Movie 1", "movie"),
            TestFixtures.createContentItem("fav_2", "Favorite Show 1", "tv"),
            TestFixtures.createContentItem("fav_3", "Favorite Movie 2", "movie"),
        )
    }

    /**
     * Get mock continue watching.
     */
    fun getMockContinueWatching(): List<TestFixtures.TestContentItem> {
        return listOf(
            TestFixtures.createContentItem("cw_1", "Continue Watching 1", "movie"),
            TestFixtures.createContentItem("cw_2", "Continue Watching 2", "tv"),
        )
    }

    /**
     * Get mock search results.
     */
    fun getMockSearchResults(query: String): TestFixtures.TestSearchResult {
        val results = TestFixtures.createContentList(5).map {
            it.copy(title = "$query - ${it.title}")
        }
        return TestFixtures.TestSearchResult(
            query = query,
            results = results,
            totalCount = results.size,
            page = 0,
        )
    }

    /**
     * Get mock content as a Flow.
     */
    fun getMockContentFlow(): Flow<List<TestFixtures.TestContentItem>> = flow {
        emit(getMockTrending())
    }

    /**
     * Get mock empty results.
     */
    fun getMockEmptyResults(): List<TestFixtures.TestContentItem> = emptyList()
}
