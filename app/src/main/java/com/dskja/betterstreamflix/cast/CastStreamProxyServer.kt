package com.dskja.betterstreamflix.cast

import android.util.Log
import com.dskja.betterstreamflix.utils.BypassWebSocketEndpointHelper
import fi.iki.elonen.NanoHTTPD
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.internal.userAgent
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * LAN-facing stream proxy so Chromecast can fetch URLs that require custom headers
 * (Referer, Cookie, User-Agent, tokens) which the default Cast receiver cannot send.
 *
 * Chromecast cannot reach the phone's 127.0.0.1 — we advertise the device LAN IP.
 * Large media responses are streamed; HLS playlists are rewritten so variants/segments
 * also flow through this proxy.
 */
class CastStreamProxyServer(
    private val httpClient: OkHttpClient = defaultClient(),
) : NanoHTTPD(0) {

    @Volatile
    private var defaultHeaders: Map<String, String> = emptyMap()

    private val pumpExecutor = Executors.newCachedThreadPool()

    fun updateDefaultHeaders(headers: Map<String, String>) {
        defaultHeaders = headers
    }

    fun publicBaseUrl(): String? {
        val host = BypassWebSocketEndpointHelper.getLocalIpv4Address() ?: return null
        return "http://$host:$listeningPort"
    }

    /** Build a Cast-reachable URL that proxies [originalUrl] with the current headers. */
    fun wrap(originalUrl: String): String {
        val base = publicBaseUrl() ?: return originalUrl
        val encoded = URLEncoder.encode(originalUrl, StandardCharsets.UTF_8.name())
        return "$base/p?u=$encoded"
    }

    override fun serve(session: IHTTPSession): Response {
        return try {
            when {
                session.method == Method.OPTIONS -> {
                    newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "").also {
                        it.addHeader("Access-Control-Allow-Origin", "*")
                        it.addHeader("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS")
                        it.addHeader("Access-Control-Allow-Headers", "Range, Content-Type")
                    }
                }
                session.uri == "/health" ->
                    newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "ok")
                session.uri == "/p" || session.uri.startsWith("/p") -> proxy(session)
                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "not found")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cast proxy failed", e)
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                e.message ?: "proxy error",
            )
        }
    }

    private fun proxy(session: IHTTPSession): Response {
        val encoded = session.parms["u"].orEmpty()
        if (encoded.isBlank()) {
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "missing u")
        }
        val target = URLDecoder.decode(encoded, StandardCharsets.UTF_8.name())
        if (!target.startsWith("http://") && !target.startsWith("https://")) {
            return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "unsupported scheme")
        }

        val requestBuilder = Request.Builder().url(target).get()
        val mergedHeaders = linkedMapOf("User-Agent" to userAgent)
        mergedHeaders.putAll(defaultHeaders)
        session.headers["range"]?.let { mergedHeaders["Range"] = it }
        session.headers["accept"]?.let { mergedHeaders.putIfAbsent("Accept", it) }
        mergedHeaders.forEach { (key, value) ->
            if (key.equals("Host", ignoreCase = true)) return@forEach
            requestBuilder.header(key, value)
        }

        val upstream = httpClient.newCall(requestBuilder.build()).execute()
        val body = upstream.body
        val contentType = body?.contentType()?.toString()
            ?: upstream.header("Content-Type")
            ?: "application/octet-stream"

        if (!upstream.isSuccessful && body == null) {
            return newFixedLengthResponse(
                Response.Status.lookup(upstream.code) ?: Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                "upstream ${upstream.code}",
            )
        }

        val looksLikePlaylist = contentType.contains("mpegurl", ignoreCase = true) ||
            contentType.contains("m3u8", ignoreCase = true) ||
            target.substringBefore('?').endsWith(".m3u8", ignoreCase = true)

        if (looksLikePlaylist) {
            val bytes = body?.bytes() ?: ByteArray(0)
            val rewritten = maybeRewritePlaylist(target, contentType, bytes)
            val response = newFixedLengthResponse(
                Response.Status.lookup(upstream.code) ?: Response.Status.OK,
                contentType,
                rewritten.inputStream(),
                rewritten.size.toLong(),
            )
            decorate(response, upstream)
            return response
        }

        // Stream large media so Chromecast can start sooner and we avoid OOM.
        val contentLength = body?.contentLength() ?: upstream.header("Content-Length")?.toLongOrNull() ?: -1L
        val pipedIn = PipedInputStream(256 * 1024)
        val pipedOut = PipedOutputStream(pipedIn)
        pumpExecutor.execute {
            try {
                body?.byteStream()?.use { input ->
                    input.copyTo(pipedOut, 64 * 1024)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Cast proxy stream pump ended: ${e.message}")
            } finally {
                runCatching { pipedOut.close() }
                runCatching { upstream.close() }
            }
        }

        val response = newFixedLengthResponse(
            Response.Status.lookup(upstream.code) ?: Response.Status.OK,
            contentType,
            pipedIn,
            contentLength,
        )
        decorate(response, upstream)
        response.setChunkedTransfer(contentLength < 0)
        return response
    }

    private fun decorate(response: Response, upstream: okhttp3.Response) {
        upstream.header("Accept-Ranges")?.let { response.addHeader("Accept-Ranges", it) }
        upstream.header("Content-Range")?.let { response.addHeader("Content-Range", it) }
        upstream.header("Cache-Control")?.let { response.addHeader("Cache-Control", it) }
        response.addHeader("Access-Control-Allow-Origin", "*")
    }

    private fun maybeRewritePlaylist(playlistUrl: String, contentType: String, bytes: ByteArray): ByteArray {
        val text = runCatching { String(bytes, StandardCharsets.UTF_8) }.getOrNull() ?: return bytes
        if (!text.contains("#EXTM3U")) return bytes
        val base = publicBaseUrl() ?: return bytes
        val rewritten = text.lineSequence().joinToString("\n") { line ->
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() || trimmed.startsWith("#") -> rewritePlaylistTagUris(line, playlistUrl, base)
                else -> {
                    val absolute = resolveAgainst(playlistUrl, trimmed)
                    val encoded = URLEncoder.encode(absolute, StandardCharsets.UTF_8.name())
                    "$base/p?u=$encoded"
                }
            }
        }
        return rewritten.toByteArray(StandardCharsets.UTF_8)
    }

    private fun rewritePlaylistTagUris(line: String, playlistUrl: String, base: String): String {
        // Rewrite URI="..." attributes inside #EXT-X-KEY / #EXT-X-MAP / #EXT-X-MEDIA tags.
        if (!line.contains("URI=", ignoreCase = true)) return line
        return URI_ATTR_REGEX.replace(line) { match ->
            val raw = match.groupValues[1]
            val absolute = resolveAgainst(playlistUrl, raw)
            val encoded = URLEncoder.encode(absolute, StandardCharsets.UTF_8.name())
            "URI=\"$base/p?u=$encoded\""
        }
    }

    private fun resolveAgainst(baseUrl: String, ref: String): String {
        if (ref.startsWith("http://") || ref.startsWith("https://")) return ref
        return runCatching { URI(baseUrl).resolve(ref).toString() }.getOrDefault(ref)
    }

    override fun stop() {
        runCatching { super.stop() }
        runCatching { pumpExecutor.shutdownNow() }
    }

    companion object {
        private const val TAG = "CastStreamProxy"
        private val URI_ATTR_REGEX = Regex("""URI="([^"]+)"""", RegexOption.IGNORE_CASE)

        private fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)
                .build()
    }
}
