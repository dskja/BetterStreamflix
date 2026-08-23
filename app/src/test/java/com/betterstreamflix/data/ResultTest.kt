package com.betterstreamflix.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ResultTest {

    @Test
    fun `Success should be isSuccess and return data`() {
        val result = Result.Success("hello")
        assertTrue(result.isSuccess)
        assertFalse(result.isError)
        assertFalse(result.isLoading)
        assertEquals("hello", result.getOrNull())
    }

    @Test
    fun `Error should be isError`() {
        val result = Result.Error(ErrorType.Network("timeout"))
        assertTrue(result.isError)
        assertFalse(result.isSuccess)
        assertNull(result.getOrNull())
    }

    @Test
    fun `Loading should be isLoading`() {
        val result = Result.Loading
        assertTrue(result.isLoading)
        assertFalse(result.isSuccess)
        assertFalse(result.isError)
        assertNull(result.getOrNull())
    }

    @Test
    fun `map should transform Success data`() {
        val result = Result.Success(42)
        val mapped = result.map { it.toString() }
        assertTrue(mapped.isSuccess)
        assertEquals("42", mapped.getOrNull())
    }

    @Test
    fun `map should pass through Error`() {
        val result: Result<Int> = Result.Error(ErrorType.Network("fail"))
        val mapped = result.map { it.toString() }
        assertTrue(mapped.isError)
    }

    @Test
    fun `map should pass through Loading`() {
        val result: Result<Int> = Result.Loading
        val mapped = result.map { it.toString() }
        assertTrue(mapped.isLoading)
    }

    @Test
    fun `onSuccess should execute for Success`() {
        var captured = ""
        Result.Success("data").onSuccess { captured = it }
        assertEquals("data", captured)
    }

    @Test
    fun `onSuccess should not execute for Error`() {
        var executed = false
        Result.Error(ErrorType.Network("fail")).onSuccess { executed = true }
        assertFalse(executed)
    }

    @Test
    fun `onError should execute for Error`() {
        var captured: ErrorType? = null
        Result.Error(ErrorType.Network("fail")).onError { captured = it }
        assertNotNull(captured)
        assertTrue(captured is ErrorType.Network)
    }

    @Test
    fun `runCatching should wrap success`() {
        val result = Result.runCatching { 42 }
        assertTrue(result.isSuccess)
        assertEquals(42, result.getOrNull())
    }

    @Test
    fun `runCatching should wrap exception`() {
        val result = Result.runCatching<Int> { throw RuntimeException("boom") }
        assertTrue(result.isError)
    }

    @Test
    fun `ErrorType from SocketTimeoutException should be Network`() {
        val error = ErrorType.from(java.net.SocketTimeoutException("timeout"))
        assertTrue(error is ErrorType.Network)
    }

    @Test
    fun `ErrorType from UnknownHostException should be Network`() {
        val error = ErrorType.from(java.net.UnknownHostException("dns fail"))
        assertTrue(error is ErrorType.Network)
    }
}
