package com.streamflixreborn.streamflix.activities.tools

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.webkit.JavascriptInterface
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
import org.json.JSONObject
import org.json.JSONArray

class BypassWebViewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_URL = "extra_url"
        const val EXTRA_COOKIE_HEADER = "extra_cookie_header"
        const val EXTRA_S_TO_TOKEN = "extra_s_to_token"
    }

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var statusView: TextView
    private lateinit var continueButton: Button
    private lateinit var cancelButton: Button
    private var domObserverInstalled = false
    private var isCleaningUp = false
    private var currentPageUrl: String? = null

    private val targetUrl: String by lazy {
        intent.getStringExtra(EXTRA_URL).orEmpty()
    }
    private val sToToken: String by lazy {
        intent.getStringExtra(EXTRA_S_TO_TOKEN).orEmpty()
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
        webView.addJavascriptInterface(BypassJavascriptBridge(), "AndroidBypass")

        if (targetUrl.isBlank()) {
            Toast.makeText(this, getString(R.string.bypass_status_missing_url), Toast.LENGTH_SHORT).show()
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.loadUrl(targetUrl)
    }

    override fun onDestroy() {
        isCleaningUp = true
        if (::webView.isInitialized) {
            runCatching { webView.removeJavascriptInterface("AndroidBypass") }
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
                domObserverInstalled = false
                currentPageUrl = url
                updateBypassState(url)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (isCleaningUp) return
                currentPageUrl = url
                installSxToModalWatcher()
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

    private fun installSxToModalWatcher() {
        if (isCleaningUp || domObserverInstalled || !isSerienStreamUrl(currentPageUrl ?: targetUrl)) return

        val tokenJson = JSONArray().put(sToToken).toString()

        val script = """
            (function() {
              if (window.__streamflixSxToWatcherInstalled) {
                return 'installed';
              }
              window.__streamflixSxToWatcherInstalled = true;
              var streamflixToken = $tokenJson[0] || '';
              window.__streamflixSubmitted = false;
              window.__streamflixTurnstileRendered = false;
              window.__streamflixTickCount = 0;
              window.__streamflixLastReportAt = 0;

              function ensureBackdrop() {
                var backdrop = document.querySelector('.modal-backdrop');
                if (!backdrop) {
                  backdrop = document.createElement('div');
                  backdrop.className = 'modal-backdrop fade show';
                  document.body.appendChild(backdrop);
                } else {
                  backdrop.classList.add('show');
                }
              }

              function ensureModalVisible() {
                var modal = document.getElementById('playerPrepareModal');
                if (!modal) {
                  return false;
                }
                modal.classList.add('show');
                modal.style.display = 'block';
                modal.removeAttribute('aria-hidden');
                modal.setAttribute('aria-modal', 'true');
                document.body.classList.add('modal-open');
                document.body.style.overflow = 'hidden';
                document.body.style.paddingRight = '0px';
                ensureBackdrop();
                var frame = modal.querySelector('iframe[src*="cloudflare"], iframe[title*="Widget"], iframe[title*="Turnstile"]');
                if (frame) {
                  frame.scrollIntoView({ block: 'center', inline: 'center' });
                }
                return true;
              }

              function ensureTurnstileScript() {
                if (window.turnstile) {
                  return true;
                }
                if (!document.querySelector('script[data-streamflix-turnstile]')) {
                  var script = document.createElement('script');
                  script.src = 'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit';
                  script.async = true;
                  script.defer = true;
                  script.setAttribute('data-streamflix-turnstile', '1');
                  document.head.appendChild(script);
                }
                return false;
              }

              function ensureTurnstileRendered() {
                var gateRoot = document.getElementById('episode-redirect-gate-root');
                var form = document.getElementById('player-prepare-form');
                var container = document.getElementById('player-prepare-turnstile');
                if (!gateRoot || !form || !container) {
                  return false;
                }
                if (container.querySelector('iframe')) {
                  window.__streamflixTurnstileRendered = true;
                  return true;
                }
                if (window.__streamflixTurnstileRendered) {
                  return false;
                }
                if (!ensureTurnstileScript() || !window.turnstile) {
                  return false;
                }

                var sitekey = gateRoot.getAttribute('data-turnstile-sitekey');
                if (!sitekey) {
                  return false;
                }

                container.innerHTML = '';
                window.__streamflixTurnstileRendered = true;
                try {
                  window.turnstile.render(container, {
                    sitekey: sitekey,
                    callback: function(token) {
                      var responseInput = form.querySelector('input[name="cf-turnstile-response"]');
                      if (!responseInput) {
                        responseInput = document.createElement('input');
                        responseInput.type = 'hidden';
                        responseInput.name = 'cf-turnstile-response';
                        form.appendChild(responseInput);
                      }
                      responseInput.value = token || '';
                      reportState();
                    },
                    'error-callback': function() {
                      window.__streamflixTurnstileRendered = false;
                      reportState();
                    },
                    'expired-callback': function() {
                      window.__streamflixSubmitted = false;
                      window.__streamflixTurnstileRendered = false;
                      reportState();
                    },
                    theme: 'dark'
                  });
                  return true;
                } catch (e) {
                  window.__streamflixTurnstileRendered = false;
                  return false;
                }
              }

              function getTurnstileResponse() {
                var responseInput = document.querySelector('#player-prepare-form input[name="cf-turnstile-response"]');
                if (!responseInput) {
                  responseInput = document.querySelector('#player-prepare-turnstile input[name="cf-turnstile-response"]');
                }
                var value = responseInput && typeof responseInput.value === 'string'
                  ? responseInput.value.trim()
                  : '';
                return {
                  present: !!responseInput,
                  solved: value.length > 0
                };
              }

              function getAltchaState() {
                var container = document.getElementById('player-prepare-altcha');
                if (!container) {
                  return {
                    present: false,
                    visible: false
                  };
                }
                var visible = window.getComputedStyle(container).display !== 'none';
                return {
                  present: true,
                  visible: visible
                };
              }

              function applyToken() {
                if (!streamflixToken) {
                  return false;
                }
                var tokenInput = document.getElementById('player-prepare-token');
                var form = document.getElementById('player-prepare-form');
                if (!tokenInput || !form) {
                  return false;
                }
                tokenInput.value = streamflixToken;
                form.setAttribute('action', '/r');
                return true;
              }

              function reportState() {
                var now = Date.now();
                if (now - window.__streamflixLastReportAt < 500) {
                  return;
                }
                window.__streamflixLastReportAt = now;

                var modal = document.getElementById('playerPrepareModal');
                var rendered = ensureTurnstileRendered();
                var frame = document.querySelector('#playerPrepareModal iframe[src*="cloudflare"], #playerPrepareModal iframe[title*="Widget"], #playerPrepareModal iframe[title*="Turnstile"]');
                var continueButton = document.querySelector('#playerPrepareModal .btn-primary, #playerPrepareModal button[type="submit"], #playerPrepareModal input[type="submit"]');
                var form = document.getElementById('player-prepare-form');
                var turnstile = getTurnstileResponse();
                var altcha = getAltchaState();
                var state = {
                  modalPresent: !!modal,
                  modalVisible: !!(modal && (modal.classList.contains('show') || (window.getComputedStyle(modal).display !== 'none'))),
                  turnstilePresent: !!frame,
                  turnstileResponsePresent: turnstile.present,
                  turnstileSolved: turnstile.solved,
                  altchaPresent: altcha.present,
                  altchaVisible: altcha.visible,
                  continueVisible: !!(continueButton && continueButton.offsetParent !== null),
                  formPresent: !!form,
                  tokenInjected: applyToken(),
                  formSubmitted: window.__streamflixSubmitted,
                  turnstileRendered: rendered || !!frame || window.__streamflixTurnstileRendered
                };
                if (window.AndroidBypass && window.AndroidBypass.onDomState) {
                  window.AndroidBypass.onDomState(JSON.stringify(state));
                }
              }

              applyToken();
              ensureModalVisible();
              reportState();

              var tick = function() {
                window.__streamflixTickCount += 1;
                ensureModalVisible();
                reportState();
                if (window.__streamflixTickCount < 45 &&
                    !document.cookie.match(/(?:^|; )cf_clearance=/)) {
                  window.setTimeout(tick, 1000);
                }
              };

              window.addEventListener('load', function() {
                window.__streamflixTickCount = 0;
                ensureModalVisible();
                reportState();
                window.setTimeout(tick, 500);
              });

              window.setTimeout(tick, 500);

              return 'ok';
            })();
        """.trimIndent()

        webView.evaluateJavascript(script) {
            domObserverInstalled = true
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

    private fun isSerienStreamUrl(url: String): Boolean {
        return runCatching {
            Uri.parse(url).host.equals("s.to", ignoreCase = true)
        }.getOrDefault(false)
    }

    private fun isAllowedBypassHost(url: String): Boolean {
        val host = runCatching { Uri.parse(url).host.orEmpty() }.getOrDefault("")
        return host.equals("s.to", ignoreCase = true) ||
            host.equals("challenges.cloudflare.com", ignoreCase = true)
    }

    private fun updateStatusFromDom(stateJson: String?) {
        if (stateJson.isNullOrBlank() || isCleaningUp || isDestroyed || isFinishing) return

        runOnUiThread {
            if (isCleaningUp || isDestroyed || isFinishing) return@runOnUiThread
            val cookies = collectCookieHeader()
            val hasClearance = cookies.contains("cf_clearance")
            val state = runCatching { JSONObject(stateJson) }.getOrNull() ?: return@runOnUiThread
            val modalPresent = state.optBoolean("modalPresent")
            val modalVisible = state.optBoolean("modalVisible")
            val turnstilePresent = state.optBoolean("turnstilePresent")
            val turnstileResponsePresent = state.optBoolean("turnstileResponsePresent")
            val turnstileSolved = state.optBoolean("turnstileSolved")
            val altchaVisible = state.optBoolean("altchaVisible")
            val continueVisible = state.optBoolean("continueVisible")
            val formPresent = state.optBoolean("formPresent")
            val formSubmitted = state.optBoolean("formSubmitted")
            val turnstileRendered = state.optBoolean("turnstileRendered")

            if (hasClearance) {
                continueButton.isEnabled = true
                statusView.text = getString(R.string.bypass_status_completed_continue)
                return@runOnUiThread
            }

            statusView.text = when {
                altchaVisible && turnstilePresent -> getString(R.string.bypass_status_complete_captchas_then_weiter_continue)
                altchaVisible -> getString(R.string.bypass_status_complete_captchas_then_weiter_continue)
                formSubmitted -> getString(R.string.bypass_status_submitted_wait)
                turnstileSolved && continueVisible -> getString(R.string.bypass_status_complete_captchas_then_weiter_continue)
                turnstilePresent && modalVisible -> getString(R.string.bypass_status_complete_captcha_modal)
                turnstileRendered && modalVisible -> getString(R.string.bypass_status_loading_captcha)
                turnstileResponsePresent && formPresent -> getString(R.string.bypass_status_complete_captchas_then_weiter_continue)
                modalPresent -> getString(R.string.bypass_status_waiting_modal)
                continueVisible -> getString(R.string.bypass_status_complete_captchas_then_weiter_continue)
                else -> getString(R.string.bypass_status_complete_in_page)
            }
        }
    }

    private inner class BypassJavascriptBridge {
        @JavascriptInterface
        fun onDomState(stateJson: String?) {
            updateStatusFromDom(stateJson)
        }
    }
}
