package com.dskja.betterstreamflix.activities.tools

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
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
import androidx.lifecycle.lifecycleScope
import com.dskja.betterstreamflix.R
import com.dskja.betterstreamflix.player.SerienStreamBypassHelper
import com.dskja.betterstreamflix.providers.AniWorldProvider
import com.dskja.betterstreamflix.providers.SerienStreamProvider
import com.dskja.betterstreamflix.utils.AppLanguageManager
import com.dskja.betterstreamflix.utils.ExperimentalMobileDesign
import com.dskja.betterstreamflix.utils.ThemeManager
import com.google.android.material.color.DynamicColors
import com.dskja.betterstreamflix.utils.UserPreferences
import com.dskja.betterstreamflix.watchlist.WatchlistImporter
import kotlinx.coroutines.launch

/**
 * WebView login + scrape flow for SerienStream / AniWorld watchlists (#96).
 */
class WatchlistImportActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SOURCE = "extra_source"
        const val SOURCE_SERIENSTREAM = "serienstream"
        const val SOURCE_ANIWORLD = "aniworld"
    }

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var statusView: TextView
    private lateinit var importButton: Button
    private lateinit var cancelButton: Button

    private val source: WatchlistImporter.Source by lazy {
        when (intent.getStringExtra(EXTRA_SOURCE)) {
            SOURCE_ANIWORLD -> WatchlistImporter.Source.ANIWORLD
            else -> WatchlistImporter.Source.SERIENSTREAM
        }
    }

    private val baseUrl: String by lazy {
        when (source) {
            WatchlistImporter.Source.SERIENSTREAM ->
                SerienStreamProvider.baseUrl.trimEnd('/') + "/"
            WatchlistImporter.Source.ANIWORLD ->
                AniWorldProvider.baseUrl.trimEnd('/') + "/"
        }
    }

    private val startUrl: String by lazy {
        // Load site root first for SerienStream — Cloudflare often blanks /login
        // when opened cold; landing on home then navigating to login is more reliable.
        when (source) {
            WatchlistImporter.Source.SERIENSTREAM -> baseUrl
            WatchlistImporter.Source.ANIWORLD -> "${baseUrl.trimEnd('/')}/login"
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguageManager.wrap(newBase))
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(
            if (ExperimentalMobileDesign.enabled()) {
                R.style.AppTheme_Mobile_Experimental
            } else {
                ThemeManager.mobileThemeRes(UserPreferences.selectedTheme)
            }
        )
        if (ExperimentalMobileDesign.enabled()) {
            DynamicColors.applyToActivityIfAvailable(this)
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_watchlist_import)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, systemBars.top, 0, systemBars.bottom)
            insets
        }

        webView = findViewById(R.id.watchlist_webview)
        progressBar = findViewById(R.id.watchlist_progress)
        statusView = findViewById(R.id.watchlist_status)
        importButton = findViewById(R.id.watchlist_import)
        cancelButton = findViewById(R.id.watchlist_cancel)

        statusView.setText(R.string.watchlist_import_login_hint)
        title = getString(R.string.settings_watchlist_import_title)
        importButton.isEnabled = source != WatchlistImporter.Source.SERIENSTREAM

        cancelButton.setOnClickListener { finish() }
        importButton.setOnClickListener { runImport() }

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.setBackgroundColor(android.graphics.Color.WHITE)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadsImagesAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            useWideViewPort = true
            loadWithOverviewMode = true
            // Desktop UA helps SerienStream CF challenges that blank mobile WebViews
            userAgentString = when (source) {
                WatchlistImporter.Source.SERIENSTREAM -> WatchlistImporter.USER_AGENT_DESKTOP
                WatchlistImporter.Source.ANIWORLD -> WatchlistImporter.USER_AGENT_MOBILE
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility =
                    if (newProgress in 1..99) android.view.View.VISIBLE else android.view.View.GONE
            }
        }
        webView.webViewClient = object : WebViewClient() {
            private var navigatedToLogin = false

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                if (source != WatchlistImporter.Source.SERIENSTREAM) {
                    statusView.setText(R.string.watchlist_import_login_hint)
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                updateImportReadyState(url)
                // After SerienStream home loads, open login once
                if (source == WatchlistImporter.Source.SERIENSTREAM &&
                    !navigatedToLogin &&
                    url != null &&
                    !url.contains("/login", ignoreCase = true)
                ) {
                    navigatedToLogin = true
                    view?.loadUrl("${baseUrl.trimEnd('/')}/login")
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?,
            ) {
                if (request?.isForMainFrame == true) {
                    statusView.text = getString(
                        R.string.watchlist_import_failed,
                        error?.description?.toString() ?: "load error",
                    )
                }
            }
        }
        webView.loadUrl(startUrl)
    }

    private fun updateImportReadyState(url: String?) {
        val cookies = collectCookieHeader()
        if (source == WatchlistImporter.Source.SERIENSTREAM) {
            val solved = SerienStreamBypassHelper.looksLikeBypassSolved(cookies)
            importButton.isEnabled = cookies.isNotBlank() || solved
            when {
                cookies.isBlank() -> {
                    statusView.setText(R.string.watchlist_import_login_hint)
                }
                !solved -> {
                    statusView.setText(R.string.bypass_status_challenge_pending)
                    SerienStreamBypassHelper.applyCookies(baseUrl, cookies)
                    url?.takeIf { it.isNotBlank() }?.let {
                        SerienStreamBypassHelper.applyCookies(it, cookies)
                    }
                }
                else -> {
                    statusView.setText(R.string.watchlist_import_login_hint)
                    SerienStreamBypassHelper.applyCookies(baseUrl, cookies)
                    url?.takeIf { it.isNotBlank() }?.let {
                        SerienStreamBypassHelper.applyCookies(it, cookies)
                    }
                }
            }
        } else {
            importButton.isEnabled = true
            statusView.setText(R.string.watchlist_import_login_hint)
        }
    }

    private fun collectCookieHeader(): String {
        val cookieManager = CookieManager.getInstance()
        val candidates = linkedSetOf<String>()
        val currentUrl = webView.url

        if (!currentUrl.isNullOrBlank()) {
            candidates += currentUrl
        }
        candidates += baseUrl

        if (source == WatchlistImporter.Source.SERIENSTREAM) {
            candidates += "https://serienstream.to/"
            candidates += "https://s.to/"
            candidates += "http://serienstream.to/"
            candidates += "http://s.to/"
        }

        val host = runCatching { Uri.parse(currentUrl ?: baseUrl).host.orEmpty() }.getOrDefault("")
        if (host.isNotBlank()) {
            candidates += "https://$host/"
            candidates += "http://$host/"
        }

        return candidates
            .mapNotNull { candidate -> cookieManager.getCookie(candidate)?.trim() }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()
    }

    private fun runImport() {
        val cookies = collectCookieHeader()
        if (cookies.isBlank()) {
            Toast.makeText(this, R.string.watchlist_import_login_hint, Toast.LENGTH_LONG).show()
            return
        }

        if (source == WatchlistImporter.Source.SERIENSTREAM) {
            SerienStreamBypassHelper.applyCookies(baseUrl, cookies)
            webView.url?.takeIf { it.isNotBlank() }?.let {
                SerienStreamBypassHelper.applyCookies(it, cookies)
            }
        }

        importButton.isEnabled = false
        statusView.setText(R.string.watchlist_import_progress)
        lifecycleScope.launch {
            val result = runCatching {
                WatchlistImporter.import(this@WatchlistImportActivity, source, cookies)
            }.getOrElse { e ->
                statusView.text = getString(R.string.watchlist_import_failed, e.message ?: "error")
                updateImportReadyState(webView.url)
                return@launch
            }
            if (result.importedCount > 0) {
                statusView.text = getString(R.string.watchlist_import_done, result.importedCount)
                Toast.makeText(
                    this@WatchlistImportActivity,
                    getString(R.string.watchlist_import_done, result.importedCount),
                    Toast.LENGTH_LONG,
                ).show()
                finish()
            } else {
                val detail = result.errors.firstOrNull() ?: "empty"
                statusView.text = getString(R.string.watchlist_import_failed, detail)
                updateImportReadyState(webView.url)
            }
        }
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
