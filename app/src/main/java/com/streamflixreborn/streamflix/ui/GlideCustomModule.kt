package com.streamflixreborn.streamflix.ui

import android.content.Context
import android.webkit.CookieManager
import com.bumptech.glide.Glide
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.module.AppGlideModule
import com.streamflixreborn.streamflix.utils.ArtworkRequestHeaders
import com.streamflixreborn.streamflix.utils.DnsResolver
import com.streamflixreborn.streamflix.utils.NetworkClient
import okhttp3.*
import okhttp3.OkHttpClient.Builder
import okhttp3.logging.HttpLoggingInterceptor
import java.io.File
import java.io.InputStream
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

@GlideModule
class GlideCustomModule : AppGlideModule() {

    private fun getOkHttpClient(context: Context): OkHttpClient {
        val appCache = Cache(File(context.cacheDir, "glide-okhttp-cache"), 10 * 1024 * 1024)

        val logging = HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC)

        val trustAllCerts = arrayOf<TrustManager>(
            object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
            }
        )
        val sslContext = SSLContext.getInstance("TLS").apply { init(null, trustAllCerts, SecureRandom()) }
        val trustManager = trustAllCerts[0] as X509TrustManager

        return Builder()
            .cache(appCache)
            .cookieJar(imageCookieJar)
            .addInterceptor { chain ->
                val original = chain.request()
                val requestBuilder = original.newBuilder()

                if (original.header("User-Agent") == null) {
                    requestBuilder.header("User-Agent", NetworkClient.USER_AGENT)
                }
                if (original.header("Accept") == null) {
                    requestBuilder.header("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                }
                if (original.header("Accept-Language") == null) {
                    requestBuilder.header(
                        "Accept-Language", "it-IT,it;q=0.9,en-US;q=0.8,en;q=0.7"
                    )
                }
                chain.proceed(requestBuilder.build())
            }
            .readTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request()
                val headers = ArtworkRequestHeaders.headersFor(request.url)
                val strippedUrl = ArtworkRequestHeaders.stripHeaders(request.url)
                val fixedRequest = if (headers.isNotEmpty() || strippedUrl != request.url) {
                    request.newBuilder()
                        .url(strippedUrl)
                        .apply {
                            headers.forEach { (name, value) -> header(name, value) }
                        }
                        .build()
                } else {
                    request
                }
                chain.proceed(fixedRequest.withAnimeOnlineCookies())
            }
            .addInterceptor(logging)
            .sslSocketFactory(sslContext.socketFactory, trustManager)
            .hostnameVerifier { _, _ -> true }
            .dns(DnsResolver.doh)
            .build()
    }

    override fun registerComponents(
        context: Context, glide: Glide, registry: com.bumptech.glide.Registry
    ) {
        val okHttpClient = getOkHttpClient(context)
        registry.replace(
            GlideUrl::class.java, InputStream::class.java, OkHttpUrlLoader.Factory(okHttpClient)
        )
    }

    private val imageCookieJar = object : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            NetworkClient.cookieJar.saveFromResponse(url, cookies)
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> {
            return NetworkClient.cookieJar.loadForRequest(url)
        }
    }

    private fun Request.withAnimeOnlineCookies(): Request {
        val host = url.host.lowercase(Locale.ROOT)
        if (host != "ww3.animeonline.ninja" || header("Cookie") != null) return this

        val cookie = animeOnlineCookieHeader(url) ?: return this
        return newBuilder()
            .header("Cookie", cookie)
            .build()
    }

    private fun animeOnlineCookieHeader(url: HttpUrl): String? {
        val cookieManager = CookieManager.getInstance()
        val exact = url.newBuilder().fragment(null).build().toString()
        val root = url.newBuilder().encodedPath("/").query(null).fragment(null).build().toString()

        return listOf(
            exact,
            root,
            "https://ww3.animeonline.ninja/",
            "https://ww3.animeonline.ninja"
        ).firstNotNullOfOrNull { candidate ->
            cookieManager.getCookie(candidate)?.takeIf { it.isNotBlank() }
        }
    }
}
