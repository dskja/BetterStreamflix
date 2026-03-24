package com.streamflixreborn.streamflix.activities.tools

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
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
import com.streamflixreborn.streamflix.R
import com.streamflixreborn.streamflix.utils.AppLanguageManager
import com.streamflixreborn.streamflix.utils.NetworkClient
import com.streamflixreborn.streamflix.utils.ThemeManager
import com.streamflixreborn.streamflix.utils.UserPreferences

class BypassWebViewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_COOKIE_HEADER = "extra_cookie_header"
    }

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var statusView: TextView
    private lateinit var continueButton: Button
    private lateinit var cancelButton: Button

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
                    "Complete the bypass first.",
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
            Toast.makeText(this, "Missing bypass URL.", Toast.LENGTH_SHORT).show()
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.loadUrl(targetUrl)
    }

    override fun onDestroy() {
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString = NetworkClient.USER_AGENT
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress in 0..99) View.VISIBLE else View.GONE
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                progressBar.visibility = View.VISIBLE
                updateBypassState(url)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                updateBypassState(url)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?,
            ) {
                updateBypassState(request?.url?.toString())
            }
        }
    }

    private fun updateBypassState(currentUrl: String?) {
        val cookies = collectCookieHeader()
        val hasClearance = cookies.contains("cf_clearance")
        continueButton.isEnabled = cookies.isNotBlank()
        statusView.text = when {
            hasClearance -> "Bypass completed. Tap Continue to send access to the TV."
            cookies.isNotBlank() -> "Cookies detected. If the page looks solved, tap Continue."
            else -> "Complete the bypass in the page below, then tap Continue."
        }

        if (!currentUrl.isNullOrBlank()) {
            title = Uri.parse(currentUrl).host ?: getString(R.string.app_name)
        }
    }

    private fun collectCookieHeader(): String {
        val cookieManager = CookieManager.getInstance()
        val candidates = linkedSetOf<String>()
        val currentUrl = webView.url

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
}
