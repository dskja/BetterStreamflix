package com.betterstreamflix.testing

import com.betterstreamflix.data.Result
import com.betterstreamflix.data.Result.ErrorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Tests for Result wrapper extensions and edge cases.
 */
class ResultExtensionsTest {

    @Test
    fun `Success getOrNull returns data`() {
        val result: Result<String> = Result.Success("hello")
        assertEquals("hello", result.getOrNull())
    }

    @Test
    fun `Error getOrNull returns null`() {
        val result: Result<String> = Result.Error(ErrorType.Network("fail"))
        assertNull(result.getOrNull())
    }

    @Test
    fun `Loading getOrNull returns null`() {
        val result: Result<String> = Result.Loading
        assertNull(result.getOrNull())
    }

    @Test
    fun `Success getOrElse returns data`() {
        val result: Result<String> = Result.Success("hello")
        assertEquals("hello", result.getOrElse { "default" })
    }

    @Test
    fun `Error getOrElse returns default`() {
        val result: Result<String> = Result.Error(ErrorType.Network("fail"))
        assertEquals("default", result.getOrElse { "default" })
    }

    @Test
    fun `Success map transforms data`() {
        val result: Result<Int> = Result.Success(42)
        val mapped = result.map { it.toString() }
        assertTrue(mapped is Result.Success)
        assertEquals("42", (mapped as Result.Success).data)
    }

    @Test
    fun `Error map preserves error`() {
        val result: Result<Int> = Result.Error(ErrorType.Network("fail"))
        val mapped = result.map { it.toString() }
        assertTrue(mapped is Result.Error)
    }

    @Test
    fun `Success onSuccess is called`() {
        var called = false
        val result: Result<String> = Result.Success("hello")
        result.onSuccess { called = true }
        assertTrue(called)
    }

    @Test
    fun `Error onSuccess is not called`() {
        var called = false
        val result: Result<String> = Result.Error(ErrorType.Network("fail"))
        result.onSuccess { called = true }
        assertFalse(called)
    }

    @Test
    fun `Success onError is not called`() {
        var called = false
        val result: Result<String> = Result.Success("hello")
        result.onError { called = true }
        assertFalse(called)
    }

    @Test
    fun `Error onError is called`() {
        var called = false
        val result: Result<String> = Result.Error(ErrorType.Network("fail"))
        result.onError { called = true }
        assertTrue(called)
    }

    @Test
    fun `Success isSuccess is true`() {
        val result: Result<String> = Result.Success("hello")
        assertTrue(result.isSuccess)
        assertFalse(result.isError)
        assertFalse(result.isLoading)
    }

    @Test
    fun `Error isError is true`() {
        val result: Result<String> = Result.Error(ErrorType.Network("fail"))
        assertTrue(result.isError)
        assertFalse(result.isSuccess)
        assertFalse(result.isLoading)
    }

    @Test
    fun `Loading isLoading is true`() {
        val result: Result<String> = Result.Loading
        assertTrue(result.isLoading)
        assertFalse(result.isSuccess)
        assertFalse(result.isError)
    }
}
