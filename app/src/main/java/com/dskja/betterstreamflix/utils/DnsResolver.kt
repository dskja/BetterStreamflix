package com.dskja.betterstreamflix.utils

import android.util.Log
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
import java.net.Inet4Address
import java.net.InetAddress

object DnsResolver : Dns {
    private const val TAG = "DnsResolver"
    private val logging = HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC)

    private val trustAllCerts = arrayOf<TrustManager>(
        object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
        }
    )
    private val sslContext = SSLContext.getInstance("TLS").apply { init(null, trustAllCerts, SecureRandom()) }
    private val trustManager = trustAllCerts[0] as X509TrustManager

    private var client: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(30, TimeUnit.SECONDS)
        .sslSocketFactory(sslContext.socketFactory, trustManager)
        .hostnameVerifier { _, _ -> true }
        .addInterceptor(logging)
        .build()

    private var _url: String = UserPreferences.dohProviderUrl
    private var _internalDoh: Dns = buildDoh(_url)

    override fun lookup(hostname: String): List<InetAddress> {
        val providerName = if (_url.isEmpty()) "SYSTEM" else _url
        Log.d(TAG, "Resolving host: $hostname using provider: $providerName")
        return try {
            val addresses = preferIpv4(_internalDoh.lookup(hostname))
            Log.d(TAG, "Resolved $hostname to: ${addresses.joinToString { it.hostAddress ?: "" }}")
            addresses
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve $hostname with $providerName: ${e.message}")
            if (_internalDoh === Dns.SYSTEM) {
                throw e
            }

            Log.w(TAG, "Falling back to system DNS for host: $hostname")
            val fallbackAddresses = preferIpv4(Dns.SYSTEM.lookup(hostname))
            Log.d(TAG, "System DNS resolved $hostname to: ${fallbackAddresses.joinToString { it.hostAddress ?: "" }}")
            fallbackAddresses
        }
    }

    /**
     * Prefer IPv4 when available. Broken/hijacked IPv6 routes are a common cause of
     * long connect timeouts on some ISP networks (including for api.themoviedb.org).
     */
    private fun preferIpv4(addresses: List<InetAddress>): List<InetAddress> {
        if (addresses.size <= 1) return addresses
        val ipv4 = addresses.filterIsInstance<Inet4Address>()
        if (ipv4.isEmpty()) return addresses
        val ipv6 = addresses.filterNot { it is Inet4Address }
        return ipv4 + ipv6
    }

    val doh: Dns get() = this

    @Synchronized
    fun setDnsUrl(newUrl: String) {
        Log.i(TAG, "DNS Change Requested: New URL = '$newUrl' (Current = '$_url')")
        if (newUrl != _url) {
            _url = newUrl
            _internalDoh = buildDoh(_url)
            Log.i(TAG, "DNS Engine updated successfully to: ${if (newUrl.isEmpty()) "SYSTEM" else newUrl}")
        } else {
            Log.d(TAG, "DNS URL is the same as current, skipping update.")
        }
    }

    @Synchronized
    private fun buildDoh(url: String): Dns {
        return if (url.isNotEmpty()) {
            try {
                DnsOverHttps.Builder()
                    .client(client)
                    .url(url.toHttpUrl())
                    .build()
            } catch (e: Exception) {
                Log.e(TAG, "Error building DoH for $url, falling back to SYSTEM: ${e.message}")
                Dns.SYSTEM
            }
        } else {
            Log.d(TAG, "No DoH URL provided, using SYSTEM DNS")
            Dns.SYSTEM
        }
    }
}
