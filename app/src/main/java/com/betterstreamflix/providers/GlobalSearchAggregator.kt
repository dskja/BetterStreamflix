package com.betterstreamflix.providers

import com.betterstreamflix.adapters.AppAdapter

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

        val results = kotlinx.coroutines.coroutineScope {
            providers.map { provider ->
                kotlinx.coroutines.async {
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
            }.awaitAllWithTimeout(timeoutMs)
        }

        return results.filter { it.success && it.results.isNotEmpty() }
    }

    /**
     * Await all deferreds with a combined timeout, returning partial results.
     */
    private suspend fun <T> List<kotlinx.coroutines.Deferred<T>>.awaitAllWithTimeout(
        timeoutMs: Long,
    ): List<T> {
        return kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
            this@awaitAllWithTimeout.map { deferred -> deferred.await() }
        } ?: this@awaitAllWithTimeout.mapNotNull { deferred ->
            if (deferred.isCompleted) deferred.getCompleted() else null
        }
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
