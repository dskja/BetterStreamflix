package com.dskja.betterstreamflix.providers

import android.net.Uri
import android.util.Log
import android.webkit.CookieManager
import com.dskja.betterstreamflix.player.SerienStreamBypassHelper
import com.dskja.betterstreamflix.utils.NetworkClient
import com.dskja.betterstreamflix.utils.UserPreferences
import com.dskja.betterstreamflix.watchlist.WatchlistImporter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * First-class SerienStream account / CF session management.
 *
 * SerienStream has no OAuth API — sessions are cookie jars (`cf_clearance`,
 * `PHPSESSID`, …) captured from WebView login or TV phone-bypass. This manager
 * owns persist, seed, validate, logout, paste/export, and status snapshots used
 * by Settings and the players.
 */
object SerienStreamAuthManager {

    private const val TAG = "SerienStreamAuth"

    data class SessionSnapshot(
        val cookies: String,
        val isLoggedIn: Boolean,
        val cookieCount: Int,
        val authCookieNames: List<String>,
        val displayName: String?,
        val domain: String,
        val lastValidatedAtMs: Long?,
        val lastValidatedOk: Boolean?,
    )

    data class ValidationResult(
        val ok: Boolean,
        val displayName: String?,
        val challengeActive: Boolean,
        val redirectedToLogin: Boolean,
        val detail: String,
    )

    fun snapshot(): SessionSnapshot {
        val cookies = SerienStreamBypassHelper.sanitizeSessionCookies(
            UserPreferences.serienStreamSessionCookies,
        )
        val loggedIn = SerienStreamBypassHelper.looksLikeBypassSolved(cookies)
        val names = authCookieNames(cookies)
        return SessionSnapshot(
            cookies = cookies,
            isLoggedIn = loggedIn,
            cookieCount = names.size,
            authCookieNames = names,
            displayName = UserPreferences.serienStreamSessionDisplayName.takeIf { it.isNotBlank() },
            domain = SerienStreamEndpoints.normalizeHost(UserPreferences.serienstreamDomain),
            lastValidatedAtMs = UserPreferences.serienStreamSessionValidatedAtMs
                .takeIf { it > 0L },
            lastValidatedOk = when {
                UserPreferences.serienStreamSessionValidatedAtMs <= 0L -> null
                else -> UserPreferences.serienStreamSessionValidatedOk
            },
        )
    }

    fun isLoggedIn(): Boolean = snapshot().isLoggedIn

    /** Seed SharedPreferences cookies into CookieManager for all known SerienStream origins. */
    fun seedRuntimeCookies() {
        runCatching {
            SerienStreamBypassHelper.applyStoredSessionCookies()
        }.onFailure {
            Log.w(TAG, "Failed to seed SerienStream cookies: ${it.message}")
        }
    }

    fun persist(cookieHeader: String, displayName: String? = null): Boolean {
        val ok = SerienStreamBypassHelper.persistSessionCookiesIfValid(cookieHeader)
        if (!ok) return false
        if (!displayName.isNullOrBlank()) {
            UserPreferences.serienStreamSessionDisplayName = displayName.trim()
        }
        // Fresh persist — clear stale validation until next probe.
        UserPreferences.serienStreamSessionValidatedAtMs = 0L
        UserPreferences.serienStreamSessionValidatedOk = false
        seedRuntimeCookies()
        return true
    }

    fun pasteCookies(raw: String): Boolean {
        val cleaned = SerienStreamBypassHelper.sanitizeSessionCookies(raw)
        if (!SerienStreamBypassHelper.looksLikeBypassSolved(cleaned)) {
            return false
        }
        return persist(cleaned)
    }

    fun exportCookieHeader(): String {
        return SerienStreamBypassHelper.sanitizeSessionCookies(
            UserPreferences.serienStreamSessionCookies,
        )
    }

    /**
     * Full logout: prefs + CookieManager entries for every SerienStream / proxy origin.
     */
    fun logout() {
        val existing = exportCookieHeader()
        UserPreferences.serienStreamSessionCookies = ""
        UserPreferences.serienStreamSessionDisplayName = ""
        UserPreferences.serienStreamSessionValidatedAtMs = 0L
        UserPreferences.serienStreamSessionValidatedOk = false
        clearCookieManager(existing)
    }

    /**
     * Probe `/account` (and `/`) with the current cookie jar.
     * Updates validation timestamps and optional display name.
     */
    suspend fun validateSession(): ValidationResult = withContext(Dispatchers.IO) {
        val snap = snapshot()
        if (!snap.isLoggedIn) {
            return@withContext ValidationResult(
                ok = false,
                displayName = null,
                challengeActive = false,
                redirectedToLogin = true,
                detail = "not_signed_in",
            )
        }

        seedRuntimeCookies()
        val base = SerienStreamProvider.baseUrl.trimEnd('/')
        val clients = listOf(NetworkClient.default, NetworkClient.trustAll)
        val urls = listOf("$base/account", "$base/", "$base/account/watchlist")

        var lastDetail = "unreachable"
        var challenge = false
        var login = false
        var displayName: String? = snap.displayName

        for (client in clients) {
            for (url in urls) {
                val response = runCatching {
                    client.newCall(
                        Request.Builder()
                            .url(url)
                            .header(
                                "User-Agent",
                                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                                    "Chrome/124.0.0.0 Mobile Safari/537.36",
                            )
                            .header("Accept", "text/html,application/xhtml+xml")
                            .apply {
                                if (snap.cookies.isNotBlank()) {
                                    header("Cookie", snap.cookies)
                                }
                            }
                            .get()
                            .build(),
                    ).execute()
                }.getOrNull() ?: continue

                response.use { resp ->
                    val body = resp.body?.string().orEmpty()
                    val finalUrl = resp.request.url.toString()
                    if (WatchlistImporter.looksLikeChallengePage(body)) {
                        challenge = true
                        lastDetail = "challenge"
                        return@use
                    }
                    if (WatchlistImporter.looksLikeLoginPage(body, finalUrl)) {
                        login = true
                        lastDetail = "login"
                        return@use
                    }
                    if (resp.isSuccessful && body.isNotBlank()) {
                        val scraped = parseDisplayName(body)
                        if (!scraped.isNullOrBlank()) {
                            displayName = scraped
                            UserPreferences.serienStreamSessionDisplayName = scraped
                        }
                        UserPreferences.serienStreamSessionValidatedAtMs = System.currentTimeMillis()
                        UserPreferences.serienStreamSessionValidatedOk = true
                        return@withContext ValidationResult(
                            ok = true,
                            displayName = displayName,
                            challengeActive = false,
                            redirectedToLogin = false,
                            detail = "ok",
                        )
                    }
                    lastDetail = "http_${resp.code}"
                }
            }
        }

        UserPreferences.serienStreamSessionValidatedAtMs = System.currentTimeMillis()
        UserPreferences.serienStreamSessionValidatedOk = false
        ValidationResult(
            ok = false,
            displayName = displayName,
            challengeActive = challenge,
            redirectedToLogin = login,
            detail = lastDetail,
        )
    }

    fun parseDisplayName(html: String): String? {
        if (html.isBlank()) return null
        // Common SerienStream / AniWorld account chrome.
        val patterns = listOf(
            Regex("""class=["'][^"']*user-name[^"']*["'][^>]*>\s*([^<]{2,64})\s*<""", RegexOption.IGNORE_CASE),
            Regex("""id=["']user["'][^>]*>\s*([^<]{2,64})\s*<""", RegexOption.IGNORE_CASE),
            Regex("""Mein\s+Konto[^<]{0,40}</[^>]+>\s*<[^>]+>\s*([^<]{2,64})\s*<""", RegexOption.IGNORE_CASE),
            Regex("""<title>\s*([^|<]{2,64})\s*\|""", RegexOption.IGNORE_CASE),
        )
        for (pattern in patterns) {
            val match = pattern.find(html)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            if (match.length in 2..64 &&
                !match.contains("serienstream", ignoreCase = true) &&
                !match.contains("aniworld", ignoreCase = true) &&
                !match.contains("login", ignoreCase = true) &&
                !match.contains("ddos", ignoreCase = true)
            ) {
                return match
            }
        }
        return null
    }

    fun authCookieNames(cookieHeader: String): List<String> {
        val cleaned = SerienStreamBypassHelper.sanitizeSessionCookies(cookieHeader)
        if (cleaned.isBlank()) return emptyList()
        return cleaned.split(";")
            .map { it.trim() }
            .filter { it.contains("=") }
            .map { it.substringBefore("=").trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.US) }
    }

    fun needsReauthHint(htmlOrMessage: String?): Boolean {
        val text = htmlOrMessage.orEmpty()
        if (text.isBlank()) return false
        return WatchlistImporter.looksLikeLoginPage(text) ||
            WatchlistImporter.looksLikeChallengePage(text) ||
            text.contains("just a moment", ignoreCase = true) ||
            text.contains("access denied", ignoreCase = true)
    }

    private fun clearCookieManager(cookieHeader: String) {
        val cookieManager = runCatching { CookieManager.getInstance() }.getOrNull() ?: return
        val names = authCookieNames(cookieHeader).ifEmpty {
            listOf(
                "cf_clearance",
                "ddos_token",
                "PHPSESSID",
                "phpsessid",
                "laravel_session",
                "XSRF-TOKEN",
                "xsrf-token",
                "altcha",
            )
        }
        val targets = linkedSetOf<String>().apply {
            add("https://serienstream.to/")
            add("https://serienstream.cx/")
            add(SerienStreamEndpoints.originFor(SerienStreamEndpoints.PROXY_HOST))
            SerienStreamProvider.candidateDomains().forEach { domain ->
                add(SerienStreamEndpoints.originFor(domain))
            }
            runCatching {
                add(SerienStreamProvider.baseUrl.trimEnd('/') + "/")
            }
        }
        targets.forEach { target ->
            val host = runCatching { Uri.parse(target).host }.getOrNull()
            // Expire known names.
            names.forEach { name ->
                runCatching {
                    cookieManager.setCookie(target, "$name=; Max-Age=0; Path=/")
                    if (!host.isNullOrBlank()) {
                        cookieManager.setCookie(target, "$name=; Max-Age=0; Path=/; Domain=$host")
                    }
                }
            }
            // Also expire whatever CookieManager still reports for the URL.
            val existing = runCatching { cookieManager.getCookie(target) }.getOrNull().orEmpty()
            existing.split(";")
                .map { it.trim() }
                .filter { it.contains("=") }
                .forEach { part ->
                    val name = part.substringBefore("=").trim()
                    if (name.isNotBlank()) {
                        runCatching {
                            cookieManager.setCookie(target, "$name=; Max-Age=0; Path=/")
                        }
                    }
                }
        }
        runCatching { cookieManager.flush() }
    }
}
