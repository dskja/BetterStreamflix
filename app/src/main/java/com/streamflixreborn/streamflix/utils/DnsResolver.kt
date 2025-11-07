package com.streamflixreborn.streamflix.utils

import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import okhttp3.logging.HttpLoggingInterceptor
import java.security.SecureRandom
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object DnsResolver {
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

    private var client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(30, TimeUnit.SECONDS)
        .sslSocketFactory(sslContext.socketFactory, trustManager)
        .hostnameVerifier { _, _ -> true }
        .addInterceptor(logging)
        .build()

    // Valeur par défaut
    private var _url: String = "https://1.1.1.1/dns-query"

    // DoH instancié en lazy initialement
    private var _doh: Dns = buildDoh(_url)

    val doh: Dns
        get() = _doh

    // Setter pour modifier l'URL et recréer le DoH
    @Synchronized
    fun setDnsUrl(newUrl: String) {
        if (newUrl != _url) {
            _url = newUrl
            _doh = buildDoh(_url)
        }
    }
@Synchronized
    private fun buildDoh(url: String): Dns {
        return if (url.isNotEmpty())
                   DnsOverHttps.Builder()
                       .client(client)
                       .url(url.toHttpUrl())
                       .build()
               else Dns.SYSTEM
    }
}
