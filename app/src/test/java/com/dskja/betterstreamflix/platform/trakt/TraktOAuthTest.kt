package com.dskja.betterstreamflix.platform.trakt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TraktOAuthTest {
    @Test
    fun redirectUriIsStable() {
        assertEquals("betterstreamflix://trakt/oauth", TraktConfig.OAUTH_REDIRECT_URI)
        assertEquals("betterstreamflix", TraktConfig.OAUTH_SCHEME)
        assertEquals("trakt", TraktConfig.OAUTH_HOST)
    }

    @Test
    fun oauthPathConstant() {
        assertEquals("oauth", TraktConfig.OAUTH_PATH)
        assertTrue(TraktConfig.OAUTH_REDIRECT_URI.endsWith("/oauth"))
    }
}
