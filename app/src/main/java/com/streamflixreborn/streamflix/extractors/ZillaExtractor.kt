package com.streamflixreborn.streamflix.extractors

import com.streamflixreborn.streamflix.models.Video
import androidx.media3.common.MimeTypes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class ZillaExtractor : Extractor() {

    override val name = "Zilla"
    override val mainUrl = "https://player.zilla-networks.com"

    companion object {
        private val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    override suspend fun extract(link: String): Video {
        try {
            val id = link.substringAfterLast("/").substringBefore("?").ifBlank {
                throw Exception("Missing Zilla media id")
            }
            val playlistUrl = "$mainUrl/m3u8/$id"
            val headers = mapOf(
                "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64; rv:148.0) Gecko/20100101 Firefox/148.0",
                "Accept" to "*/*",
                "Accept-Language" to "es-MX,es-ES;q=0.9,es;q=0.8,en-US;q=0.7,en;q=0.6",
                "Referer" to link,
                "Origin" to mainUrl,
                "Connection" to "keep-alive",
            )

            // Fail fast when Cloudflare blocks media segments so the player can
            // advance to the next AnimeAV1 server instead of stalling on HLS.
            ensureSegmentsReachable(playlistUrl, headers)

            return Video(
                source = playlistUrl,
                type = MimeTypes.APPLICATION_M3U8,
                headers = headers,
            )
        } catch (e: Exception) {
            throw Exception("ZillaExtractor failed: ${e.message}", e)
        }
    }

    private suspend fun ensureSegmentsReachable(
        playlistUrl: String,
        headers: Map<String, String>,
    ) = withContext(Dispatchers.IO) {
        val playlist = executeGet(playlistUrl, headers)
            ?: throw Exception("Unable to load Zilla playlist")
        if (playlist.contains("Attention Required", ignoreCase = true) ||
            playlist.contains("<html", ignoreCase = true)
        ) {
            throw Exception("Zilla playlist blocked by Cloudflare")
        }

        val initUri = Regex("""#EXT-X-MAP:URI="([^"]+)"""")
            .find(playlist)
            ?.groupValues
            ?.getOrNull(1)
        val firstSegment = playlist
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() && !it.startsWith("#") }

        val probeUrl = when {
            !initUri.isNullOrBlank() -> resolveUrl(playlistUrl, initUri)
            !firstSegment.isNullOrBlank() -> resolveUrl(playlistUrl, firstSegment)
            else -> null
        } ?: return@withContext

        val probe = executeGetBytes(probeUrl, headers)
            ?: throw Exception("Unable to fetch Zilla media segment")
        val prefix = probe.decodeToString()
        if (prefix.contains("Attention Required", ignoreCase = true) ||
            prefix.contains("<html", ignoreCase = true) ||
            prefix.startsWith("<!DOCTYPE", ignoreCase = true)
        ) {
            throw Exception("Zilla media segments blocked by Cloudflare")
        }
    }

    private fun executeGet(url: String, headers: Map<String, String>): String? {
        val request = Request.Builder().url(url).apply {
            headers.forEach { (k, v) -> header(k, v) }
        }.build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body?.string()
        }
    }

    private fun executeGetBytes(url: String, headers: Map<String, String>): ByteArray? {
        val request = Request.Builder().url(url).apply {
            headers.forEach { (k, v) -> header(k, v) }
        }.build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val stream = response.body?.byteStream() ?: return null
            val buffer = ByteArray(512)
            val read = stream.read(buffer)
            if (read <= 0) null else buffer.copyOf(read)
        }
    }

    private fun resolveUrl(base: String, relative: String): String {
        return if (relative.startsWith("http://") || relative.startsWith("https://")) {
            relative
        } else {
            java.net.URI(base).resolve(relative).toString()
        }
    }
}
