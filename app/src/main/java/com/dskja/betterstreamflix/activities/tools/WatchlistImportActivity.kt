package com.dskja.betterstreamflix.activities.tools

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
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
                WatchlistImporter.Source.SERIENSTREAM ->
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
                WatchlistImporter.Source.ANIWORLD ->
                    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
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
                statusView.setText(R.string.watchlist_import_login_hint)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                importButton.isEnabled = true
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

    private fun runImport() {
        val host = when (source) {
            WatchlistImporter.Source.SERIENSTREAM ->
                SerienStreamProvider.baseUrl.trimEnd('/').removePrefix("https://").removePrefix("http://")
            WatchlistImporter.Source.ANIWORLD ->
                AniWorldProvider.baseUrl.trimEnd('/').removePrefix("https://").removePrefix("http://")
        }
        val cookies = CookieManager.getInstance().getCookie("https://$host").orEmpty()
        if (cookies.isBlank()) {
            Toast.makeText(this, R.string.watchlist_import_login_hint, Toast.LENGTH_LONG).show()
            return
        }

        importButton.isEnabled = false
        statusView.setText(R.string.watchlist_import_progress)
        lifecycleScope.launch {
            val result = runCatching {
                WatchlistImporter.import(this@WatchlistImportActivity, source, cookies)
            }.getOrElse { e ->
                statusView.text = getString(R.string.watchlist_import_failed, e.message ?: "error")
                importButton.isEnabled = true
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
                importButton.isEnabled = true
            }
        }
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
