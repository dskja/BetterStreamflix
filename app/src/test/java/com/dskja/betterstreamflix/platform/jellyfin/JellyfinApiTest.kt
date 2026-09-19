package com.dskja.betterstreamflix.platform.jellyfin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JellyfinApiTest {

    @Test
    fun configured_requiresHttpBaseTokenAndUser() {
        val ok = JellyfinApi(
            baseUrlProvider = { "https://jelly.example" },
            tokenProvider = { "token" },
            userIdProvider = { "user-1" },
        )
        assertTrue(ok.configured())

        val missingToken = JellyfinApi(
            baseUrlProvider = { "https://jelly.example" },
            tokenProvider = { "" },
            userIdProvider = { "user-1" },
        )
        assertFalse(missingToken.configured())

        val badBase = JellyfinApi(
            baseUrlProvider = { "jelly.example" },
            tokenProvider = { "token" },
            userIdProvider = { "user-1" },
        )
        assertFalse(badBase.configured())
    }

    @Test
    fun parseQuickConnectState_readsPascalAndCamelCase() {
        val api = JellyfinApi(
            baseUrlProvider = { "https://jelly.example" },
            tokenProvider = { "t" },
            userIdProvider = { "u" },
        )
        val pascal = api.parseQuickConnectState(
            """{"Secret":"sec","Code":"123456","Authenticated":false}""",
        )
        assertNotNull(pascal)
        assertEquals("sec", pascal!!.secret)
        assertEquals("123456", pascal.code)
        assertFalse(pascal.authenticated)

        val camel = api.parseQuickConnectState(
            """{"secret":"s2","code":"654321","authenticated":true}""",
        )
        assertNotNull(camel)
        assertTrue(camel!!.authenticated)

        assertNull(api.parseQuickConnectState("""{"Secret":"","Code":"1"}"""))
    }
}
