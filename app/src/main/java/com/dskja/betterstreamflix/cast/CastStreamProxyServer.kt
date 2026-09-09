package com.dskja.betterstreamflix.cast

import android.util.Log
import com.dskja.betterstreamflix.utils.BypassWebSocketEndpointHelper
import fi.iki.elonen.NanoHTTPD
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.internal.userAgent
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * LAN-facing stream proxy so Chromecast can fetch URLs that require custom headers
 * (Referer, Cookie, User-Agent, tokens) which the default Cast receiver cannot send.
 *
 * Chromecast cannot reach the phone's 127.0.0.1 — we advertise the device LAN IP.
 */
class CastStreamProxyServer(
    private val httpClient: OkHttpClient = defaultClient(),
) : NanoHTTPD(0) {

    @Volatile
    private var defaultHeaders: Map<String, String> = emptyMap()

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
                session.uri == "/p" || session.uri.startsWith("/p") -> proxy(session)
                session.uri == "/health" -> newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "ok")
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

        val bytes = body?.bytes() ?: ByteArray(0)
        val rewritten = maybeRewritePlaylist(target, contentType, bytes)
        val response = newFixedLengthResponse(
            Response.Status.lookup(upstream.code) ?: Response.Status.OK,
            contentType,
            ByteArrayInputStream(rewritten),
            rewritten.size.toLong(),
        )
        upstream.header("Accept-Ranges")?.let { response.addHeader("Accept-Ranges", it) }
        upstream.header("Content-Range")?.let { response.addHeader("Content-Range", it) }
        response.addHeader("Access-Control-Allow-Origin", "*")
        return response
    }

    /**
     * Rewrite HLS playlists so relative segment / variant URIs also go through this proxy
     * (and therefore carry auth headers).
     */
    private fun maybeRewritePlaylist(playlistUrl: String, contentType: String, bytes: ByteArray): ByteArray {
        val looksLikePlaylist = contentType.contains("mpegurl", ignoreCase = true) ||
            contentType.contains("m3u8", ignoreCase = true) ||
            playlistUrl.substringBefore('?').endsWith(".m3u8", ignoreCase = true)
        if (!looksLikePlaylist) return bytes

        val text = runCatching { String(bytes, StandardCharsets.UTF_8) }.getOrNull() ?: return bytes
        if (!text.contains("#EXTM3U")) return bytes

        val base = publicBaseUrl() ?: return bytes
        val rewritten = text.lineSequence().joinToString("\n") { line ->
            val trimmed = line.trim()
            when {
                trimmed.isEmpty() || trimmed.startsWith("#") -> line
                else -> {
                    val absolute = resolveAgainst(playlistUrl, trimmed)
                    val encoded = URLEncoder.encode(absolute, StandardCharsets.UTF_8.name())
                    "$base/p?u=$encoded"
                }
            }
        }
        return rewritten.toByteArray(StandardCharsets.UTF_8)
    }

    private fun resolveAgainst(baseUrl: String, ref: String): String {
        if (ref.startsWith("http://") || ref.startsWith("https://")) return ref
        return runCatching { URI(baseUrl).resolve(ref).toString() }.getOrDefault(ref)
    }

    companion object {
        private const val TAG = "CastStreamProxy"

        private fun defaultClient(): OkHttpClient =
            OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(45, TimeUnit.SECONDS)
                .build()
    }
}
