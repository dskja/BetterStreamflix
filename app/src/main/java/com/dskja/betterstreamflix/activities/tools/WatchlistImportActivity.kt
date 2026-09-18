package com.dskja.betterstreamflix.activities.tools

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
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
import com.dskja.betterstreamflix.utils.NetworkClient
import com.dskja.betterstreamflix.utils.ThemeManager
import com.dskja.betterstreamflix.utils.UserPreferences
import com.dskja.betterstreamflix.watchlist.WatchlistImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONTokener
import kotlin.coroutines.resume

/**
 * WebView login + scrape flow for SerienStream / AniWorld watchlists.
 *
 * After login, Import navigates the same WebView to watchlist pages (so DDoS /
 * Cloudflare challenges stay solved), extracts HTML via JavaScript, then
 * persists Favorites. OkHttp is only used as a fallback.
 */
class WatchlistImportActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SOURCE = "extra_source"
        const val SOURCE_SERIENSTREAM = "serienstream"
        const val SOURCE_ANIWORLD = "aniworld"
        private const val TAG = "WatchlistImport"
        private const val PAGE_SETTLE_MS = 700L
    }

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var statusView: TextView
    private lateinit var importButton: Button
    private lateinit var cancelButton: Button

    private val mainHandler = Handler(Looper.getMainLooper())
    private var importing = false
    private var pageFinishedCallback: ((String?) -> Unit)? = null

    private val source: WatchlistImporter.Source by lazy {
        when (intent.getStringExtra(EXTRA_SOURCE)) {
            SOURCE_ANIWORLD -> WatchlistImporter.Source.ANIWORLD
            else -> WatchlistImporter.Source.SERIENSTREAM
        }
    }

    private val hostBase: String by lazy {
        when (source) {
            WatchlistImporter.Source.SERIENSTREAM ->
                SerienStreamProvider.baseUrl.trimEnd('/')
            WatchlistImporter.Source.ANIWORLD ->
                AniWorldProvider.baseUrl.trimEnd('/')
        }
    }

    private val startUrl: String by lazy { "$hostBase/login" }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguageManager.wrap(newBase))
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeManager.mobileThemeRes(UserPreferences.selectedTheme))
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
        importButton.setText(R.string.watchlist_import_action)

        cancelButton.setOnClickListener {
            if (importing) return@setOnClickListener
            finish()
        }
        importButton.setOnClickListener { runImport() }

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.userAgentString = NetworkClient.USER_AGENT
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility =
                    if (newProgress in 1..99) android.view.View.VISIBLE else android.view.View.GONE
            }
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?,
            ): Boolean = false

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                CookieManager.getInstance().flush()
                updateLoginState(url)
                pageFinishedCallback?.invoke(url)
            }
        }
        webView.loadUrl(startUrl)
    }

    private fun updateLoginState(url: String?) {
        if (importing) return
        val cookies = cookieHeader()
        val leftLogin = url != null && !url.contains("/login", ignoreCase = true)
        val hasSession = looksLoggedIn(cookies)
        importButton.isEnabled = leftLogin || hasSession || cookies.isNotBlank()
        if (leftLogin && hasSession) {
            statusView.setText(R.string.watchlist_import_ready)
        } else if (leftLogin) {
            statusView.setText(R.string.watchlist_import_ready_soft)
        } else {
            statusView.setText(R.string.watchlist_import_login_hint)
        }
    }

    private fun looksLoggedIn(cookies: String): Boolean {
        if (cookies.isBlank()) return false
        val lower = cookies.lowercase()
        val sessionMarkers = listOf(
            "remember",
            "auth",
            "user",
            "login",
            "session",
            "token",
            "jwt",
        )
        // More than a bare PHPSESSID / challenge cookie.
        val parts = cookies.split(';').map { it.trim() }.filter { it.contains('=') }
        if (parts.size >= 2 && sessionMarkers.any { lower.contains(it) }) return true
        if (lower.contains("rememberlogin") || lower.contains("remember_login")) return true
        return parts.size >= 3
    }

    private fun cookieHeader(): String {
        val hosts = linkedSetOf(
            hostBase,
            "$hostBase/",
            hostBase.replace("https://", "http://"),
        )
        // SerienStream mirrors
        if (source == WatchlistImporter.Source.SERIENSTREAM) {
            hosts += listOf("https://s.to/", "https://serienstream.to/")
        }
        val merged = linkedSetOf<String>()
        for (host in hosts) {
            CookieManager.getInstance().getCookie(host)
                ?.split(';')
                ?.map { it.trim() }
                ?.filter { it.contains('=') }
                ?.forEach { merged += it }
        }
        return merged.joinToString("; ")
    }

    private fun runImport() {
        if (importing) return
        val cookies = cookieHeader()
        if (cookies.isBlank()) {
            Toast.makeText(this, R.string.watchlist_import_login_hint, Toast.LENGTH_LONG).show()
            return
        }

        importing = true
        importButton.isEnabled = false
        cancelButton.isEnabled = false
        statusView.setText(R.string.watchlist_import_progress)

        lifecycleScope.launch {
            val collected = linkedMapOf<String, WatchlistImporter.ImportedItem>()
            val errors = mutableListOf<String>()
            var pagesScraped = 0
            val baseUrl = WatchlistImporter.baseUrlFor(source)

            try {
                // 1) WebView scrape (preferred — challenge cookies already valid here)
                var nextUrl: String? = WatchlistImporter.watchlistUrls(source, 1).first()
                var page = 1
                val visited = linkedSetOf<String>()

                while (nextUrl != null && page <= WatchlistImporter.MAX_PAGES) {
                    if (!visited.add(normalizeVisitKey(nextUrl))) break
                    statusView.text = getString(R.string.watchlist_import_progress_page, page)
                    val pageResult = loadHtmlInWebView(nextUrl)
                    val html = pageResult.html
                    val finalUrl = pageResult.url

                    if (WatchlistImporter.looksLikeChallengePage(html)) {
                        errors += getString(R.string.watchlist_import_challenge)
                        break
                    }
                    if (WatchlistImporter.looksLikeLoginPage(html, finalUrl)) {
                        errors += getString(R.string.watchlist_import_not_logged_in)
                        break
                    }

                    val parsed = withContext(Dispatchers.Default) {
                        WatchlistImporter.parseItems(html, baseUrl, source)
                    }
                    val before = collected.size
                    parsed.forEach { collected.putIfAbsent(it.id, it) }
                    pagesScraped++
                    Log.i(TAG, "Page $page ($finalUrl): +${parsed.size} items (total ${collected.size})")

                    val discoveredNext = WatchlistImporter.findNextPageUrl(html, finalUrl, baseUrl)
                    nextUrl = when {
                        discoveredNext != null -> discoveredNext
                        parsed.isNotEmpty() && collected.size > before -> {
                            // Implicit next page guess
                            WatchlistImporter.watchlistUrls(source, page + 1).firstOrNull()
                        }
                        else -> null
                    }
                    // Stop when a guessed page yields nothing new
                    if (parsed.isEmpty() && page > 1) break
                    if (parsed.isNotEmpty() && collected.size == before && discoveredNext == null) break
                    page++
                }

                // 2) OkHttp fallback if WebView found nothing
                if (collected.isEmpty()) {
                    statusView.setText(R.string.watchlist_import_progress_fallback)
                    val httpResult = WatchlistImporter.importViaHttp(
                        this@WatchlistImportActivity,
                        source,
                        cookies,
                    )
                    if (httpResult.importedCount > 0) {
                        finishWithSuccess(httpResult.importedCount)
                        return@launch
                    }
                    errors += httpResult.errors
                } else {
                    statusView.setText(R.string.watchlist_import_progress_saving)
                    val result = WatchlistImporter.importParsed(
                        context = this@WatchlistImportActivity,
                        source = source,
                        items = collected.values,
                        pagesScraped = pagesScraped,
                        errors = errors,
                    )
                    if (result.importedCount > 0) {
                        finishWithSuccess(result.importedCount)
                        return@launch
                    }
                    errors += result.errors
                }

                val detail = errors.firstOrNull()
                    ?: getString(R.string.watchlist_import_empty)
                statusView.text = getString(R.string.watchlist_import_failed, detail)
            } catch (e: Exception) {
                Log.e(TAG, "Import failed", e)
                statusView.text = getString(
                    R.string.watchlist_import_failed,
                    e.message ?: "error",
                )
            } finally {
                importing = false
                importButton.isEnabled = true
                cancelButton.isEnabled = true
                // Restore browsing client behavior
                pageFinishedCallback = null
            }
        }
    }

    private fun finishWithSuccess(count: Int) {
        statusView.text = getString(R.string.watchlist_import_done, count)
        Toast.makeText(
            this,
            getString(R.string.watchlist_import_done, count),
            Toast.LENGTH_LONG,
        ).show()
        finish()
    }

    private data class PageHtml(val url: String, val html: String)

    private suspend fun loadHtmlInWebView(url: String): PageHtml =
        suspendCancellableCoroutine { cont ->
            var settled = false
            val finish: (String?) -> Unit = finish@{ finishedUrl ->
                if (settled) return@finish
                settled = true
                pageFinishedCallback = null
                mainHandler.postDelayed({
                    webView.evaluateJavascript(EXTRACT_HTML_JS) { raw ->
                        val html = decodeJavascriptValue(raw).orEmpty()
                        if (cont.isActive) {
                            cont.resume(PageHtml(finishedUrl ?: url, html))
                        }
                    }
                }, PAGE_SETTLE_MS)
            }
            pageFinishedCallback = finish
            cont.invokeOnCancellation {
                pageFinishedCallback = null
                settled = true
            }
            // If already on the target URL, force a reload so onPageFinished fires.
            val current = webView.url.orEmpty()
            if (normalizeVisitKey(current) == normalizeVisitKey(url)) {
                webView.reload()
            } else {
                webView.loadUrl(url)
            }
            // Safety timeout
            mainHandler.postDelayed({
                if (!settled && cont.isActive) {
                    finish(webView.url)
                }
            }, 25_000L)
        }

    private fun normalizeVisitKey(url: String): String =
        url.substringBefore('#').trimEnd('/').lowercase()

    private fun decodeJavascriptValue(value: String?): String? {
        val encoded = value?.trim()?.takeIf { it.isNotEmpty() && it != "null" } ?: return null
        return when (val decoded = runCatching { JSONTokener(encoded).nextValue() }.getOrNull()) {
            is String -> decoded
            null -> encoded.removeSurrounding("\"")
            else -> decoded.toString()
        }
    }

    override fun onDestroy() {
        pageFinishedCallback = null
        mainHandler.removeCallbacksAndMessages(null)
        webView.destroy()
        super.onDestroy()
    }
}

private const val EXTRACT_HTML_JS =
    "(function(){return document.documentElement.outerHTML;})();"
