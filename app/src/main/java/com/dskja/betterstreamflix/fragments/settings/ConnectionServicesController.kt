package com.dskja.betterstreamflix.fragments.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.webkit.CookieManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import com.dskja.betterstreamflix.BuildConfig
import com.dskja.betterstreamflix.R
import com.dskja.betterstreamflix.platform.ConnectionDiagnostics
import com.dskja.betterstreamflix.platform.IntegrationStatus
import com.dskja.betterstreamflix.utils.CacheUtils
import com.dskja.betterstreamflix.utils.DnsResolver
import com.dskja.betterstreamflix.utils.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shared Connection & Services (screen_network) binder for Mobile + TV.
 */
object ConnectionServicesController {

    fun bind(
        fragment: Fragment,
        scope: LifecycleCoroutineScope,
        findPreference: (String) -> Preference?,
        openScreen: ((String) -> Unit)? = null,
        onScanResolverQr: (() -> Unit)? = null,
        onDohChanged: (() -> Unit)? = null,
        onWebSocketBypassTest: (() -> Unit)? = null,
    ) {
        val context = fragment.requireContext()

        findPreference("p_scan_resolver_qr")?.setOnPreferenceClickListener {
            onScanResolverQr?.invoke()
            true
        }

        findPreference("p_doh_provider_url")?.let { pref ->
            val list = pref as? ListPreference ?: return@let
            list.value = UserPreferences.dohProviderUrl
            list.summary = list.entry
            list.setOnPreferenceChangeListener { preference, newValue ->
                val newUrl = newValue as String
                UserPreferences.dohProviderUrl = newUrl
                DnsResolver.setDnsUrl(newUrl)
                if (preference is ListPreference) {
                    val index = preference.findIndexOfValue(newUrl)
                    preference.summary = preference.entries?.getOrNull(index)
                }
                refreshStatus(findPreference, context)
                if (onDohChanged != null) {
                    onDohChanged.invoke()
                } else {
                    Toast.makeText(context, R.string.doh_provider_updated, Toast.LENGTH_LONG).show()
                }
                true
            }
        }

        findPreference("SUBDL_API_KEY")?.let { pref ->
            val edit = pref as? EditTextPreference ?: return@let
            val value = UserPreferences.subdlApiKey
            edit.text = value
            edit.summary = maskedKeySummary(context, value)
            edit.setOnPreferenceChangeListener { preference, newValue ->
                val typed = (newValue as? String).orEmpty().trim()
                UserPreferences.subdlApiKey = typed
                preference.summary = maskedKeySummary(context, typed)
                Toast.makeText(
                    context,
                    if (typed.isBlank()) R.string.settings_subdl_api_key_reset
                    else R.string.settings_subdl_api_key_success,
                    Toast.LENGTH_SHORT,
                ).show()
                true
            }
        }

        findPreference("BYPASS_WS_ADVERTISED_HOST")?.let { pref ->
            val edit = pref as? EditTextPreference ?: return@let
            val current = UserPreferences.bypassWsAdvertisedHost
            edit.text = current
            edit.summary = if (current.isBlank()) {
                context.getString(R.string.settings_bypass_advertised_host_auto)
            } else {
                current
            }
            edit.setOnBindEditTextListener { editText ->
                editText.setSingleLine()
                editText.hint = "192.168.1.50"
                editText.setText(UserPreferences.bypassWsAdvertisedHost)
                editText.setSelection(editText.text?.length ?: 0)
            }
            edit.setOnPreferenceChangeListener { preference, newValue ->
                val value = (newValue as? String).orEmpty().trim()
                UserPreferences.bypassWsAdvertisedHost = value
                preference.summary = if (value.isBlank()) {
                    context.getString(R.string.settings_bypass_advertised_host_auto)
                } else {
                    value
                }
                true
            }
        }

        findPreference("test_websocket_bypass")?.apply {
            isVisible = BuildConfig.DEBUG && onWebSocketBypassTest != null
            setOnPreferenceClickListener {
                onWebSocketBypassTest?.invoke()
                true
            }
        }

        findPreference("NETWORK_REFRESH_STATUS")?.setOnPreferenceClickListener {
            refresh(findPreference, context)
            Toast.makeText(context, R.string.connection_status_refreshed, Toast.LENGTH_SHORT).show()
            true
        }

        findPreference("NETWORK_TEST_CONNECTION")?.setOnPreferenceClickListener { pref ->
            pref.isEnabled = false
            pref.summary = context.getString(R.string.connection_probe_running)
            scope.launch {
                val report = withContext(Dispatchers.IO) {
                    ConnectionDiagnostics.runFullDiagnostic(context)
                }
                pref.isEnabled = true
                pref.summary = report.asSummary()
                applyLastProbe(findPreference, report)
                AlertDialog.Builder(context)
                    .setTitle(report.title)
                    .setMessage(report.lines.joinToString("\n"))
                    .setPositiveButton(android.R.string.ok, null)
                    .setNeutralButton(R.string.connection_copy_report) { _, _ ->
                        copyReport(context, report)
                    }
                    .show()
            }
            true
        }

        findPreference("NETWORK_TEST_DOH")?.setOnPreferenceClickListener { pref ->
            pref.isEnabled = false
            pref.summary = context.getString(R.string.connection_probe_running)
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    ConnectionDiagnostics.probeDoh()
                }
                pref.isEnabled = true
                pref.summary = result.message
                Toast.makeText(
                    context,
                    if (result.ok) R.string.platform_test_ok else R.string.platform_test_fail,
                    Toast.LENGTH_SHORT,
                ).show()
            }
            true
        }

        findPreference("NETWORK_TEST_PROVIDER")?.setOnPreferenceClickListener { pref ->
            pref.isEnabled = false
            pref.summary = context.getString(R.string.connection_probe_running)
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    ConnectionDiagnostics.probeProvider()
                }
                pref.isEnabled = true
                pref.summary = result.message
                Toast.makeText(
                    context,
                    if (result.ok) R.string.platform_test_ok else R.string.platform_test_fail,
                    Toast.LENGTH_SHORT,
                ).show()
            }
            true
        }

        findPreference("NETWORK_TEST_SUBDL")?.setOnPreferenceClickListener { pref ->
            pref.isEnabled = false
            pref.summary = context.getString(R.string.connection_probe_running)
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    ConnectionDiagnostics.probeSubdl()
                }
                pref.isEnabled = true
                pref.summary = result.message
                Toast.makeText(
                    context,
                    if (result.ok) R.string.platform_test_ok else R.string.platform_test_fail,
                    Toast.LENGTH_SHORT,
                ).show()
            }
            true
        }

        findPreference("NETWORK_TEST_SERVICES")?.setOnPreferenceClickListener { pref ->
            pref.isEnabled = false
            pref.summary = context.getString(R.string.connection_probe_running)
            scope.launch {
                val report = withContext(Dispatchers.IO) {
                    ConnectionDiagnostics.probeAllIntegrations()
                }
                pref.isEnabled = true
                pref.summary = report.title
                applyLastProbe(findPreference, report)
                AlertDialog.Builder(context)
                    .setTitle(report.title)
                    .setMessage(report.lines.joinToString("\n"))
                    .setPositiveButton(android.R.string.ok, null)
                    .setNeutralButton(R.string.connection_copy_report) { _, _ ->
                        copyReport(context, report)
                    }
                    .show()
            }
            true
        }

        findPreference("NETWORK_COPY_LAST_PROBE")?.setOnPreferenceClickListener {
            val report = ConnectionDiagnostics.lastReport()
            if (report == null) {
                Toast.makeText(context, R.string.connection_probe_idle, Toast.LENGTH_SHORT).show()
            } else {
                copyReport(context, report)
            }
            true
        }

        findPreference("NETWORK_CLEAR_CACHE")?.setOnPreferenceClickListener {
            CacheUtils.clearAppCache(context)
            Toast.makeText(context, R.string.clear_cache_done, Toast.LENGTH_SHORT).show()
            true
        }

        findPreference("NETWORK_CLEAR_COOKIES")?.setOnPreferenceClickListener {
            AlertDialog.Builder(context)
                .setMessage(R.string.connection_clear_cookies_confirm)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    runCatching {
                        CookieManager.getInstance().removeAllCookies(null)
                        CookieManager.getInstance().flush()
                    }
                    Toast.makeText(context, R.string.connection_cookies_cleared, Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            true
        }

        findPreference("NETWORK_OPEN_INTEGRATIONS")?.setOnPreferenceClickListener {
            openScreen?.invoke("screen_platform")
            true
        }

        findPreference("NETWORK_OPEN_TMDB")?.setOnPreferenceClickListener {
            openScreen?.invoke("screen_content")
            true
        }

        findPreference("NETWORK_OPEN_OPENSUBTITLES")?.setOnPreferenceClickListener {
            openScreen?.invoke("screen_platform_subtitles")
            true
        }

        findPreference("NETWORK_OPEN_TRAKT")?.setOnPreferenceClickListener {
            openScreen?.invoke("screen_platform_trakt")
            true
        }

        findPreference("NETWORK_OPEN_DEBRID")?.setOnPreferenceClickListener {
            openScreen?.invoke("screen_platform_debrid")
            true
        }

        findPreference("NETWORK_OPEN_JELLYFIN")?.setOnPreferenceClickListener {
            openScreen?.invoke("screen_platform_jellyfin")
            true
        }

        findPreference("NETWORK_OPEN_PLEX")?.setOnPreferenceClickListener {
            openScreen?.invoke("screen_platform_plex")
            true
        }

        refresh(findPreference, context)
    }

    fun refresh(findPreference: (String) -> Preference?, context: Context) {
        refreshStatus(findPreference, context)
        (findPreference("p_doh_provider_url") as? ListPreference)?.apply {
            value = UserPreferences.dohProviderUrl
            summary = entry
        }
        val subdl = UserPreferences.subdlApiKey
        findPreference("SUBDL_API_KEY")?.summary = maskedKeySummary(context, subdl)
        val bypass = UserPreferences.bypassWsAdvertisedHost
        findPreference("BYPASS_WS_ADVERTISED_HOST")?.summary = if (bypass.isBlank()) {
            context.getString(R.string.settings_bypass_advertised_host_auto)
        } else {
            bypass
        }
        findPreference("NETWORK_TMDB_STATUS")?.summary =
            IntegrationStatus.label(context, IntegrationStatus.tmdb())
        findPreference("NETWORK_INTEGRATIONS_STATUS")?.summary =
            IntegrationStatus.hubOverview(context)
        findPreference("NETWORK_PROVIDER_STATUS")?.summary =
            ConnectionDiagnostics.providerStatusSummary(context)
        findPreference("NETWORK_VALIDATED")?.summary =
            ConnectionDiagnostics.validatedLabel(context)
        ConnectionDiagnostics.lastReport()?.let { applyLastProbe(findPreference, it) }
    }

    private fun refreshStatus(
        findPreference: (String) -> Preference?,
        context: Context,
    ) {
        findPreference("NETWORK_STATUS")?.summary =
            ConnectionDiagnostics.statusSummary(context)
        val snap = ConnectionDiagnostics.networkSnapshot(context)
        findPreference("NETWORK_DOH_STATUS")?.summary = if (snap.dohEnabled) {
            context.getString(R.string.connection_doh_on, snap.dohProvider)
        } else {
            context.getString(R.string.connection_doh_off)
        }
        findPreference("NETWORK_VALIDATED")?.summary =
            ConnectionDiagnostics.validatedLabel(context)
        findPreference("NETWORK_PROVIDER_STATUS")?.summary =
            ConnectionDiagnostics.providerStatusSummary(context)
    }

    private fun applyLastProbe(
        findPreference: (String) -> Preference?,
        report: ConnectionDiagnostics.ProbeReport,
    ) {
        val summary = report.latencyMs?.let { ms ->
            "${report.asSummary(5)} · ${ms}ms"
        } ?: report.asSummary(6)
        findPreference("NETWORK_LAST_PROBE")?.summary = summary
    }

    private fun copyReport(context: Context, report: ConnectionDiagnostics.ProbeReport) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(
            ClipData.newPlainText("BetterStreamflix diagnostics", report.asClipboardText()),
        )
        Toast.makeText(context, R.string.connection_report_copied, Toast.LENGTH_SHORT).show()
    }

    private fun maskedKeySummary(context: Context, value: String): String =
        if (value.isBlank()) {
            context.getString(R.string.settings_subdl_api_key_summary)
        } else {
            "••••••••"
        }
}
