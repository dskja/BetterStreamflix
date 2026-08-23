package com.betterstreamflix.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Header interceptor — adds standard headers to all network requests
 * including user agent, accept headers, and custom provider headers.
 */
class HeaderInterceptor(
    private val userAgent: String,
    private val extraHeaders: Map<String, String> = emptyMap(),
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("User-Agent", userAgent)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Accept-Encoding", "gzip, deflate")
            .header("Connection", "keep-alive")
            .apply {
                extraHeaders.forEach { (key, value) -> header(key, value) }
            }
            .build()

        return chain.proceed(request)
    }

    companion object {
        /**
         * Create a header interceptor with the default app user agent.
         */
        fun create(userAgent: String, extraHeaders: Map<String, String> = emptyMap()): HeaderInterceptor {
            return HeaderInterceptor(userAgent, extraHeaders)
        }

        /**
         * Default user agent string.
         */
        const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Linux; Android 14; BetterStreamflix) AppleWebKit/537.36"
    }
}
