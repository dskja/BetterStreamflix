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
        "voe.sx", "voe-unblock",
        "streamtape.com",
        "dood.watch", "doodstream",
    )

    fun activeService(): DebridService? {
        if (!runCatching { UserPreferences.debridEnabled }.getOrDefault(false)) return null
        return when (DebridProviderId.fromId(runCatching { UserPreferences.debridProvider }.getOrNull())) {
            DebridProviderId.REAL_DEBRID -> {
                val token = runCatching { UserPreferences.realDebridToken }.getOrDefault("")
                if (token.isBlank()) null else RealDebridClient()
            }
            DebridProviderId.PREMIUMIZE -> {
                val key = runCatching { UserPreferences.premiumizeApiKey }.getOrDefault("")
                if (key.isBlank()) null else PremiumizeClient()
            }
            DebridProviderId.ALLDEBRID -> {
                val key = runCatching { UserPreferences.allDebridApiKey }.getOrDefault("")
                if (key.isBlank()) null else AllDebridClient()
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

    fun looksLikeHosterOrMagnet(link: String): Boolean = runCatching {
        val enabled = UserPreferences.debridEnabled
        val configured = activeService() != null
        if (link.startsWith("magnet:", ignoreCase = true)) {
            return@runCatching enabled && configured
        }
        if (!enabled || !configured) return@runCatching false
        val host = java.net.URI(link).host?.lowercase() ?: return@runCatching false
        KNOWN_HOSTERS.any { host == it || host.endsWith(".$it") || host.contains(it) }
    }.getOrDefault(false)
}
