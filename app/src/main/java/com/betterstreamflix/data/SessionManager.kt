package com.betterstreamflix.data

import android.webkit.CookieManager

/**
 * Manages provider sessions including cookies and auth tokens.
 */
object SessionManager {

    /**
     * Get cookies for a URL from the CookieManager.
     */
    fun getCookies(url: String): String {
        return CookieManager.getInstance().getCookie(url) ?: ""
    }

    /**
     * Set cookies for a URL.
     */
    fun setCookies(url: String, cookieHeader: String) {
        val cookieManager = CookieManager.getInstance()
        cookieHeader.split(";")
            .map { it.trim() }
            .filter { it.contains("=") }
            .forEach { cookie ->
                cookieManager.setCookie(url, cookie)
            }
        cookieManager.flush()
    }

    /**
     * Clear all cookies.
     */
    fun clearAllCookies() {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()
    }

    /**
     * Clear cookies for a specific domain.
     */
    fun clearCookiesForDomain(domain: String) {
        val cookieManager = CookieManager.getInstance()
        val cookies = cookieManager.getCookie("https://$domain") ?: return
        cookies.split(";")
            .map { it.trim() }
            .filter { it.contains("=") }
            .forEach { cookie ->
                val name = cookie.substringBefore("=")
                cookieManager.setCookie("https://$domain", "$name=; expires=Thu, 01 Jan 1970 00:00:00 GMT")
            }
        cookieManager.flush()
    }

    /**
     * Check if a session is valid by checking for essential cookies.
     */
    fun hasValidSession(url: String, requiredCookieNames: List<String> = emptyList()): Boolean {
        val cookies = getCookies(url)
        if (cookies.isBlank()) return false
        if (requiredCookieNames.isEmpty()) return true
        return requiredCookieNames.all { name ->
            cookies.contains("$name=")
        }
    }
}
