package com.dskja.betterstreamflix.providers

import java.util.Locale

/**
 * SerienStream host / proxy endpoint helpers (pure JVM — no Android APIs).
 *
 * Official CUII bypass from [serien.domains](https://serien.domains):
 * `http://186.2.175.5/` (HTTP only). Hostname mirrors need HTTPS.
 */
object SerienStreamEndpoints {

    /** Official serien.domains CUII proxy IP (HTTP only). */
    const val PROXY_HOST = "186.2.175.5"

    const val DEFAULT_HOST = PROXY_HOST

    val FALLBACK_HOSTS: List<String> = listOf(
        PROXY_HOST,
        "serienstream.to",
        "serienstream.cx",
    )

    private val ipv4Pattern = Regex("""^\d{1,3}(?:\.\d{1,3}){3}$""")

    fun normalizeHost(raw: String): String {
        return raw.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore("/")
            .removePrefix("www.")
            .trimEnd('.')
            .lowercase(Locale.ROOT)
            .ifBlank { DEFAULT_HOST }
    }

    fun isProxyHost(host: String?): Boolean {
        val h = host?.lowercase(Locale.ROOT)?.removePrefix("www.") ?: return false
        if (h == PROXY_HOST) return true
        return ipv4Pattern.matches(h)
    }

    fun schemeFor(host: String): String =
        if (isProxyHost(host)) "http" else "https"

    fun originFor(domain: String): String {
        val host = normalizeHost(domain)
        return "${schemeFor(host)}://$host/"
    }

    fun candidateHosts(configured: String): List<String> {
        val preferred = normalizeHost(configured)
        return linkedSetOf(preferred).apply { addAll(FALLBACK_HOSTS) }.toList()
    }

    fun isKnownHost(hostOrUrl: String?): Boolean {
        if (hostOrUrl.isNullOrBlank()) return false
        val host = runCatching {
            if (hostOrUrl.contains("://")) {
                // Avoid android.net.Uri — keep this JVM-safe for unit tests.
                hostOrUrl.substringAfter("://").substringBefore('/').substringBefore('?')
            } else {
                hostOrUrl
            }
        }.getOrNull()
            ?.lowercase(Locale.ROOT)
            ?.removePrefix("www.")
            .orEmpty()
        if (host.isBlank()) return false
        if (host == "challenges.cloudflare.com") return true
        if (host == PROXY_HOST) return true
        val known = FALLBACK_HOSTS.toSet()
        return known.any { host == it || host.endsWith(".$it") }
    }
}
