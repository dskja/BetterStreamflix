package com.betterstreamflix.providers

import android.util.Log
import com.betterstreamflix.StreamFlixApp
import com.betterstreamflix.utils.NetworkClient
import com.betterstreamflix.utils.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun normalizeHost(domain: String): String =
        domain.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .trimEnd('/')

    /**
     * Update a provider's domain in the correct UserPreferences field and refresh services.
     */
    fun updateDomain(providerName: String, newDomain: String) {
        val host = normalizeHost(newDomain)
        if (host.isBlank()) return

        val provider = Provider.findByName(providerName)
        if (provider != null) {
            UserPreferences.setProviderCache(provider, "url", host)
        }

        when (providerName) {
            "SerienStream" -> UserPreferences.serienstreamDomain = host
            "AniWorld" -> UserPreferences.aniworldDomain = host
            "StreamingCommunity", "StreamingCommunity (EN)" -> UserPreferences.streamingcommunityDomain = host
            "Cuevana" -> UserPreferences.cuevanaDomain = host
            "Moflix" -> UserPreferences.moflixDomain = host
            "PoseidonHD2" -> UserPreferences.poseidonDomain = host
            else -> Log.w(TAG, "No dedicated domain field for $providerName; cache only")
        }

        scope.launch {
            runCatching {
                when (providerName) {
                    "SerienStream" -> SerienStreamProvider.initialize(StreamFlixApp.instance)
                    "AniWorld" -> AniWorldProvider.initialize(StreamFlixApp.instance)
                    "StreamingCommunity", "StreamingCommunity (EN)" -> {
                        Provider.providers.keys
                            .filterIsInstance<StreamingCommunityProvider>()
                            .forEach { it.rebuildService(host) }
                    }
                    else -> Unit
                }
            }.onFailure { e ->
                Log.e(TAG, "Failed to rebuild service for $providerName", e)
            }
        }
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
