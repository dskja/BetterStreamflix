package com.betterstreamflix.testing

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Test utilities — utility functions for testing coroutines,
 * flows, and async operations.
 */
object TestUtils {

    /**
     * Create a flow that emits values with delays.
     */
    fun <T> createDelayedFlow(values: List<T>, delayMs: Long = 100): Flow<T> = flow {
        values.forEach {
            delay(delayMs)
            emit(it)
        }
    }

    /**
     * Create a flow that emits an error.
     */
    fun <T> createErrorFlow(error: Throwable = RuntimeException("Test error")): Flow<T> = flow {
        throw error
    }

    /**
     * Wait for a condition to be true.
     */
    suspend fun waitForCondition(timeoutMs: Long = 5000L, checkIntervalMs: Long = 100L, condition: () -> Boolean): Boolean {
        val startTime = System.currentTimeMillis()
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            if (condition()) return true
            delay(checkIntervalMs)
        }
        return false
    }

    /**
     * Generate a random string.
     */
    fun randomString(length: Int = 10): String {
        val chars = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        return (1..length).map { chars.random() }.joinToString("")
    }

    /**
     * Generate a random URL.
     */
    fun randomUrl(): String {
        return "https://example.com/${randomString(8)}"
    }

    /**
     * Create a random content ID.
     */
    fun randomContentId(): String = "content_${randomString(6)}"

    /**
     * Measure execution time of a block.
     */
    suspend fun <T> measureTime(block: suspend () -> T): Pair<T, Long> {
        val start = System.currentTimeMillis()
        val result = block()
        val duration = System.currentTimeMillis() - start
        return result to duration
    }

    /**
     * Run a block multiple times and return average time.
     */
    suspend fun measureAverageTime(iterations: Int = 10, block: suspend () -> Unit): Long {
        var totalTime = 0L
        repeat(iterations) {
            val start = System.currentTimeMillis()
            block()
            totalTime += System.currentTimeMillis() - start
        }
        return totalTime / iterations
    }
}
