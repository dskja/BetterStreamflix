package com.dskja.betterstreamflix.providers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SerienStreamProxyTest {

    @Test
    fun proxyHost_isRecognized() {
        assertTrue(SerienStreamEndpoints.isProxyHost(SerienStreamEndpoints.PROXY_HOST))
        assertTrue(SerienStreamEndpoints.isProxyHost("186.2.175.5"))
        assertFalse(SerienStreamEndpoints.isProxyHost("serienstream.to"))
    }

    @Test
    fun originFor_usesHttpForProxyAndHttpsForDomains() {
        assertEquals("http://186.2.175.5/", SerienStreamEndpoints.originFor("186.2.175.5"))
        assertEquals("https://serienstream.to/", SerienStreamEndpoints.originFor("serienstream.to"))
        assertEquals("https://serienstream.cx/", SerienStreamEndpoints.originFor("https://serienstream.cx/"))
    }

    @Test
    fun candidateHosts_includeProxyAndMirrors() {
        val domains = SerienStreamEndpoints.candidateHosts("serienstream.to")
        assertEquals("serienstream.to", domains.first())
        assertTrue(domains.contains(SerienStreamEndpoints.PROXY_HOST))
        assertTrue(domains.contains("serienstream.cx"))
    }

    @Test
    fun isKnownHost_acceptsProxyIp() {
        assertTrue(SerienStreamEndpoints.isKnownHost("http://186.2.175.5/login"))
        assertTrue(SerienStreamEndpoints.isKnownHost("186.2.175.5"))
        assertTrue(SerienStreamEndpoints.isKnownHost("https://serienstream.to/serie/foo"))
        assertFalse(SerienStreamEndpoints.isKnownHost("https://evil.example/"))
    }

    @Test
    fun defaultHost_isOfficialProxy() {
        assertEquals("186.2.175.5", SerienStreamEndpoints.DEFAULT_HOST)
    }
}
