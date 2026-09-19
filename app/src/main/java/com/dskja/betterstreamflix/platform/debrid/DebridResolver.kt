package com.dskja.betterstreamflix.platform.debrid

import com.dskja.betterstreamflix.utils.UserPreferences

enum class DebridProviderId(val id: String, val displayName: String) {
    REAL_DEBRID("realdebrid", "Real-Debrid"),
    PREMIUMIZE("premiumize", "Premiumize"),
    ALLDEBRID("alldebrid", "AllDebrid"),
    ;

    companion object {
        fun fromId(raw: String?): DebridProviderId =
            entries.firstOrNull { it.id.equals(raw, ignoreCase = true) } ?: REAL_DEBRID
    }
}

/**
 * Picks an active debrid backend and resolves hoster / magnet links.
 */
object DebridResolver {
    private val KNOWN_HOSTERS = setOf(
        "rapidgator.net", "rg.to",
        "uploaded.net", "ul.to", "uploaded.to",
        "nitroflare.com",
        "1fichier.com",
        "ddownload.com", "ddl.to",
        "keep2share.cc", "k2s.cc",
        "mediafire.com",
        "mega.nz",
        "zippyshare.com",
        "turbobit.net",
        "hitfile.net",
        "filefactory.com",
        "uptobox.com", "uptostream.com",
        "clicknupload",
        "katfile.com",
        "mexa.sh",
    )

    fun activeService(): DebridService? {
        if (!UserPreferences.debridEnabled) return null
        return when (DebridProviderId.fromId(UserPreferences.debridProvider)) {
            DebridProviderId.REAL_DEBRID -> {
                if (UserPreferences.realDebridToken.isBlank()) null else RealDebridClient()
            }
            DebridProviderId.PREMIUMIZE -> {
                if (UserPreferences.premiumizeApiKey.isBlank()) null else PremiumizeClient()
            }
            DebridProviderId.ALLDEBRID -> {
                if (UserPreferences.allDebridApiKey.isBlank()) null else AllDebridClient()
            }
        }
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
        val enabled = runCatching { UserPreferences.debridEnabled }.getOrDefault(false)
        if (!enabled) return false
        val host = runCatching { java.net.URI(link).host?.lowercase() }.getOrNull() ?: return false
        return KNOWN_HOSTERS.any { host == it || host.endsWith(".$it") || host.contains(it) }
    }
}
