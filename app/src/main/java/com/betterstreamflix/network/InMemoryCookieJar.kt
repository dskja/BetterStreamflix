package com.betterstreamflix.network

import com.betterstreamflix.utils.Logger
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory cookie jar for managing session cookies across requests.
 */
class InMemoryCookieJar : CookieJar {

    private val cookies = ConcurrentHashMap<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host
        this.cookies.getOrPut(host) { mutableListOf() }.apply {
            removeAll { existing -> cookies.any { it.name == existing.name } }
            addAll(cookies)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        val hostCookies = cookies[host] ?: return emptyList()
        return hostCookies.filter { !it.expiresAt || it.persistent && System.currentTimeMillis() < it.expiresAt }
    }

    /**
     * Clear all cookies.
     */
    fun clearAll() {
        cookies.clear()
    }

    /**
     * Clear cookies for a specific host.
     */
    fun clearForHost(host: String) {
        cookies.remove(host)
    }

    /**
     * Get all cookies for a host.
     */
    fun getCookiesForHost(host: String): List<Cookie> {
        return cookies[host]?.toList() ?: emptyList()
    }

    /**
     * Add a cookie manually.
     */
    fun addCookie(host: String, name: String, value: String, domain: String, path: String = "/") {
        val cookie = Cookie.Builder()
            .name(name)
            .value(value)
            .domain(domain)
            .path(path)
            .build()
        cookies.getOrPut(host) { mutableListOf() }.add(cookie)
    }
}
