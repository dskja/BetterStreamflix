package com.streamflixreborn.streamflix.utils

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import com.streamflixreborn.streamflix.R
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class WebViewResolver(private val context: Context) {

    private var webView: WebView? = null
    private var dialog: AlertDialog? = null
    private val mutex = Mutex()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val TAG = "Cine24hBypass"
    
    private var cursorX = 0f
    private var cursorY = 0f
    private var virtualCursor: ImageView? = null
    private var pollingCount = 0
    private val isTv = context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)

    private val challengeKeywords = listOf(
        "Just a moment...", "cf-browser-verification", "challenge-running", "Checking your browser", "cloudflare"
    )

    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): String = mutex.withLock {
        Log.d(TAG, "[WebView] Fetching: $url (IsTV: $isTv)")
        pollingCount = 0
        val result = withTimeoutOrNull(120000) {
            suspendCancellableCoroutine { continuation ->
                mainHandler.post { setupWebView(url, headers, continuation) }
                continuation.invokeOnCancellation { cleanup() }
            }
        }
        if (result == null) Log.e(TAG, "[WebView] Global Timeout for $url")
        return@withLock result ?: "<html><body>Timeout</body></html>"
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView(url: String, headers: Map<String, String>, continuation: kotlinx.coroutines.CancellableContinuation<String>) {
        webView = WebView(context).apply {
            setBackgroundColor(Color.WHITE)
            // IMPORTANTE: Su TV non deve essere focusable per lasciare il controllo al container
            isFocusable = !isTv 
            isFocusableInTouchMode = !isTv
            
            // Stabilità Rendering Software per Android TV 9 (come da registro)
            if (isTv) {
                setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            }
            
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                userAgentString = NetworkClient.USER_AGENT
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                loadWithOverviewMode = true
                useWideViewPort = true
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, currentUrl: String?) {
                    Log.d(TAG, "[WebView] onPageFinished: $currentUrl")
                    mainHandler.postDelayed({
                        if (webView != null) checkChallengeStatus(view, currentUrl ?: url, continuation)
                    }, 1500)
                }
            }
            loadUrl(url, headers)
        }
    }

    private fun checkChallengeStatus(view: WebView?, currentUrl: String, continuation: kotlinx.coroutines.CancellableContinuation<String>) {
        if (continuation.isCompleted || webView == null) return
        
        val cookieManager = CookieManager.getInstance()
        val cookies = cookieManager.getCookie(currentUrl) ?: ""
        val hasClearance = cookies.contains("cf_clearance")
        
        view?.evaluateJavascript("(function() { return document.documentElement.innerHTML; })();") { html ->
            val cleanHtml = html?.trim()?.removeSurrounding("\"")
                ?.replace("\\u003C", "<")?.replace("\\\"", "\"")?.replace("\\n", "\n") ?: ""
            
            val isChallenge = challengeKeywords.any { cleanHtml.contains(it, ignoreCase = true) }
            val hasContent = cleanHtml.contains("article") || cleanHtml.contains("iframe") || 
                             cleanHtml.contains("TPost") || cleanHtml.contains("grid-item") || 
                             cleanHtml.contains("optnslst") // Rilevamento server Cine24h (come da registro)

            Log.d(TAG, "[WebView] Status -> Challenge: $isChallenge, Content: $hasContent, Clearance: $hasClearance, Polling: $pollingCount")

            // Se rileviamo sblocco, chiudiamo tutto subito
            if ((!isChallenge && hasContent && cleanHtml.length > 1000) || hasClearance) {
                Log.d(TAG, "[WebView] SUCCESS detected! Closing bypass.")
                cookieManager.flush()
                if (continuation.isActive) {
                    continuation.resume("<html>$cleanHtml</html>")
                    cleanup()
                }
                return@evaluateJavascript
            }

            // Se dopo 2 polling (circa 3-4 secondi) non c'è contenuto, mostriamo il dialog per sbloccare
            if (dialog == null && pollingCount >= 2 && !hasContent) {
                Log.d(TAG, "[WebView] Content not found, forcing Visible Challenge UI")
                showVisibleChallenge(continuation)
            }

            pollingCount++
            if (pollingCount < 80) {
                mainHandler.postDelayed({ checkChallengeStatus(view, currentUrl, continuation) }, 2000)
            } else {
                Log.w(TAG, "[WebView] Max polling reached")
                if (continuation.isActive) continuation.resume("<html>$cleanHtml</html>")
                cleanup()
            }
        }
    }

    private fun showVisibleChallenge(continuation: kotlinx.coroutines.CancellableContinuation<String>) {
        if (dialog != null || webView == null) return
        mainHandler.post {
            try {
                // CONTAINER TV: Intercetta i tasti globalmente (Soluzione "Meravigliosa")
                val rootContainer = object : RelativeLayout(context) {
                    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                        if (event.action == KeyEvent.ACTION_DOWN) {
                            if (isTv) {
                                val step = 45f
                                when (event.keyCode) {
                                    KeyEvent.KEYCODE_DPAD_UP -> { cursorY -= step; updateCursorPosition(); return true }
                                    KeyEvent.KEYCODE_DPAD_DOWN -> { cursorY += step; updateCursorPosition(); return true }
                                    KeyEvent.KEYCODE_DPAD_LEFT -> { cursorX -= step; updateCursorPosition(); return true }
                                    KeyEvent.KEYCODE_DPAD_RIGHT -> { cursorX += step; updateCursorPosition(); return true }
                                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                                        Log.d(TAG, "[WebView] TV OK Key -> Simulating Mouse")
                                        simulateHumanMouseClick()
                                        return true
                                    }
                                }
                            }
                            
                            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                                Log.d(TAG, "[WebView] BACK Key -> Cancelling bypass")
                                dialog?.cancel()
                                return true
                            }
                        }
                        return super.dispatchKeyEvent(event)
                    }
                }.apply {
                    layoutParams = ViewGroup.LayoutParams(-1, -1)
                    setBackgroundColor(Color.BLACK)
                    isFocusable = isTv
                    isFocusableInTouchMode = isTv
                }

                if (isTv) {
                    val btnInfo = Button(context).apply {
                        id = View.generateViewId()
                        text = context.getString(R.string.bypass_tv_instructions)
                        setBackgroundColor(Color.parseColor("#4CAF50"))
                        setTextColor(Color.WHITE)
                        textSize = 20f
                        stateListAnimator = null 
                        isFocusable = false
                    }
                    val infoParams = RelativeLayout.LayoutParams(-1, 200)
                    infoParams.addRule(RelativeLayout.ALIGN_PARENT_TOP)
                    rootContainer.addView(btnInfo, infoParams)

                    val webContainer = FrameLayout(context).apply {
                        id = View.generateViewId()
                        setBackgroundColor(Color.WHITE)
                    }
                    val webParams = RelativeLayout.LayoutParams(-1, -1)
                    webParams.addRule(RelativeLayout.BELOW, btnInfo.id)
                    rootContainer.addView(webContainer, webParams)

                    (webView?.parent as? ViewGroup)?.removeView(webView)
                    webContainer.addView(webView, FrameLayout.LayoutParams(-1, -1))

                    virtualCursor = ImageView(context).apply {
                        setImageResource(android.R.drawable.ic_menu_mylocation) 
                        setColorFilter(Color.RED)
                        layoutParams = FrameLayout.LayoutParams(80, 80)
                        elevation = 100f
                    }
                    rootContainer.addView(virtualCursor)
                } else {
                    // Mobile: Solo WebView a tutto schermo
                    (webView?.parent as? ViewGroup)?.removeView(webView)
                    rootContainer.addView(webView, RelativeLayout.LayoutParams(-1, -1))
                }

                dialog = AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_NoActionBar_Fullscreen)
                    .setView(rootContainer)
                    .setCancelable(true)
                    .setOnCancelListener {
                        Log.d(TAG, "[WebView] Challenge cancelled by user")
                        if (continuation.isActive) {
                            continuation.resume("<html><body>User cancelled</body></html>")
                        }
                        cleanup()
                    }
                    .create()
                
                dialog?.show()

                if (isTv) {
                    rootContainer.post {
                        cursorX = rootContainer.width / 2f
                        cursorY = rootContainer.height / 2f
                        updateCursorPosition()
                        rootContainer.requestFocus()
                    }
                }
                Log.d(TAG, "[WebView] Challenge Dialog DISPLAYED (isTv: $isTv)")
            } catch (e: Exception) { Log.e(TAG, "[WebView] CRITICAL UI ERROR", e) }
        }
    }

    private fun updateCursorPosition() {
        virtualCursor?.let { cursor ->
            cursor.translationX = cursorX - 40
            cursor.translationY = cursorY - 40
            cursor.bringToFront()
        }
    }

    private fun simulateHumanMouseClick() {
        webView?.let { wv ->
            val location = IntArray(2)
            wv.getLocationOnScreen(location)
            val relX = cursorX - location[0]
            val relY = cursorY - location[1]

            val downTime = SystemClock.uptimeMillis()
            val propM = MotionEvent.PointerProperties().apply { id = 0; toolType = MotionEvent.TOOL_TYPE_MOUSE }
            val coordM = MotionEvent.PointerCoords().apply { x = relX; y = relY; pressure = 1f; size = 1f }

            val hover = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_HOVER_MOVE, 1, arrayOf(propM), arrayOf(coordM), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0)
            wv.dispatchGenericMotionEvent(hover); hover.recycle()

            val eventDown = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, 1, arrayOf(propM), arrayOf(coordM), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0)
            wv.dispatchTouchEvent(eventDown)

            mainHandler.postDelayed({
                coordM.x += 1f; coordM.y += 1f
                val eventUp = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, 1, arrayOf(propM), arrayOf(coordM), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0)
                wv.dispatchTouchEvent(eventUp)
                eventDown.recycle(); eventUp.recycle()
                CookieManager.getInstance().flush()
                Log.d(TAG, "[WebView] Simulated Mouse Click at ($relX, $relY)")
            }, 200)
        }
    }

    private fun cleanup() {
        mainHandler.post {
            try {
                dialog?.dismiss(); dialog = null
                webView?.stopLoading(); webView?.destroy(); webView = null
                virtualCursor = null
            } catch (e: Exception) { }
        }
    }
}
