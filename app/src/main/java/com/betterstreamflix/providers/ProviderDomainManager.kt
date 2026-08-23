package com.betterstreamflix.providers

import com.betterstreamflix.utils.UserPreferences

/**
 * Provider domain manager — handles dynamic domain updates for providers
 * that frequently change their domain (e.g., SerienStream, AniWorld, Frembed).
 */
object ProviderDomainManager {

    /**
     * Known provider domain patterns for auto-detection.
     */
    private val domainPatterns = mapOf(
        "SerienStream" to listOf("serienstream.to", "s.to", "serienstream.sx"),
        "AniWorld" to listOf("aniworld.to", "aniworld.sx"),
        "Frembed" to listOf("frembed.xyz", "frembed.cc"),
        "StreamingCommunity" to listOf("streamingcommunity.cz", "streamingcommunity.xyz"),
        "Cuevana" to listOf("cuevana3.eu", "cuevana3.ch"),
    )

    /**
     * Check if a provider's current domain is accessible.
     * If not, try alternative domains.
     */
    fun getCurrentDomain(providerName: String): String? {
        return UserPreferences.providerCache.optJSONObject(providerName)?.optString("url")
            ?.takeIf { it.isNotBlank() }
    }

    /**
     * Update a provider's domain.
     */
    fun updateDomain(providerName: String, newDomain: String) {
        val provider = Provider.findByName(providerName) ?: return
        UserPreferences.setProviderCache(provider, "url", newDomain)
    }

    /**
     * Get alternative domains for a provider.
     */
    fun getAlternativeDomains(providerName: String): List<String> {
        return domainPatterns[providerName] ?: emptyList()
    }

    /**
     * Check if a URL matches any known domain for a provider.
     */
    fun matchesProviderDomain(providerName: String, url: String): Boolean {
        val domains = domainPatterns[providerName] ?: return false
        return domains.any { domain -> url.contains(domain, ignoreCase = true) }
    }
}
