package com.betterstreamflix.network

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Retry interceptor — retries failed requests with exponential backoff.
 */
class RetryInterceptor(
    private val maxRetries: Int = 3,
    private val initialDelayMs: Long = 500,
    private val maxDelayMs: Long = 10_000,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        var attempt = 0
        var lastException: IOException? = null

        while (attempt <= maxRetries) {
            try {
                val response = chain.proceed(request)
                if (response.isSuccessful || response.code in 400..499) {
                    return response
                }
                response.close()
            } catch (e: IOException) {
                lastException = e
            }

            attempt++
            if (attempt > maxRetries) break

            val delay = (initialDelayMs * (1L shl (attempt - 1))).coerceAtMost(maxDelayMs)
            val jitter = (Math.random() * delay * 0.3).toLong()
            Thread.sleep(delay + jitter)
        }

        throw lastException ?: IOException("Max retries exceeded for ${request.url}")
    }
}
