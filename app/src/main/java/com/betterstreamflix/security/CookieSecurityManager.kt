package com.betterstreamflix.security

import android.content.Context
import android.webkit.CookieManager
import okhttp3.CookieJar

/**
 * Cookie security manager — manages cookies securely and clears
 * sensitive session data when needed.
 */
object CookieSecurityManager {

    /**
     * Clear all web cookies.
     */
    fun clearAllCookies() {
        val cookieManager = CookieManager.getInstance()
        cookieManager.removeAllCookies(null)
        cookieManager.flush()
    }

    /**
     * Clear cookies for a specific domain.
     */
    fun clearCookiesForDomain(domain: String) {
        val cookieManager = CookieManager.getInstance()
        val cookie = cookieManager.getCookie(domain)
        if (cookie != null) {
            cookie.split(";").forEach { part ->
                val name = part.trim().substringBefore("=", "")
                if (name.isNotEmpty()) {
                    cookieManager.setCookie(domain, "$name=; Max-Age=0; Path=/")
                }
            }
        }
        cookieManager.flush()
    }

    /**
     * Get cookies for a domain.
     */
    fun getCookiesForDomain(domain: String): Map<String, String> {
        val cookieManager = CookieManager.getInstance()
        val cookie = cookieManager.getCookie(domain) ?: return emptyMap()
        return cookie.split(";")
            .filter { it.contains("=") }
            .associate {
                val parts = it.trim().split("=", limit = 2)
                parts[0] to parts.getOrElse(1) { "" }
            }
    }

    /**
     * Set a secure cookie.
     */
    fun setSecureCookie(domain: String, name: String, value: String, httpsOnly: Boolean = true) {
        val cookieManager = CookieManager.getInstance()
        val flags = if (httpsOnly) "; Secure; HttpOnly" else ""
        cookieManager.setCookie(domain, "$name=$value; Path=/$flags")
        cookieManager.flush()
    }
}
