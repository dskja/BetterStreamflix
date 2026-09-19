package com.dskja.betterstreamflix.platform.debrid

/**
 * Unified debrid / premiumize-style resolve contract.
 */
interface DebridService {
    val name: String
    suspend fun isAuthenticated(): Boolean
    suspend fun unrestrict(link: String): DebridResult
    suspend fun resolveMagnet(magnet: String): DebridResult
}

sealed class DebridResult {
    data class Stream(val url: String, val headers: Map<String, String> = emptyMap()) : DebridResult()
    data class Pending(val id: String, val message: String = "Processing") : DebridResult()
    data class Failure(val reason: String) : DebridResult()
}
