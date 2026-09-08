package com.streamflixreborn.streamflix.utils

import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Tiny HTTP landing page for TV SerienStream captcha bypass.
 *
 * Phone cameras often cannot open `streamflix://` deep links (they search Google instead).
 * Encoding an `http://TV-IP:port/resolve?...` URL in the QR lets the camera open a page that
 * bridges into the StreamFlix app and explains the in-app scanner fallback.
 */
class BypassHttpLandingServer(
    port: Int,
) : NanoHTTPD(port) {

    @Volatile
    private var deepLinkTemplate: String? = null

    fun setDeepLink(deepLink: String) {
        deepLinkTemplate = deepLink
    }

    override fun serve(session: IHTTPSession): Response {
        return try {
            when {
                session.uri == "/resolve" || session.uri.startsWith("/resolve") -> {
                    val params = session.parms
                    val token = params["token"].orEmpty()
                    val ws = params["ws"].orEmpty()
                    val deepLink = deepLinkTemplate
                        ?: buildDeepLink(ws, token)
                    htmlResponse(landingHtml(deepLink, ws, token))
                }
                else -> htmlResponse(landingHtml(deepLinkTemplate.orEmpty(), "", ""))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to serve bypass landing page", e)
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                MIME_PLAINTEXT,
                "Bypass landing page error",
            )
        }
    }

    private fun buildDeepLink(ws: String, token: String): String {
        val encodedWs = URLEncoder.encode(ws, StandardCharsets.UTF_8.name())
        val encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8.name())
        return "streamflix://resolve?ws=$encodedWs&token=$encodedToken"
    }

    private fun htmlResponse(body: String): Response {
        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", body)
    }

    private fun landingHtml(deepLink: String, ws: String, token: String): String {
        val safeDeepLink = deepLink
            .replace("&", "&amp;")
            .replace("\"", "&quot;")
        val jsDeepLink = deepLink
            .replace("\\", "\\\\")
            .replace("'", "\\'")
        return """
            <!DOCTYPE html>
            <html lang="de">
            <head>
              <meta charset="utf-8"/>
              <meta name="viewport" content="width=device-width, initial-scale=1"/>
              <meta http-equiv="refresh" content="0;url=$safeDeepLink"/>
              <title>StreamFlix TV Bypass</title>
              <style>
                body{font-family:-apple-system,BlinkMacSystemFont,Segoe UI,Roboto,sans-serif;margin:0;padding:24px;background:#111;color:#f5f5f5;line-height:1.45}
                .card{max-width:520px;margin:0 auto;background:#1c1c1c;border-radius:16px;padding:20px}
                h1{font-size:1.35rem;margin:0 0 12px}
                p{margin:0 0 12px;color:#ddd}
                ol{margin:0 0 16px 18px;padding:0}
                li{margin:0 0 8px}
                a.button{display:block;text-align:center;background:#e50914;color:#fff;text-decoration:none;padding:14px 16px;border-radius:10px;font-weight:700}
                .muted{color:#9a9a9a;font-size:.92rem}
                code{word-break:break-all;font-size:.8rem;color:#bbb}
              </style>
            </head>
            <body>
              <div class="card">
                <h1>StreamFlix TV-Captcha</h1>
                <p><strong>DE:</strong> Dies ist kein SerienStream-Link. Öffne die Captcha-Seite in der <strong>StreamFlix-App</strong>.</p>
                <ol>
                  <li>Tippe unten auf „In StreamFlix öffnen“.</li>
                  <li>Oder in der App: Einstellungen → <strong>TV-Bypass-QR scannen</strong>.</li>
                  <li>Captcha lösen → <strong>Weiter</strong>. Der QR auf dem TV schließt sich automatisch.</li>
                </ol>
                <p class="muted"><strong>EN:</strong> Do not use a normal camera search. Open StreamFlix on this phone, then use Settings → Scan TV bypass QR if the button below does nothing.</p>
                <p><a class="button" href="$safeDeepLink">In StreamFlix öffnen / Open in StreamFlix</a></p>
                <p class="muted">Deep link:<br/><code>$safeDeepLink</code></p>
              </div>
              <script>
                try { window.location.href = '$jsDeepLink'; } catch (e) {}
              </script>
            </body>
            </html>
        """.trimIndent()
    }

    companion object {
        private const val TAG = "BypassHttpLanding"
    }
}
