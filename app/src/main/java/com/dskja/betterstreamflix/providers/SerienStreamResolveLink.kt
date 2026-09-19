package com.dskja.betterstreamflix.providers

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Parse SerienStream / TV bypass resolve links from deep links and HTTP landing QR codes.
 *
 * Pure JVM (no Android APIs) so unit tests can cover the QR / deep-link matrix.
 *
 * Supported forms:
 * - `betterstreamflix://resolve?ws=…&token=…`
 * - `streamflix://resolve?ws=…&token=…`
 * - `http(s)://TV-IP:port/resolve?ws=…&token=…` (QR preferred by TV player)
 */
object SerienStreamResolveLink {

    data class Target(
        val ws: String,
        val token: String,
    ) {
        fun toDeepLink(scheme: String = "betterstreamflix"): String {
            val encodedWs = java.net.URLEncoder.encode(ws, StandardCharsets.UTF_8.name())
            val encodedToken = java.net.URLEncoder.encode(token, StandardCharsets.UTF_8.name())
            return "$scheme://resolve?ws=$encodedWs&token=$encodedToken"
        }
    }

    fun parse(raw: String?): Target? {
        val value = raw?.trim().orEmpty()
        if (value.isBlank()) return null

        val scheme = value.substringBefore("://", missingDelimiterValue = "")
            .lowercase(Locale.ROOT)
        if (scheme.isBlank()) return null

        val afterScheme = value.substringAfter("://", missingDelimiterValue = "")
        if (afterScheme.isBlank()) return null

        val authorityAndPath = afterScheme.substringBefore('?')
        val query = afterScheme.substringAfter('?', missingDelimiterValue = "")

        return when (scheme) {
            "betterstreamflix", "streamflix" -> {
                val host = authorityAndPath.substringBefore('/').substringBefore('?')
                if (!host.equals("resolve", ignoreCase = true)) return null
                fromQuery(query)
            }
            "http", "https" -> {
                val path = authorityAndPath.substringAfter('/', missingDelimiterValue = "")
                if (!path.contains("resolve", ignoreCase = true) &&
                    !authorityAndPath.contains("/resolve", ignoreCase = true)
                ) {
                    return null
                }
                fromQuery(query)
            }
            else -> null
        }
    }

    fun isResolveLink(raw: String?): Boolean = parse(raw) != null

    private fun fromQuery(query: String): Target? {
        if (query.isBlank()) return null
        val params = linkedMapOf<String, String>()
        query.split('&').forEach { part ->
            if (!part.contains('=')) return@forEach
            val name = decode(part.substringBefore('=')).lowercase(Locale.ROOT)
            val value = decode(part.substringAfter('='))
            if (name.isNotBlank()) params[name] = value
        }
        val ws = params["ws"]?.trim().orEmpty()
        val token = params["token"]?.trim().orEmpty()
        if (ws.isBlank() || token.isBlank()) return null
        return Target(ws = ws, token = token)
    }

    private fun decode(value: String): String {
        return runCatching {
            URLDecoder.decode(value, StandardCharsets.UTF_8.name())
        }.getOrDefault(value)
    }
}
