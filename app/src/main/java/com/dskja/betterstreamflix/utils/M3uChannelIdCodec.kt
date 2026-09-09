package com.dskja.betterstreamflix.utils

import android.util.Base64

/**
 * Encodes IPTV channel metadata into Base64 IDs without using `|` as a field
 * separator — channel titles often contain `|` (e.g. Sports Events "Team A | English").
 *
 * Also decodes legacy `|`-joined IDs with a heuristic so existing favorites keep working.
 */
object M3uChannelIdCodec {
    private const val SEP = "\u001F"

    data class Payload(
        val url: String,
        val name: String,
        val logo: String = "",
        val userAgent: String? = null,
        val referrer: String? = null,
        val origin: String? = null,
    )

    fun encode(
        url: String,
        name: String,
        logo: String? = null,
        userAgent: String? = null,
        referrer: String? = null,
        origin: String? = null,
    ): String {
        val raw = listOf(
            url,
            name,
            logo.orEmpty(),
            userAgent.orEmpty(),
            referrer.orEmpty(),
            origin.orEmpty(),
        ).joinToString(SEP)
        return Base64.encodeToString(raw.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
    }

    fun decode(id: String): Payload {
        if (id.isBlank()) return Payload(url = id, name = "Unknown")
        return try {
            val raw = String(Base64.decode(id, Base64.DEFAULT), Charsets.UTF_8)
            if (raw.contains(SEP)) {
                val parts = raw.split(SEP)
                Payload(
                    url = parts.getOrNull(0).orEmpty().ifBlank { id },
                    name = parts.getOrNull(1).orEmpty().ifBlank { "Unknown" },
                    logo = parts.getOrNull(2).orEmpty(),
                    userAgent = parts.getOrNull(3)?.takeIf { it.isNotBlank() },
                    referrer = parts.getOrNull(4)?.takeIf { it.isNotBlank() },
                    origin = parts.getOrNull(5)?.takeIf { it.isNotBlank() },
                )
            } else {
                decodeLegacyPipe(raw, fallbackId = id)
            }
        } catch (_: Exception) {
            Payload(url = id, name = "Unknown")
        }
    }

    fun playbackHeaders(id: String): Map<String, String> {
        val payload = decode(id)
        return buildMap {
            payload.userAgent?.let { put("User-Agent", it) }
            payload.referrer?.let { put("Referer", it) }
            payload.origin?.let { put("Origin", it) }
        }
    }

    private fun decodeLegacyPipe(raw: String, fallbackId: String): Payload {
        val parts = raw.split("|")
        if (parts.isEmpty()) return Payload(url = fallbackId, name = "Unknown")
        val url = parts[0].ifBlank { fallbackId }
        if (parts.size == 1) return Payload(url = url, name = "Unknown")

        var end = parts.lastIndex
        var referrer: String? = null
        var userAgent: String? = null
        var origin: String? = null
        var logo = ""

        fun looksLikeUrl(value: String): Boolean =
            value.startsWith("http://", ignoreCase = true) ||
                value.startsWith("https://", ignoreCase = true)

        fun looksLikeUa(value: String): Boolean =
            value.contains("Mozilla", ignoreCase = true) ||
                value.contains("VLC", ignoreCase = true) ||
                value.contains("AppleWebKit", ignoreCase = true)

        // Prefer peeling known trailer fields from the end when titles contain `|`.
        if (end > 0 && looksLikeUrl(parts[end])) {
            referrer = parts[end]
            end--
        }
        if (end > 0 && looksLikeUa(parts[end])) {
            userAgent = parts[end]
            end--
        }
        if (end > 0 && (parts[end].isEmpty() || looksLikeUrl(parts[end]))) {
            logo = parts[end]
            end--
        }
        // Optional trailing origin in some hand-built IDs.
        if (end > 0 && looksLikeUrl(parts[end]) && referrer != null && parts[end] != logo) {
            // Keep as part of name if already assigned logo; otherwise treat as origin only
            // when we still have room for a name field.
        }

        val name = if (end >= 1) {
            parts.subList(1, end + 1).joinToString("|").ifBlank { "Unknown" }
        } else {
            "Unknown"
        }
        // Silence unused when heuristic doesn't need origin from legacy.
        origin = null
        return Payload(
            url = url,
            name = name,
            logo = logo,
            userAgent = userAgent,
            referrer = referrer,
            origin = origin,
        )
    }
}
