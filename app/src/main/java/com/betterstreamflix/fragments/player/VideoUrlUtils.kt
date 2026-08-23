package com.betterstreamflix.fragments.player

import java.util.Base64

/**
 * Utility for decoding base64 URIs and extracting URLs from playlists.
 * Extracted from PlayerMobileFragment for shared use between Mobile and TV.
 */
object VideoUrlUtils {

    /**
     * Decode a base64-encoded data URI (e.g. "data:application/x-mpegURL;base64,XXXX").
     * Returns the decoded string, or null if the input is not a base64 data URI.
     */
    fun decodeBase64Uri(uri: String): String? {
        return try {
            val parts = uri.split(",")
            if (parts.size == 2 && parts[0].contains(";base64")) {
                val base64Data = parts[1]
                val decodedBytes = Base64.getDecoder().decode(base64Data)
                String(decodedBytes, Charsets.UTF_8)
            } else {
                null
            }
        } catch (ignored: Exception) {
            null
        }
    }

    /**
     * Extract the first HTTP URL from an HLS playlist string.
     * Looks for lines starting with "http" or URI="http..." patterns.
     */
    fun extractUrlFromPlaylist(playlist: String): String? {
        return try {
            val lines = playlist.lines().map { it.trim() }
            lines.firstOrNull { it.startsWith("http") }
                ?: lines.firstNotNullOfOrNull { line ->
                    val regex = """URI=["'](http[^"']+)["']""".toRegex()
                    regex.find(line)?.groupValues?.get(1)
                }
        } catch (ignored: Exception) {
            null
        }
    }
}
