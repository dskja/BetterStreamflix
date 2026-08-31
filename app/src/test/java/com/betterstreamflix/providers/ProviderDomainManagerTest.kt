package com.betterstreamflix.providers

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderDomainManagerTest {

    @Test
    fun matchesKnownProviderDomain() {
        assertTrue(ProviderDomainManager.matchesProviderDomain("SerienStream", "https://s.to/serie/test"))
        assertTrue(ProviderDomainManager.matchesProviderDomain("Cuevana", "https://cuevana.gs/pelicula"))
    }

    @Test
    fun returnsAlternativesForKnownProviders() {
        val alternatives = ProviderDomainManager.getAlternativeDomains("StreamingCommunity")
        assertFalse(alternatives.isEmpty())
    }
}
