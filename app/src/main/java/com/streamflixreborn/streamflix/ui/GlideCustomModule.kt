package com.streamflixreborn.streamflix.ui

import android.content.Context
import com.bumptech.glide.Glide
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.module.AppGlideModule
import com.streamflixreborn.streamflix.utils.ArtworkRequestHeaders
import com.streamflixreborn.streamflix.utils.NetworkClient
import java.io.InputStream

@GlideModule
class GlideCustomModule : AppGlideModule() {
    private fun getOkHttpClient() = NetworkClient.default.newBuilder()
        .cookieJar(NetworkClient.cookieJar)
        .addInterceptor { chain ->
            val request = chain.request()
            val headers = ArtworkRequestHeaders.headersFor(request.url)
            val strippedUrl = ArtworkRequestHeaders.stripHeaders(request.url)
            val fixedRequest = if (headers.isNotEmpty() || strippedUrl != request.url) {
                request.newBuilder()
                    .url(strippedUrl)
                    .apply {
                        headers.forEach { (name, value) -> header(name, value) }
                        header("Accept-Language", "es-ES,es;q=0.9,en-US;q=0.8,en;q=0.7")
                        header("Sec-Fetch-Dest", "image")
                        header("Sec-Fetch-Mode", "no-cors")
                        header("Sec-Fetch-Site", "same-origin")
                        removeHeader("Upgrade-Insecure-Requests")
                    }
                    .build()
            } else {
                request
            }
            chain.proceed(fixedRequest)
        }
        .build()

    override fun registerComponents(
        context: Context,
        glide: Glide,
        registry: com.bumptech.glide.Registry
    ) {
        registry.replace(
            GlideUrl::class.java,
            InputStream::class.java,
            OkHttpUrlLoader.Factory(getOkHttpClient())
        )
    }
}
