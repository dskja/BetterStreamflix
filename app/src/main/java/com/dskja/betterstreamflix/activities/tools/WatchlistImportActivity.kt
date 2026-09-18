package com.dskja.betterstreamflix.activities.tools

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
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
import com.dskja.betterstreamflix.player.SerienStreamBypassHelper
import com.dskja.betterstreamflix.utils.AppLanguageManager
import com.dskja.betterstreamflix.utils.NetworkClient
import com.dskja.betterstreamflix.utils.ThemeManager
import com.dskja.betterstreamflix.utils.UserPreferences
import com.dskja.betterstreamflix.watchlist.WatchlistImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONTokener
import kotlin.coroutines.resume

/**
 * WebView login + scrape flow for SerienStream / AniWorld watchlists.
 *
 * After login, Import navigates the same WebView to watchlist pages (so DDoS /
 * Cloudflare challenges stay solved), scrolls to load lazy cards, extracts HTML,
 * then persists Favorites. OkHttp is only used as a fallback.
 */
class WatchlistImportActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SOURCE = "extra_source"
        const val SOURCE_SERIENSTREAM = "serienstream"
        const val SOURCE_ANIWORLD = "aniworld"
        private const val TAG = "WatchlistImport"
        private const val PAGE_SETTLE_MS = 1_200L
        private const val SCROLL_ROUNDS = 8
        private const val MODERN_UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/133.0.0.0 Mobile Safari/537.36"
    }

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var statusView: TextView
    private lateinit var importButton: Button
    private lateinit var cancelButton: Button

    private val mainHandler = Handler(Looper.getMainLooper())
    private var importing = false
    private var pageFinishedCallback: ((String?) -> Unit)? = null
    private var lastLoadError: String? = null

    private val source: WatchlistImporter.Source by lazy {
        when (intent.getStringExtra(EXTRA_SOURCE)) {
            SOURCE_ANIWORLD -> WatchlistImporter.Source.ANIWORLD
            else -> WatchlistImporter.Source.SERIENSTREAM
        }
    }

    private var hostBase: String = ""

    private val startUrl: String
        get() = "$hostBase/login"

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
        // Restore any previously pasted SerienStream session cookies.
        if (source == WatchlistImporter.Source.SERIENSTREAM) {
            val pasted = UserPreferences.serienStreamSessionCookies.trim()
            if (pasted.isNotBlank()) {
                SerienStreamBypassHelper.applyStoredSessionCookies()
            }
        }

        hostBase = resolveHostBase()
        setupWebView()
        // Warm the site first (challenge cookies), then open login. Fail over domains if blank.
        warmAndOpenLogin()
    }

    private fun warmAndOpenLogin(domainIndex: Int = 0) {
        val candidates = when (source) {
            WatchlistImporter.Source.SERIENSTREAM -> {
                val preferred = SerienStreamProvider.baseUrl.trimEnd('/')
                    .removePrefix("https://").removePrefix("http://")
                (listOf(preferred) + SerienStreamProvider.candidateDomains())
                    .map { it.trim().lowercase() }
                    .distinct()
            }
            WatchlistImporter.Source.ANIWORLD -> listOf(
                AniWorldProvider.baseUrl.trimEnd('/').removePrefix("https://").removePrefix("http://")
                    .ifBlank { "aniworld.to" },
                "aniworld.to",
            ).distinct()
        }
        if (domainIndex >= candidates.size) {
            statusView.setText(R.string.watchlist_import_login_hint)
            webView.loadUrl(startUrl)
            return
        }
        hostBase = "https://${candidates[domainIndex]}"
        lastLoadError = null
        webView.loadUrl(hostBase)
        mainHandler.postDelayed({
            if (isFinishing || importing) return@postDelayed
            // Only fail over when the main frame failed hard — challenge pages still "load".
            if (lastLoadError != null && domainIndex + 1 < candidates.size) {
                Log.w(TAG, "Warm failed on ${candidates[domainIndex]} ($lastLoadError) — trying next")
                warmAndOpenLogin(domainIndex + 1)
            } else {
                webView.loadUrl(startUrl)
            }
        }, 1_400L)
    }

    private fun resolveHostBase(): String {
        return when (source) {
            WatchlistImporter.Source.SERIENSTREAM -> {
                val preferred = SerienStreamProvider.baseUrl.trimEnd('/')
                preferred.ifBlank { "https://${SerienStreamProvider.candidateDomains().first()}" }
            }
            WatchlistImporter.Source.ANIWORLD ->
                AniWorldProvider.baseUrl.trimEnd('/').ifBlank { "https://aniworld.to" }
        }
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            userAgentString = MODERN_UA.ifBlank { NetworkClient.USER_AGENT }
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(false)
            mediaPlaybackRequiresUserGesture = true
            cacheMode = WebSettings.LOAD_DEFAULT
        }

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

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                lastLoadError = null
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                CookieManager.getInstance().flush()
                updateLoginState(url)
                pageFinishedCallback?.invoke(url)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?,
            ) {
                if (request?.isForMainFrame != true) return
                lastLoadError = error?.description?.toString() ?: "load error"
                if (!importing) {
                    statusView.text = getString(
                        R.string.watchlist_import_failed,
                        lastLoadError ?: "error",
                    )
                }
            }

            @SuppressLint("WebViewClientOnReceivedSslError")
            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?,
            ) {
                // Some SerienStream mirrors present odd intermediate certs on WebView.
                Log.w(TAG, "SSL warning on ${error?.url}: ${error?.primaryError}")
                handler?.proceed()
            }
        }
    }

    private fun updateLoginState(url: String?) {
        if (importing) return
        val cookies = cookieHeader()
        val leftLogin = url != null && !url.contains("/login", ignoreCase = true)
        val hasSession = looksLoggedIn(cookies)
        importButton.isEnabled = leftLogin || hasSession || cookies.isNotBlank()
        if (lastLoadError != null && !leftLogin && !hasSession) {
            statusView.text = getString(R.string.watchlist_import_failed, lastLoadError!!)
            return
        }
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
            "xsrf",
            "laravel_session",
        )
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
        when (source) {
            WatchlistImporter.Source.SERIENSTREAM -> {
                SerienStreamProvider.candidateDomains().forEach { domain ->
                    hosts += "https://$domain/"
                    hosts += "http://$domain/"
                }
            }
            WatchlistImporter.Source.ANIWORLD -> {
                hosts += listOf("https://aniworld.to/", "http://aniworld.to/")
            }
        }
        val merged = linkedMapOf<String, String>()
        for (host in hosts) {
            CookieManager.getInstance().getCookie(host)
                ?.split(';')
                ?.map { it.trim() }
                ?.filter { it.contains('=') }
                ?.forEach { part ->
                    val key = part.substringBefore('=').trim().lowercase()
                    if (key.isNotBlank()) merged[key] = part
                }
        }
        return merged.values.joinToString("; ")
    }

    private fun runImport() {
        if (importing) return
        val cookies = cookieHeader()
        if (cookies.isBlank()) {
            Toast.makeText(this, R.string.watchlist_import_login_hint, Toast.LENGTH_LONG).show()
            return
        }
        // Persist a working SerienStream cookie jar for TV / later sessions.
        if (source == WatchlistImporter.Source.SERIENSTREAM && looksLoggedIn(cookies)) {
            UserPreferences.serienStreamSessionCookies = cookies
        }

        importing = true
        importButton.isEnabled = false
        cancelButton.isEnabled = false
        statusView.setText(R.string.watchlist_import_progress)

        lifecycleScope.launch {
            val collected = linkedMapOf<String, WatchlistImporter.ImportedItem>()
            val errors = mutableListOf<String>()
            var pagesScraped = 0
            val baseUrl = "$hostBase/"

            try {
                val startUrls = WatchlistImporter.watchlistUrls(source, 1, hostBase)
                var nextUrl: String? = startUrls.first()
                var page = 1
                val visited = linkedSetOf<String>()
                var startUrlIndex = 0

                while (nextUrl != null && page <= WatchlistImporter.MAX_PAGES) {
                    if (!visited.add(normalizeVisitKey(nextUrl))) {
                        // Try alternate start URL patterns on page 1
                        if (page == 1 && startUrlIndex + 1 < startUrls.size) {
                            startUrlIndex++
                            nextUrl = startUrls[startUrlIndex]
                            continue
                        }
                        break
                    }
                    statusView.text = getString(R.string.watchlist_import_progress_page, page)
                    val pageResult = loadHtmlInWebView(nextUrl)
                    val html = pageResult.html
                    val finalUrl = pageResult.url

                    if (WatchlistImporter.looksLikeChallengePage(html)) {
                        // Give the challenge a chance to finish, then re-extract once.
                        delay(2_500)
                        val retried = extractHtmlNow(finalUrl)
                        if (WatchlistImporter.looksLikeChallengePage(retried.html)) {
                            errors += getString(R.string.watchlist_import_challenge)
                            break
                        }
                        val parsedRetry = withContext(Dispatchers.Default) {
                            WatchlistImporter.parseItems(retried.html, baseUrl, source)
                        }
                        parsedRetry.forEach { collected.putIfAbsent(it.id, it) }
                        pagesScraped++
                        nextUrl = WatchlistImporter.findNextPageUrl(
                            retried.html,
                            retried.url,
                            baseUrl,
                        ) ?: WatchlistImporter.watchlistUrls(source, page + 1, hostBase).firstOrNull()
                        page++
                        continue
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

                    // If page 1 is empty, try alternate watchlist URL shapes before giving up.
                    if (parsed.isEmpty() && page == 1 && startUrlIndex + 1 < startUrls.size) {
                        startUrlIndex++
                        nextUrl = startUrls[startUrlIndex]
                        continue
                    }

                    val discoveredNext = WatchlistImporter.findNextPageUrl(html, finalUrl, baseUrl)
                    nextUrl = when {
                        discoveredNext != null -> discoveredNext
                        parsed.isNotEmpty() && collected.size > before -> {
                            WatchlistImporter.watchlistUrls(source, page + 1, hostBase).firstOrNull()
                        }
                        else -> null
                    }
                    if (parsed.isEmpty() && page > 1) break
                    if (parsed.isNotEmpty() && collected.size == before && discoveredNext == null) break
                    page++
                }

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
                mainHandler.post {
                    lifecycleScope.launch {
                        scrollWatchlistToEnd()
                        delay(PAGE_SETTLE_MS)
                        val html = extractDocumentHtml()
                        if (cont.isActive) {
                            cont.resume(PageHtml(finishedUrl ?: url, html))
                        }
                    }
                }
            }
            pageFinishedCallback = finish
            cont.invokeOnCancellation {
                pageFinishedCallback = null
                settled = true
            }
            val current = webView.url.orEmpty()
            if (normalizeVisitKey(current) == normalizeVisitKey(url)) {
                webView.reload()
            } else {
                webView.loadUrl(url)
            }
            mainHandler.postDelayed({
                if (!settled && cont.isActive) {
                    finish(webView.url)
                }
            }, 30_000L)
        }

    private suspend fun extractHtmlNow(fallbackUrl: String): PageHtml {
        scrollWatchlistToEnd()
        delay(PAGE_SETTLE_MS)
        return PageHtml(webView.url ?: fallbackUrl, extractDocumentHtml())
    }

    private suspend fun scrollWatchlistToEnd() {
        repeat(SCROLL_ROUNDS) { round ->
            withContext(Dispatchers.Main) {
                webView.evaluateJavascript(
                    """
                    (function(){
                      try {
                        var h = Math.max(
                          document.body ? document.body.scrollHeight : 0,
                          document.documentElement ? document.documentElement.scrollHeight : 0
                        );
                        window.scrollTo(0, h);
                        var btn = document.querySelector(
                          'a[rel=next], .pagination .next a, button.load-more, .loadMore, #loadMore'
                        );
                        if (btn) { try { btn.click(); } catch (e) {} }
                        return h;
                      } catch (e) { return 0; }
                    })();
                    """.trimIndent(),
                    null,
                )
            }
            delay(350L + round * 40L)
        }
        withContext(Dispatchers.Main) {
            webView.evaluateJavascript("window.scrollTo(0, 0);", null)
        }
        delay(200L)
    }

    private suspend fun extractDocumentHtml(): String =
        suspendCancellableCoroutine { cont ->
            webView.evaluateJavascript(EXTRACT_HTML_JS) { raw ->
                val html = decodeJavascriptValue(raw).orEmpty()
                if (cont.isActive) cont.resume(html)
            }
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
        runCatching { webView.stopLoading() }
        runCatching { webView.destroy() }
        super.onDestroy()
    }
}

private const val EXTRACT_HTML_JS =
    "(function(){return document.documentElement ? document.documentElement.outerHTML : (document.body ? document.body.outerHTML : '');})();"
