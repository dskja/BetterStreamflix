package com.dskja.betterstreamflix.cast

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Pure HLS playlist rewriter for Cast proxy (unit-testable without NanoHTTPD).
 */
object CastPlaylistRewriter {
    private val URI_ATTR_REGEX = Regex("""URI="([^"]+)"""", RegexOption.IGNORE_CASE)

    /**
     * Rewrites absolute and relative segment / URI= references to flow through [proxyBase]/p?u=…
     */
    fun rewrite(playlistText: String, playlistUrl: String, proxyBase: String): String {
        if (!playlistText.contains("#EXTM3U")) return playlistText
        val base = proxyBase.trimEnd('/')
        return playlistText.lineSequence().joinToString("\n") { line ->
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() || trimmed.startsWith("#") -> rewriteTagUris(line, playlistUrl, base)
                else -> {
                    val absolute = resolveAgainst(playlistUrl, trimmed)
                    val encoded = URLEncoder.encode(absolute, StandardCharsets.UTF_8.name())
                    "$base/p?u=$encoded"
                }
            }
        }
    }

    fun resolveAgainst(baseUrl: String, ref: String): String {
        if (ref.startsWith("http://") || ref.startsWith("https://")) return ref
        return runCatching { URI(baseUrl).resolve(ref).toString() }.getOrDefault(ref)
    }

    private fun rewriteTagUris(line: String, playlistUrl: String, base: String): String {
        if (!line.contains("URI=", ignoreCase = true)) return line
        return URI_ATTR_REGEX.replace(line) { match ->
            val raw = match.groupValues[1]
            val absolute = resolveAgainst(playlistUrl, raw)
            val encoded = URLEncoder.encode(absolute, StandardCharsets.UTF_8.name())
            "URI=\"$base/p?u=$encoded\""
        }
    }
}
