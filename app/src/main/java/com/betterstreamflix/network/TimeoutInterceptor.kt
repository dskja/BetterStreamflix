package com.betterstreamflix.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Timeout interceptor — dynamically adjusts request timeouts based
 * on request type and network conditions.
 */
class TimeoutInterceptor(
    private val defaultTimeoutMs: Long = 30_000,
    private val streamingTimeoutMs: Long = 120_000,
    private val apiTimeoutMs: Long = 15_000,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()

        val timeout = when {
            url.contains("/stream/") || url.contains("/video/") -> streamingTimeoutMs
            url.contains("/api/") || url.contains("/search") -> apiTimeoutMs
            else -> defaultTimeoutMs
        }

        return chain.withConnectTimeout(timeout.toInt() / 1000, java.util.concurrent.TimeUnit.SECONDS)
            .withReadTimeout(timeout.toInt() / 1000, java.util.concurrent.TimeUnit.SECONDS)
            .proceed(request)
    }
}
