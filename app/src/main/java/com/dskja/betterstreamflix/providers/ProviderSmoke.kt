package com.dskja.betterstreamflix.providers

import android.util.Log
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap

/**
 * Lightweight smoke harness: validates registry integrity and wraps provider calls
 * with timeouts so a hung getHome()/getServers() cannot brick the UI forever.
 */
object ProviderSmoke {
    private const val TAG = "ProviderSmoke"
    const val HOME_TIMEOUT_MS = 25_000L
    const val SERVERS_TIMEOUT_MS = 20_000L

    data class RegistryReport(
        val totalProviders: Int,
        val duplicateNames: List<String>,
        val missingSmokeTargets: List<String>,
        val quarantinedCount: Int,
    ) {
        val ok: Boolean get() = duplicateNames.isEmpty()
    }

    fun validateRegistry(): RegistryReport {
        val names = Provider.providers.keys.map { it.name }
        val duplicates = names.groupingBy { it }.eachCount().filter { it.value > 1 }.keys.toList()
        val known = names.toSet()
        val missing = ProviderHealth.topSmokeNames.filterNot { known.contains(it) }
        return RegistryReport(
            totalProviders = names.size,
            duplicateNames = duplicates,
            missingSmokeTargets = missing,
            quarantinedCount = ProviderHealth.quarantinedNames.size,
        )
    }

    private val lastHomeFailure = ConcurrentHashMap<String, Long>()

    fun noteHomeFailure(providerName: String) {
        lastHomeFailure[providerName] = System.currentTimeMillis()
    }

    fun lastFailureAt(providerName: String): Long? = lastHomeFailure[providerName]

    suspend fun <T> withProviderTimeout(
        timeoutMs: Long,
        label: String,
        block: suspend () -> T,
    ): T {
        return try {
            withTimeout(timeoutMs) { block() }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "$label timed out after ${timeoutMs}ms")
            throw e
        }
    }
}
