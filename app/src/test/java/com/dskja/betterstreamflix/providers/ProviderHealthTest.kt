package com.dskja.betterstreamflix.providers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderHealthTest {

    @Test
    fun quarantineContainsKnownFragileProviders() {
        assertTrue(ProviderHealth.isQuarantinedName("Fanpelis"))
        assertTrue(ProviderHealth.isQuarantinedName("Kidraz"))
        assertFalse(ProviderHealth.isQuarantinedName("SerienStream"))
        assertFalse(ProviderHealth.isQuarantinedName("StreamingCommunity"))
    }

    @Test
    fun smokeListCoversCoreMarkets() {
        val names = ProviderHealth.topSmokeNames
        assertTrue(names.contains("SFlix"))
        assertTrue(names.contains("SerienStream"))
        assertTrue(names.contains("GuardaFlix"))
        assertTrue(names.contains("HDFilme"))
        assertTrue(names.contains("Frembed"))
        assertFalse(names.contains("Sflix"))
    }
}
