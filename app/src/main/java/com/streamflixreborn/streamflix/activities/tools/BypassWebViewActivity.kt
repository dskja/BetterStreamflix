package com.streamflixreborn.streamflix.activities.tools

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.streamflixreborn.streamflix.R
import com.streamflixreborn.streamflix.utils.AppLanguageManager
import com.streamflixreborn.streamflix.utils.NetworkClient
import com.streamflixreborn.streamflix.utils.ThemeManager
import com.streamflixreborn.streamflix.utils.UserPreferences

class BypassWebViewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_COOKIE_HEADER = "extra_cookie_header"
        private const val COOKIE_POLL_INTERVAL_MS = 1000L
    }

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var statusView: TextView
    private lateinit var continueButton: Button
    private lateinit var cancelButton: Button
    private var isCleaningUp = false
    private var currentPageUrl: String? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val cookiePollRunnable = object : Runnable {
        override fun run() {
            if (isCleaningUp || isDestroyed || isFinishing) return
            updateBypassState(currentPageUrl)
            mainHandler.postDelayed(this, COOKIE_POLL_INTERVAL_MS)
        }
    }

    private val targetUrl: String by lazy {
        intent.getStringExtra(EXTRA_URL).orEmpty()
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguageManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeManager.mobileThemeRes(UserPreferences.selectedTheme))
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bypass_webview)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, systemBars.top, 0, systemBars.bottom)
            insets
        }
        webView = findViewById(R.id.bypass_webview)
        progressBar = findViewById(R.id.bypass_progress)
        statusView = findViewById(R.id.bypass_status)
        continueButton = findViewById(R.id.bypass_continue)
        cancelButton = findViewById(R.id.bypass_cancel)

        cancelButton.setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

        continueButton.setOnClickListener {
            val cookies = collectCookieHeader()
            if (cookies.isBlank()) {
                Toast.makeText(
                    this,
                    getString(R.string.bypass_status_complete_bypass_first),
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            setResult(
                Activity.RESULT_OK,
                Intent().putExtra(EXTRA_COOKIE_HEADER, cookies)
            )
            finish()
        }

        setupWebView()

        if (targetUrl.isBlank()) {
            Toast.makeText(this, getString(R.string.bypass_status_missing_url), Toast.LENGTH_SHORT).show()
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.loadUrl(targetUrl)
        mainHandler.post(cookiePollRunnable)
    }

    override fun onDestroy() {
        isCleaningUp = true
        mainHandler.removeCallbacksAndMessages(null)
        if (::webView.isInitialized) {
            runCatching { webView.stopLoading() }
            runCatching { webView.webChromeClient = WebChromeClient() }
            runCatching { webView.webViewClient = WebViewClient() }
            runCatching { webView.destroy() }
        }
        super.onDestroy()
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            userAgentString = NetworkClient.USER_AGENT
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            mediaPlaybackRequiresUserGesture = true
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress in 0..99) View.VISIBLE else View.GONE
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?,
            ): Boolean {
                if (isCleaningUp) return true

                val url = request?.url?.toString().orEmpty()
                val isMainFrame = request?.isForMainFrame ?: true
                if (!isMainFrame || url.isBlank()) return false

                if (isAllowedBypassHost(url)) {
                    currentPageUrl = url
                    return false
                }

                val cookies = collectCookieHeader()
                if (cookies.isNotBlank()) {
                    continueButton.isEnabled = true
                    statusView.text = getString(R.string.bypass_status_completed_continue)
                } else {
                    statusView.text = getString(R.string.bypass_status_external_redirect_blocked)
                }
                return true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                if (isCleaningUp) return
                progressBar.visibility = View.VISIBLE
                currentPageUrl = url
                updateBypassState(url)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (isCleaningUp) return
                currentPageUrl = url
                updateBypassState(url)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?,
            ) {
                currentPageUrl = request?.url?.toString()
                updateBypassState(request?.url?.toString())
            }
        }
    }

    private fun updateBypassState(currentUrl: String?) {
        if (isCleaningUp) return
        val cookies = collectCookieHeader()
        val hasClearance = cookies.contains("cf_clearance")
        continueButton.isEnabled = cookies.isNotBlank()
        statusView.text = when {
            hasClearance -> getString(R.string.bypass_status_completed_continue)
            cookies.isNotBlank() -> getString(R.string.bypass_status_cookies_detected)
            else -> getString(R.string.bypass_status_complete_in_page)
        }

        if (!currentUrl.isNullOrBlank()) {
            title = Uri.parse(currentUrl).host ?: getString(R.string.app_name)
        }
    }

    private fun collectCookieHeader(): String {
        if (isCleaningUp) return ""

        val cookieManager = CookieManager.getInstance()
        val candidates = linkedSetOf<String>()
        val currentUrl = currentPageUrl

        if (!currentUrl.isNullOrBlank()) {
            candidates += currentUrl
        }
        if (targetUrl.isNotBlank()) {
            candidates += targetUrl
        }

        val host = runCatching { Uri.parse(currentUrl ?: targetUrl).host.orEmpty() }.getOrDefault("")
        if (host.isNotBlank()) {
            candidates += "https://$host/"
            candidates += "http://$host/"
        }

        return candidates
            .mapNotNull { candidate -> cookieManager.getCookie(candidate)?.trim() }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
    }

    private fun isAllowedBypassHost(url: String): Boolean {
        val host = runCatching { Uri.parse(url).host.orEmpty() }.getOrDefault("")
        return host.equals("s.to", ignoreCase = true) ||
            host.equals("challenges.cloudflare.com", ignoreCase = true)
    }
}
