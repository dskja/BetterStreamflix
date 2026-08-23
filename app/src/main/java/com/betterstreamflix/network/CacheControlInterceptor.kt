package com.betterstreamflix.network

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Cache control interceptor — sets cache control headers for
 * offline support and conditional requests.
 */
class CacheControlInterceptor(
    private val maxAgeSeconds: Int = 60,
    private val maxStaleSeconds: Int = 60 * 60 * 24 * 7,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()

        // Add cache control for requests
        if (request.header("Cache-Control") == null) {
            request = request.newBuilder()
                .header("Cache-Control", "public, max-age=$maxAgeSeconds")
                .build()
        }

        val response = chain.proceed(request)

        // Add cache control for responses if not present
        if (response.header("Cache-Control") == null) {
            return response.newBuilder()
                .header("Cache-Control", "public, max-age=$maxAgeSeconds, max-stale=$maxStaleSeconds")
                .build()
        }

        return response
    }
}
