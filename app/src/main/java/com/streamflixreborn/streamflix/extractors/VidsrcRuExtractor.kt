package com.streamflixreborn.streamflix.extractors

import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.media3.common.MimeTypes
import com.streamflixreborn.streamflix.StreamFlixApp
import com.streamflixreborn.streamflix.models.Video
import com.streamflixreborn.streamflix.utils.DnsResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class VidsrcRuExtractor : Extractor() {

    override val name = "Vidsrc.Ru"
    override val mainUrl = "https://vidsrc.ru"

    fun server(videoType: Video.Type): Video.Server {
        return Video.Server(
            id = name,
            name = name,
            src = when (videoType) {
                is Video.Type.Movie -> "$mainUrl/movie/${videoType.id}"
                is Video.Type.Episode -> "$mainUrl/tv/${videoType.tvShow.id}/${videoType.season.number}/${videoType.number}"
            },
        )
    }

    override suspend fun extract(link: String): Video {
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                val webView = WebView(StreamFlixApp.instance.applicationContext)
                
                webView.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                }

                val timeoutHandler = Handler(Looper.getMainLooper())
                val timeoutRunnable = Runnable {
                    if (continuation.isActive) {
                        continuation.resumeWithException(Exception("Timeout waiting for VidsrcRu stream"))
                        webView.destroy()
                    }
                }
                timeoutHandler.postDelayed(timeoutRunnable, 30000)

                webView.webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): android.webkit.WebResourceResponse? {
                        val url = request?.url?.toString() ?: ""
                        if (url.contains("/file2/") && url.endsWith(".m3u8")) {
                            timeoutHandler.removeCallbacks(timeoutRunnable)
                            if (continuation.isActive) {
                                val video = Video(
                                    source = url,
                                    subtitles = emptyList(),
                                    type = MimeTypes.APPLICATION_M3U8
                                )
                                Handler(Looper.getMainLooper()).post {
                                    continuation.resume(video)
                                    webView.destroy()
                                }
                            }
                        }
                        return super.shouldInterceptRequest(view, request)
                    }
                }

                webView.loadUrl(link)

                continuation.invokeOnCancellation {
                    webView.stopLoading()
                    webView.destroy()
                }
            }
        }
    }


}
