package com.dskja.betterstreamflix.providers

import android.util.Log
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Lightweight smoke harness: validates registry integrity and wraps provider calls
 * with timeouts so a hung getHome()/getServers() cannot brick the UI forever.
 *
 * Also tracks a short-lived home circuit breaker so chronically failing providers
 * prefer stale cache instead of hammering a dead origin every resume.
 */
object ProviderSmoke {
    private const val TAG = "ProviderSmoke"
    const val HOME_TIMEOUT_MS = 25_000L
    const val SERVERS_TIMEOUT_MS = 20_000L

    /** Open the circuit after this many failures inside [CIRCUIT_WINDOW_MS]. */
    const val CIRCUIT_FAILURE_THRESHOLD = 3
    const val CIRCUIT_WINDOW_MS = 10L * 60L * 1000L
    /** Keep the circuit open this long before allowing another live probe. */
    const val CIRCUIT_OPEN_MS = 2L * 60L * 1000L

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

    private data class FailureWindow(
        val count: AtomicInteger = AtomicInteger(0),
        @Volatile var windowStartMs: Long = 0L,
        @Volatile var lastFailureMs: Long = 0L,
        @Volatile var circuitOpenedAtMs: Long = 0L,
    )

    private val homeFailures = ConcurrentHashMap<String, FailureWindow>()

    fun noteHomeFailure(providerName: String) {
        val now = System.currentTimeMillis()
        val window = homeFailures.getOrPut(providerName) { FailureWindow() }
        synchronized(window) {
            if (window.windowStartMs == 0L || now - window.windowStartMs > CIRCUIT_WINDOW_MS) {
                window.windowStartMs = now
                window.count.set(1)
            } else {
                window.count.incrementAndGet()
            }
            window.lastFailureMs = now
            if (window.count.get() >= CIRCUIT_FAILURE_THRESHOLD) {
                window.circuitOpenedAtMs = now
                runCatching {
                    Log.w(TAG, "Home circuit OPEN for $providerName after ${window.count.get()} failures")
                }
            }
        }
    }

    fun noteHomeSuccess(providerName: String) {
        homeFailures.remove(providerName)
    }

    fun lastFailureAt(providerName: String): Long? =
        homeFailures[providerName]?.lastFailureMs?.takeIf { it > 0L }

    fun failureCount(providerName: String): Int =
        homeFailures[providerName]?.count?.get() ?: 0

    /**
     * When true, [com.dskja.betterstreamflix.fragments.home.HomeViewModel] should
     * skip the live [Provider.getHome] call and keep serving cache (if any).
     */
    fun isHomeCircuitOpen(providerName: String): Boolean {
        val window = homeFailures[providerName] ?: return false
        val opened = window.circuitOpenedAtMs
        if (opened <= 0L) return false
        val age = System.currentTimeMillis() - opened
        if (age > CIRCUIT_OPEN_MS) {
            // Half-open: allow one probe; leave failure count so another fail re-opens.
            window.circuitOpenedAtMs = 0L
            return false
        }
        return true
    }

    fun circuitHint(providerName: String): String? {
        if (!isHomeCircuitOpen(providerName)) return null
        return "Catalog paused for $providerName (recent failures) — showing cache"
    }

    suspend fun <T> withProviderTimeout(
        timeoutMs: Long,
        label: String,
        block: suspend () -> T,
    ): T {
        return try {
            withTimeout(timeoutMs) { block() }
        } catch (e: TimeoutCancellationException) {
            runCatching { Log.e(TAG, "$label timed out after ${timeoutMs}ms") }
            throw e
        }
    }
}
