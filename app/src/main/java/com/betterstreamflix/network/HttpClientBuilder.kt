package com.betterstreamflix.network

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * HTTP client builder — builds configured OkHttp clients with
 * different preset configurations.
 */
object HttpClientBuilder {

    /**
     * Build a default HTTP client.
     */
    fun buildDefault(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Build a fast client for quick API calls.
     */
    fun buildFast(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .followRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Build a patient client for large downloads.
     */
    fun buildPatient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .followRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Build a client with custom interceptors.
     */
    fun buildWithInterceptors(
        interceptors: List<okhttp3.Interceptor>,
        connectTimeout: Long = 15,
        readTimeout: Long = 30,
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .apply { interceptors.forEach { addInterceptor(it) } }
            .connectTimeout(connectTimeout, TimeUnit.SECONDS)
            .readTimeout(readTimeout, TimeUnit.SECONDS)
            .writeTimeout(readTimeout, TimeUnit.SECONDS)
            .followRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * Build a client with DNS-over-HTTPS.
     */
    fun buildWithDoH(dohConfig: DnsOverHttpsConfig): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }
}
