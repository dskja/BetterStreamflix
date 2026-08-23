package com.betterstreamflix.testing

import com.betterstreamflix.data.Result
import com.betterstreamflix.data.Result.ErrorType

/**
 * Test fixtures — provides common test data for unit and integration tests.
 */
object TestFixtures {

    /**
     * Sample movie data for tests.
     */
    val sampleMovieTitle = "The Matrix"
    val sampleMovieYear = 1999
    val sampleMovieVideoId = "movie_123"
    val sampleMovieUrl = "https://example.com/movie/matrix.mp4"

    /**
     * Sample TV show data for tests.
     */
    val sampleTvTitle = "Breaking Bad"
    val sampleTvYear = 2008
    val sampleTvVideoId = "tv_456"
    val sampleTvUrl = "https://example.com/tv/breakingbad/s1e1.mp4"

    /**
     * Sample provider name.
     */
    val sampleProviderName = "TestProvider"

    /**
     * Create a success Result for tests.
     */
    fun <T> successResult(data: T): Result<T> = Result.Success(data)

    /**
     * Create an error Result for tests.
     */
    fun <T> errorResult(message: String = "Test error"): Result<T> =
        Result.Error(ErrorType.Unknown(message))

    /**
     * Create a loading Result for tests.
     */
    fun <T> loadingResult(): Result<T> = Result.Loading

    /**
     * Sample search query.
     */
    val sampleSearchQuery = "matrix"

    /**
     * Sample pagination data.
     */
    val samplePageSize = 20
    val sampleMaxPages = 10
}
