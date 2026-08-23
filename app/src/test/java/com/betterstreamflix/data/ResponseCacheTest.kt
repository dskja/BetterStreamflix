package com.betterstreamflix.data

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ResponseCacheTest {

    @Test
    fun `get should return null for missing key`() = runTest {
        val cache = ResponseCache(maxSize = 10)
        assertNull(cache.get<String>("missing"))
    }

    @Test
    fun `put and get should store and retrieve value`() = runTest {
        val cache = ResponseCache(maxSize = 10)
        cache.put("key", "value")
        assertEquals("value", cache.get<String>("key"))
    }

    @Test
    fun `clear should remove all entries`() = runTest {
        val cache = ResponseCache(maxSize = 10)
        cache.put("key1", "value1")
        cache.put("key2", "value2")
        cache.clear()
        assertNull(cache.get<String>("key1"))
        assertNull(cache.get<String>("key2"))
        assertEquals(0, cache.size())
    }

    @Test
    fun `remove should delete specific key`() = runTest {
        val cache = ResponseCache(maxSize = 10)
        cache.put("key1", "value1")
        cache.remove("key1")
        assertNull(cache.get<String>("key1"))
    }
}
