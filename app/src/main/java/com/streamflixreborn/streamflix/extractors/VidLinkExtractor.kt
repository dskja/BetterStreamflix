package com.streamflixreborn.streamflix.extractors

import android.os.Handler
import android.os.Looper
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.streamflixreborn.streamflix.StreamFlixApp
import com.streamflixreborn.streamflix.models.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class VidLinkExtractor : Extractor() {

    override val name = "VidLink"
    override val mainUrl = "https://vidlink.pro"

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
                
                // Configure WebView settings
                webView.settings.javaScriptEnabled = true

                // Timeout handler
                val timeoutHandler = Handler(Looper.getMainLooper())
                val timeoutRunnable = Runnable {
                    if (continuation.isActive) {
                        continuation.resumeWithException(Exception("Timeout waiting for stream"))
                        webView.destroy()
                    }
                }
                timeoutHandler.postDelayed(timeoutRunnable, 30000) // 30 seconds timeout

                continuation.invokeOnCancellation {
                    webView.stopLoading()
                    webView.destroy()
                }

                webView.webViewClient = object : WebViewClient() {
                    
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        
                        // Inject JavaScript to intercept the network response
                        val jsInterceptor = """
                            (function() {
                                const originalFetch = window.fetch;
                                window.fetch = async function(...args) {
                                    const response = await originalFetch(...args);
                                    const clone = response.clone();
                                    const url = response.url;
                                    
                                    if (url.includes('/api/b/')) {
                                        clone.json().then(data => {
                                             window.Android.onStreamFound(JSON.stringify(data));
                                        }).catch(err => {});
                                    }
                                    return response;
                                };
                            })();
                        """.trimIndent()
                        
                        view?.evaluateJavascript(jsInterceptor, null)
                    }
                }

                // Add JavaScript Interface
                webView.addJavascriptInterface(object : Any() {
                    @android.webkit.JavascriptInterface
                    fun onStreamFound(jsonData: String) {
                        timeoutHandler.removeCallbacks(timeoutRunnable)
                        if (continuation.isActive) {
                            try {
                                val json = JSONObject(jsonData)
                                if (json.has("stream")) {
                                    val stream = json.getJSONObject("stream")
                                    val playlist = stream.optString("playlist")
                                    
                                    val captionsList = mutableListOf<Video.Subtitle>()
                                    val captions = stream.optJSONArray("captions")
                                    if (captions != null) {
                                        for (i in 0 until captions.length()) {
                                            val cap = captions.getJSONObject(i)
                                            val id = cap.optString("id")
                                            val lang = cap.optString("language")
                                            captionsList.add(Video.Subtitle(lang, id))
                                        }
                                    }
                                    
                                    val video = Video(
                                        source = playlist,
                                        subtitles = captionsList,
                                        headers = mapOf("Referer" to mainUrl)
                                    )
                                    continuation.resume(video)
                                } else {
                                     continuation.resumeWithException(Exception("Stream data missing in response"))
                                }
                            } catch (e: Exception) {
                                continuation.resumeWithException(e)
                            } finally {
                                Handler(Looper.getMainLooper()).post { webView.destroy() }
                            }
                        }
                    }
                }, "Android")

                // Start loading the page
                webView.loadUrl(link)
            }
        }
    }
}
