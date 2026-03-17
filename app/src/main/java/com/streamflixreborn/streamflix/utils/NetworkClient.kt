package com.streamflixreborn.streamflix.utils

import android.util.Log
import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object NetworkClient {

    private const val TAG = "Cine24hBypass"
    
    // User-Agent Mobile standard per massima compatibilità con Cloudflare
    const val USER_AGENT = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36"

    val cookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val cookieManager = CookieManager.getInstance()
            cookies.forEach { cookie ->
                cookieManager.setCookie(url.toString(), cookie.toString())
            }
            cookieManager.flush()
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            val cookieManager = CookieManager.getInstance()
            val cookieString = cookieManager.getCookie(url.toString()) ?: return emptyList()
            return cookieString.split(";").mapNotNull {
                Cookie.parse(url, it.trim())
            }
        }
    }

    private val loggingInterceptor by lazy {
        HttpLoggingInterceptor { message ->
            Log.d(TAG, "[OkHttp] $message")
        }.apply {
            level = HttpLoggingInterceptor.Level.HEADERS
        }
    }

    val default: OkHttpClient by lazy { buildClient(DnsResolver.doh) }
    val systemDns: OkHttpClient by lazy { buildClient(Dns.SYSTEM) }
    val noRedirects: OkHttpClient by lazy { buildClient(DnsResolver.doh) { it.followRedirects(false).followSslRedirects(false) } }

    val trustAll: OkHttpClient by lazy {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
        })
        val sslContext = SSLContext.getInstance("TLS").apply { init(null, trustAllCerts, SecureRandom()) }
        buildClient(DnsResolver.doh) {
            it.sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
              .hostnameVerifier { _, _ -> true }
        }
    }

    private fun buildClient(dns: Dns, customizer: ((OkHttpClient.Builder) -> Unit)? = null): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8")
                    .header("Accept-Language", "it-IT,it;q=0.9,en-US;q=0.8,en;q=0.7")
                    .header("Sec-Fetch-Dest", "document")
                    .header("Sec-Fetch-Mode", "navigate")
                    .header("Sec-Fetch-Site", "none")
                    .header("Upgrade-Insecure-Requests", "1")
                    .build()
                chain.proceed(request)
            }
            .addInterceptor(loggingInterceptor)
            .cookieJar(cookieJar)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .dns(dns)
        customizer?.invoke(builder)
        return builder.build()
    }
}
