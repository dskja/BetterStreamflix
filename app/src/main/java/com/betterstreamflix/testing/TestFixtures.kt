package com.betterstreamflix.testing

import com.betterstreamflix.architecture.OperationResult

/**
 * Test fixtures — provides test data for unit and integration tests.
 */
object TestFixtures {

    /**
     * Create a test content item.
     */
    fun createContentItem(
        id: String = "test_1",
        title: String = "Test Movie",
        type: String = "movie",
        providerName: String = "test_provider",
    ) = TestContentItem(
        id = id,
        title = title,
        type = type,
        providerName = providerName,
        posterUrl = "https://example.com/poster.jpg",
        backdropUrl = "https://example.com/backdrop.jpg",
        overview = "Test overview",
        rating = 7.5,
        year = "2024",
        genres = listOf("Action", "Drama"),
    )

    /**
     * Create a list of test content items.
     */
    fun createContentList(count: Int = 10): List<TestContentItem> {
        return (1..count).map { i ->
            createContentItem(
                id = "test_$i",
                title = "Test Movie $i",
            )
        }
    }

    /**
     * Create a test search result.
     */
    fun createSearchResult(query: String = "test", resultCount: Int = 5) = TestSearchResult(
        query = query,
        results = createContentList(resultCount),
        totalCount = resultCount,
        page = 0,
    )

    /**
     * Create a test playback state.
     */
    fun createPlaybackState(
        positionMs: Long = 60_000,
        durationMs: Long = 3_600_000,
        speed: Float = 1.0f,
    ) = TestPlaybackState(
        positionMs = positionMs,
        durationMs = durationMs,
        isPlaying = true,
        playbackSpeed = speed,
        subtitleEnabled = true,
    )

    /**
     * Create a test error.
     */
    fun <T> createErrorResult(message: String = "Test error"): OperationResult<T> {
        return OperationResult.failure(RuntimeException(message))
    }

    /**
     * Create a test success result.
     */
    fun <T> createSuccessResult(data: T): OperationResult<T> {
        return OperationResult.success(data)
    }

    data class TestContentItem(
        val id: String,
        val title: String,
        val type: String,
        val providerName: String,
        val posterUrl: String?,
        val backdropUrl: String?,
        val overview: String,
        val rating: Double,
        val year: String,
        val genres: List<String>,
    )

    data class TestSearchResult(
        val query: String,
        val results: List<TestContentItem>,
        val totalCount: Int,
        val page: Int,
    )

    data class TestPlaybackState(
        val positionMs: Long,
        val durationMs: Long,
        val isPlaying: Boolean,
        val playbackSpeed: Float,
        val subtitleEnabled: Boolean,
    )
}
