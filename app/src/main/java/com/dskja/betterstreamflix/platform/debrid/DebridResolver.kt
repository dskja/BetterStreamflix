package com.dskja.betterstreamflix.platform.debrid

import com.dskja.betterstreamflix.utils.UserPreferences

/**
 * Picks an active debrid backend and resolves hoster / magnet links.
 */
object DebridResolver {
    fun activeService(): DebridService? {
        if (!UserPreferences.debridEnabled) return null
        val token = UserPreferences.realDebridToken.trim()
        if (token.isEmpty()) return null
        return RealDebridClient()
    }

    suspend fun resolve(link: String): DebridResult {
        val service = activeService()
            ?: return DebridResult.Failure("Debrid disabled or not configured")
        return if (link.startsWith("magnet:", ignoreCase = true)) {
            service.resolveMagnet(link)
        } else {
            service.unrestrict(link)
        }
    }

    fun looksLikeHosterOrMagnet(link: String): Boolean {
        if (link.startsWith("magnet:", ignoreCase = true)) return true
        val host = runCatching { java.net.URI(link).host?.lowercase() }.getOrNull() ?: return false
        return host.contains("rapidgator") ||
            host.contains("uploaded") ||
            host.contains("nitroflare") ||
            host.contains("1fichier") ||
            host.contains("ddl") ||
            host.contains("file")
    }
}
