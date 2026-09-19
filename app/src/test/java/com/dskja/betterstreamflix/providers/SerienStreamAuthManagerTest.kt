package com.dskja.betterstreamflix.providers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SerienStreamAuthManagerTest {

    @Test
    fun authCookieNamesExtractsDistinctNames() {
        val names = SerienStreamAuthManager.authCookieNames(
            "cf_clearance=a; PHPSESSID=b; cf_clearance=c; __ddg1=noise",
        )
        assertTrue(names.any { it.equals("cf_clearance", ignoreCase = true) })
        assertTrue(names.any { it.equals("PHPSESSID", ignoreCase = true) || it.equals("phpsessid", ignoreCase = true) })
        assertFalse(names.any { it.startsWith("__ddg", ignoreCase = true) })
    }

    @Test
    fun parseDisplayNameFromUserChrome() {
        val html = """
            <div class="user-name">MaxMustermann</div>
            <title>Account | SerienStream</title>
        """.trimIndent()
        assertEquals("MaxMustermann", SerienStreamAuthManager.parseDisplayName(html))
    }

    @Test
    fun parseDisplayNameIgnoresSiteTitleOnly() {
        val html = "<title>SerienStream.to</title><body>hello</body>"
        assertNull(SerienStreamAuthManager.parseDisplayName(html))
    }

    @Test
    fun needsReauthHintDetectsChallengeAndLogin() {
        assertTrue(SerienStreamAuthManager.needsReauthHint("Just a moment..."))
        assertTrue(
            SerienStreamAuthManager.needsReauthHint(
                """<form id="login"><input name="email"><input name="password"></form>""",
            ),
        )
        assertFalse(SerienStreamAuthManager.needsReauthHint("seriesListContainer /serie/foo"))
    }
}
