package com.betterstreamflix.data

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RetryHelperTest {

    @Test
    fun `retryWithBackoff should return result on first try`() = runTest {
        val result = RetryHelper.retryWithBackoff<String>(maxRetries = 3) { "success" }
        assertEquals("success", result)
    }

    @Test
    fun `retryWithBackoff should retry on null and eventually succeed`() = runTest {
        var attempts = 0
        val result = RetryHelper.retryWithBackoff<String>(
            maxRetries = 3,
            initialDelayMs = 1,
        ) { attempt ->
            attempts++
            if (attempt < 1) null else "success"
        }
        assertEquals("success", result)
        assertEquals(2, attempts)
    }

    @Test
    fun `retryWithBackoff should return null if all retries fail`() = runTest {
        val result = RetryHelper.retryWithBackoff<String>(
            maxRetries = 2,
            initialDelayMs = 1,
        ) { null }
        assertNull(result)
    }

    @Test
    fun `retryOrThrow should throw on all failures`() = runTest {
        var threw = false
        try {
            RetryHelper.retryOrThrow<String>(
                maxRetries = 2,
                initialDelayMs = 1,
            ) { throw RuntimeException("fail") }
        } catch (e: Exception) {
            threw = true
        }
        assert(threw)
    }

    @Test
    fun `retryOrThrow should succeed on retry`() = runTest {
        var attempts = 0
        val result = RetryHelper.retryOrThrow<String>(
            maxRetries = 3,
            initialDelayMs = 1,
        ) {
            attempts++
            if (attempts < 2) throw RuntimeException("fail")
            "success"
        }
        assertEquals("success", result)
    }
}
