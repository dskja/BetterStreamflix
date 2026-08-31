package com.betterstreamflix.providers

import android.util.Log
import com.betterstreamflix.utils.NetworkClient
import com.betterstreamflix.utils.UserPreferences
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Provider domain manager — handles dynamic domain updates for providers
 * that frequently change their domain (e.g., SerienStream, AniWorld, Frembed).
 */
object ProviderDomainManager {

    private const val TAG = "ProviderDomainManager"

    /**
     * Known provider domain patterns for auto-detection.
     */
    private val domainPatterns = mapOf(
        "SerienStream" to listOf("186.2.175.5", "serienstream.to", "s.to", "serienstream.sx"),
        "AniWorld" to listOf("aniworld.to", "aniworld.sx"),
        "Frembed" to listOf("frembed.xyz", "frembed.cc"),
        "StreamingCommunity" to listOf("streamingunity.cc", "streamingcommunity.cz", "streamingcommunity.xyz"),
        "Cuevana" to listOf("cuevana.gs", "cuevana3.eu", "cuevana3.ch"),
        "Moflix" to listOf("moflix-stream.xyz", "moflix.to"),
        "PoseidonHD2" to listOf("www.poseidonhd2.co", "poseidonhd2.co"),
    )

    private val probeClient by lazy {
        NetworkClient.default.newBuilder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

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

    /**
     * Probe alternative domains and persist the first reachable one.
     */
    fun tryFallbackDomain(providerName: String): Boolean {
        val alternatives = getAlternativeDomains(providerName)
        if (alternatives.isEmpty()) return false

        val current = getCurrentDomain(providerName)
        for (domain in alternatives) {
            if (!domain.isNullOrBlank() && current?.contains(domain, ignoreCase = true) == true) continue
            val baseUrl = if (domain.startsWith("http")) domain else "https://$domain"
            val reachable = runCatching {
                probeClient.newCall(
                    Request.Builder().url(baseUrl).head().build(),
                ).execute().use { response ->
                    response.isSuccessful || response.code in 300..399
                }
            }.getOrDefault(false)
            if (reachable) {
                Log.i(TAG, "Fallback domain for $providerName -> $domain")
                updateDomain(providerName, domain)
                return true
            }
        }
        return false
    }
}
