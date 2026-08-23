package com.betterstreamflix.network

import com.betterstreamflix.utils.Constants
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * HTTP client factory — creates configured OkHttpClient instances
 * with common interceptors, timeouts, and features.
 */
object HttpClientFactory {

    /**
     * Create a default HTTP client with standard configuration.
     */
    fun createDefault(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(Constants.NETWORK_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(Constants.NETWORK_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .writeTimeout(Constants.NETWORK_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .addInterceptor(UserAgentInterceptor())
            .addInterceptor(RetryInterceptor(maxRetries = 2))
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    /**
     * Create a client with custom cookies.
     */
    fun createWithCookies(cookies: Map<String, String>): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(Constants.NETWORK_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(Constants.NETWORK_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .addInterceptor(UserAgentInterceptor())
            .addInterceptor(CookieInterceptor(cookies))
            .followRedirects(true)
            .build()
    }

    /**
     * Create a client with custom headers.
     */
    fun createWithHeaders(headers: Map<String, String>): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(Constants.NETWORK_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(Constants.NETWORK_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
            .addInterceptor(UserAgentInterceptor())
            .addInterceptor(HeaderInterceptor(Constants.USER_AGENT, headers))
            .followRedirects(true)
            .build()
    }
}

/**
 * Adds User-Agent header to all requests.
 */
class UserAgentInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("User-Agent", Constants.USER_AGENT)
            .build()
        return chain.proceed(request)
    }
}

/**
 * Adds cookies to requests.
 */
class CookieInterceptor(private val cookies: Map<String, String>) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val cookieHeader = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
        val request = chain.request().newBuilder()
            .header("Cookie", cookieHeader)
            .build()
        return chain.proceed(request)
    }
}
