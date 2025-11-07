package com.streamflixreborn.streamflix.ui

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.module.AppGlideModule
import com.streamflixreborn.streamflix.utils.DnsResolver
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.OkHttpClient.Builder
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import javax.net.ssl.SSLContext
import java.security.SecureRandom

@GlideModule
class GlideCustomModule : AppGlideModule() {
    private fun getOkHttpClient(): OkHttpClient {
        val appCache = Cache(File("cacheDir", "okhttpcache"), 10 * 1024 * 1024)

        val logging = HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC)

        // Always trust-all for image loading AND use DoH for resolution
        val trustAllCerts = arrayOf<TrustManager>(
            object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
            }
        )
        val sslContext = SSLContext.getInstance("TLS").apply { init(null, trustAllCerts, SecureRandom()) }
        val trustManager = trustAllCerts[0] as X509TrustManager

        // Build a base client (trust-all) to bootstrap DoH
        return Builder()
            .cache(appCache)
            .readTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier { _, _ -> true }
            .dns(DnsResolver.doh)
            .build()
    }

    override fun registerComponents(
        context: Context, glide: Glide, registry: com.bumptech.glide.Registry
    ) {
        val okHttpClient = getOkHttpClient()
        registry.replace(
            GlideUrl::class.java, InputStream::class.java, OkHttpUrlLoader.Factory(okHttpClient)
        )
    }
}