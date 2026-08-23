package com.betterstreamflix.providers

import com.betterstreamflix.adapters.AppAdapter
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Global search aggregator — searches across multiple providers in parallel.
 */
object GlobalSearchAggregator {

    /**
     * Search across all enabled providers in parallel.
     * Returns results grouped by provider.
     */
    suspend fun searchGlobal(
        query: String,
        currentLanguage: String,
        timeoutMs: Long = 15000L,
    ): List<GlobalSearchResult> {
        val providers = Provider.providers.keys.filter { it.language == currentLanguage }
            .filter { ProviderHealthMonitor.isHealthy(it.name) }

        val deferredResults: List<Deferred<GlobalSearchResult>> = coroutineScope {
            val scope = this
            providers.map { provider ->
                scope.async {
                    try {
                        val searchResults = provider.search(query)
                        ProviderHealthMonitor.recordSuccess(provider.name)
                        GlobalSearchResult(
                            providerName = provider.name,
                            results = searchResults,
                            success = true,
                        )
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        ProviderHealthMonitor.recordFailure(provider.name, e.message ?: "Search failed")
                        GlobalSearchResult(
                            providerName = provider.name,
                            results = emptyList(),
                            success = false,
                            error = e.message,
                        )
                    }
                }
            }
        }

        val results: List<GlobalSearchResult> = withTimeoutOrNull(timeoutMs) {
            deferredResults.map { it.await() }
        } ?: deferredResults.mapNotNull { if (it.isCompleted) it.getCompleted() else null }

        return results.filter { result -> result.success && result.results.isNotEmpty() }
    }
}

/**
 * Search result from a single provider in a global search.
 */
data class GlobalSearchResult(
    val providerName: String,
    val results: List<AppAdapter.Item>,
    val success: Boolean,
    val error: String? = null,
)
