package com.dskja.betterstreamflix.utils

import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.util.Locale

/**
 * Bridges app DoH (OkHttp [NetworkClient]) into WebView main-document loads.
 *
 * German ISP DNS (CUII) sinkholes SerienStream/AniWorld to a copyright block page
 * while Cloudflare DoH still reaches the real origin. WebView uses OS DNS by
 * default — this interceptor fetches the main HTML via OkHttp so login/import work.
 */
object WebViewDohBridge {
    private const val TAG = "WebViewDohBridge"

    private val bridgedHosts = setOf(
        "serienstream.to",
        "serienstream.cx",
        "aniworld.to",
        "s.to",
    )

    fun shouldBridge(host: String?): Boolean {
        val h = host?.lowercase(Locale.ROOT)?.removePrefix("www.") ?: return false
        return bridgedHosts.any { h == it || h.endsWith(".$it") }
    }

    fun isCopyrightBlockPage(html: String?): Boolean {
        if (html.isNullOrBlank()) return false
        val lower = html.lowercase(Locale.ROOT)
        return lower.contains("urheberrechtlichen") ||
            lower.contains("urheberrechtlich") && lower.contains("nicht verfügbar") ||
            lower.contains("aus urheberrechtlichengründen") ||
            (lower.contains("cuii") && lower.contains("nicht verfügbar"))
    }

    /**
     * Fetch [url] via OkHttp+DoH and return a WebResourceResponse, or null to
     * fall through to the default WebView loader.
     */
    fun interceptMainDocument(
        request: WebResourceRequest?,
        userAgent: String,
    ): WebResourceResponse? {
        if (request == null || !request.isForMainFrame) return null
        val url = request.url ?: return null
        if (url.scheme != "http" && url.scheme != "https") return null
        if (!shouldBridge(url.host)) return null

        return try {
            val builder = Request.Builder()
                .url(url.toString())
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "de-DE,de;q=0.9,en-US;q=0.8,en;q=0.7")
                .get()
            // Forward cookies the WebView already has for this host.
            val cookie = android.webkit.CookieManager.getInstance()
                .getCookie(url.toString())
            if (!cookie.isNullOrBlank()) {
                builder.header("Cookie", cookie)
            }
            val response = NetworkClient.default.newCall(builder.build()).execute()
            val bodyBytes = response.body?.bytes() ?: ByteArray(0)
            val mime = response.header("Content-Type")
                ?.substringBefore(';')
                ?.trim()
                ?.ifBlank { null }
                ?: "text/html"
            val encoding = response.header("Content-Type")
                ?.substringAfter("charset=", "")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: "utf-8"

            // Persist Set-Cookie back into the WebView jar.
            response.headers("Set-Cookie").forEach { setCookie ->
                android.webkit.CookieManager.getInstance().setCookie(url.toString(), setCookie)
            }
            android.webkit.CookieManager.getInstance().flush()

            val headerMap = mutableMapOf<String, String>()
            response.headers.forEach { (name, value) ->
                if (!name.equals("content-encoding", true) &&
                    !name.equals("content-length", true) &&
                    !name.equals("transfer-encoding", true)
                ) {
                    headerMap[name] = value
                }
            }

            WebResourceResponse(
                mime,
                encoding,
                response.code,
                response.message.ifBlank { "OK" },
                headerMap,
                ByteArrayInputStream(bodyBytes),
            )
        } catch (e: Exception) {
            Log.w(TAG, "DoH bridge failed for ${url}: ${e.message}")
            null
        }
    }
}
