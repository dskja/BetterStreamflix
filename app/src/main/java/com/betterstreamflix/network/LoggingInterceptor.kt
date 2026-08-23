package com.betterstreamflix.network

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Logging interceptor — logs network requests and responses for
 * debugging purposes.
 */
class LoggingInterceptor(
    private val enabled: Boolean = true,
    private val logBody: Boolean = false,
    private val tag: String = "Network",
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        if (!enabled) return chain.proceed(chain.request())

        val request = chain.request()
        val startTime = System.currentTimeMillis()

        android.util.Log.d(tag, "→ ${request.method} ${request.url}")

        try {
            val response = chain.proceed(request)
            val duration = System.currentTimeMillis() - startTime

            android.util.Log.d(tag, "← ${response.code} ${request.url} (${duration}ms)")

            if (logBody && response.isSuccessful) {
                val body = response.peekBody(1024)
                android.util.Log.d(tag, "Body: ${body.string().take(500)}")
            }

            return response
        } catch (e: IOException) {
            val duration = System.currentTimeMillis() - startTime
            android.util.Log.e(tag, "✗ ${request.url} failed (${duration}ms): ${e.message}")
            throw e
        }
    }
}
