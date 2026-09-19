package com.dskja.betterstreamflix.fragments.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreference
import androidx.preference.SwitchPreferenceCompat
import com.dskja.betterstreamflix.R
import com.dskja.betterstreamflix.platform.jellyfin.JellyfinApi
import com.dskja.betterstreamflix.platform.plugins.PluginRegistry
import com.dskja.betterstreamflix.platform.trakt.TraktClient
import com.dskja.betterstreamflix.platform.trakt.TraktConfig
import com.dskja.betterstreamflix.utils.UserPreferences
import kotlinx.coroutines.launch

object PlatformSettingsController {
    fun bind(
        fragment: Fragment,
        scope: LifecycleCoroutineScope,
        findPreference: (String) -> Preference?,
    ) {
        val context = fragment.requireContext()

        fun bindSwitch(key: String, get: () -> Boolean, set: (Boolean) -> Unit) {
            val pref = findPreference(key)
            when (pref) {
                is SwitchPreferenceCompat -> {
                    pref.isChecked = get()
                    pref.setOnPreferenceChangeListener { _, v ->
                        set(v as Boolean)
                        false
                    }
                }
                is SwitchPreference -> {
                    pref.isChecked = get()
                    pref.setOnPreferenceChangeListener { _, v ->
                        set(v as Boolean)
                        false
                    }
                }
            }
        }

        fun isHttpUrl(value: String): Boolean =
            value.isBlank() || value.startsWith("http://") || value.startsWith("https://")

        fun bindText(
            key: String,
            get: () -> String,
            set: (String) -> Unit,
            mask: Boolean = false,
            validateUrl: Boolean = false,
        ) {
            val pref = findPreference(key) as? EditTextPreference ?: return
            val value = get()
            pref.text = value
            pref.summary = when {
                value.isBlank() -> pref.context.getString(defaultSummaryRes(key))
                mask -> "••••••••"
                else -> value
            }
            pref.setOnPreferenceChangeListener { preference, newValue ->
                var typed = newValue.toString().trim().trimEnd('/')
                if (validateUrl && !isHttpUrl(typed)) {
                    Toast.makeText(context, R.string.platform_url_invalid, Toast.LENGTH_LONG).show()
                    return@setOnPreferenceChangeListener false
                }
                set(typed)
                preference.summary = when {
                    typed.isBlank() -> preference.context.getString(defaultSummaryRes(key))
                    mask -> "••••••••"
                    else -> typed
                }
                Toast.makeText(context, R.string.platform_settings_saved, Toast.LENGTH_SHORT).show()
                runCatching {
                    PluginRegistry.clear()
                    PluginRegistry.bootstrapBuiltins()
                }
                false
            }
        }

        bindSwitch(
            key = "TRAKT_ENABLED",
            get = { UserPreferences.traktEnabled },
            set = { UserPreferences.traktEnabled = it },
        )
        bindText(
            key = "TRAKT_CLIENT_ID",
            get = { UserPreferences.traktClientId },
            set = { UserPreferences.traktClientId = it },
        )
        bindText(
            key = "TRAKT_CLIENT_SECRET",
            get = { UserPreferences.traktClientSecret },
            set = { UserPreferences.traktClientSecret = it },
            mask = true,
        )
        bindText(
            key = "TRAKT_ACCESS_TOKEN",
            get = { UserPreferences.traktAccessToken },
            set = { UserPreferences.traktAccessToken = it },
            mask = true,
        )
        bindText(
            key = "JELLYFIN_BASE_URL",
            get = { UserPreferences.jellyfinBaseUrl },
            set = { UserPreferences.jellyfinBaseUrl = it },
            validateUrl = true,
        )
        bindText(
            key = "JELLYFIN_USER_ID",
            get = { UserPreferences.jellyfinUserId },
            set = { UserPreferences.jellyfinUserId = it },
        )
        bindText(
            key = "JELLYFIN_ACCESS_TOKEN",
            get = { UserPreferences.jellyfinAccessToken },
            set = { UserPreferences.jellyfinAccessToken = it },
            mask = true,
        )
        bindText(
            key = "PLEX_BASE_URL",
            get = { UserPreferences.plexBaseUrl },
            set = { UserPreferences.plexBaseUrl = it },
            validateUrl = true,
        )
        bindText(
            key = "PLEX_TOKEN",
            get = { UserPreferences.plexToken },
            set = { UserPreferences.plexToken = it },
            mask = true,
        )
        bindSwitch(
            key = "DEBRID_ENABLED",
            get = { UserPreferences.debridEnabled },
            set = { UserPreferences.debridEnabled = it },
        )
        bindText(
            key = "REAL_DEBRID_TOKEN",
            get = { UserPreferences.realDebridToken },
            set = { UserPreferences.realDebridToken = it },
            mask = true,
        )
        bindText(
            key = "PREMIUMIZE_API_KEY",
            get = { UserPreferences.premiumizeApiKey },
            set = { UserPreferences.premiumizeApiKey = it },
            mask = true,
        )
        bindText(
            key = "ALLDEBRID_API_KEY",
            get = { UserPreferences.allDebridApiKey },
            set = { UserPreferences.allDebridApiKey = it },
            mask = true,
        )
        bindText(
            key = "TORBOX_API_KEY",
            get = { UserPreferences.torBoxApiKey },
            set = { UserPreferences.torBoxApiKey = it },
            mask = true,
        )
        bindSwitch(
            key = "SIMKL_ENABLED",
            get = { UserPreferences.simklEnabled },
            set = { UserPreferences.simklEnabled = it },
        )
        bindText(
            key = "SIMKL_CLIENT_ID",
            get = { UserPreferences.simklClientId },
            set = { UserPreferences.simklClientId = it },
        )
        bindText(
            key = "SIMKL_ACCESS_TOKEN",
            get = { UserPreferences.simklAccessToken },
            set = { UserPreferences.simklAccessToken = it },
            mask = true,
        )
        bindText(
            key = "OPENSUBTITLES_API_KEY",
            get = { UserPreferences.openSubtitlesApiKey },
            set = { UserPreferences.openSubtitlesApiKey = it },
            mask = true,
        )
        bindText(
            key = "OPENSUBTITLES_LANGUAGES",
            get = { UserPreferences.openSubtitlesLanguages },
            set = { UserPreferences.openSubtitlesLanguages = it },
        )
        bindSwitch(
            key = "CAST_QUEUE_NEXT_EPISODE",
            get = { UserPreferences.castQueueNextEpisode },
            set = { UserPreferences.castQueueNextEpisode = it },
        )

        (findPreference("PLAYER_BACKEND") as? ListPreference)?.apply {
            value = UserPreferences.playerBackend
            summary = entry
            setOnPreferenceChangeListener { preference, newValue ->
                UserPreferences.playerBackend = newValue.toString()
                val list = preference as ListPreference
                val idx = list.entryValues.indexOf(newValue.toString()).coerceAtLeast(0)
                list.summary = list.entries.getOrNull(idx)
                true
            }
        }

        (findPreference("DEBRID_PROVIDER") as? ListPreference)?.apply {
            value = UserPreferences.debridProvider
            summary = entry
            setOnPreferenceChangeListener { preference, newValue ->
                UserPreferences.debridProvider = newValue.toString()
                val list = preference as ListPreference
                val idx = list.entryValues.indexOf(newValue.toString()).coerceAtLeast(0)
                list.summary = list.entries.getOrNull(idx)
                true
            }
        }

        (findPreference("SELF_HOST_PROGRESS_INTERVAL") as? ListPreference)?.apply {
            value = UserPreferences.selfHostProgressIntervalMs.toString()
            summary = entry
            setOnPreferenceChangeListener { preference, newValue ->
                UserPreferences.selfHostProgressIntervalMs =
                    newValue.toString().toLongOrNull() ?: 15_000L
                val list = preference as ListPreference
                val idx = list.entryValues.indexOf(newValue.toString()).coerceAtLeast(0)
                list.summary = list.entries.getOrNull(idx)
                true
            }
        }

        findPreference("trakt_device_auth_help")?.setOnPreferenceClickListener {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(TraktConfig.authorizeHelpUrl(context))),
            )
            true
        }

        findPreference("trakt_device_auth_start")?.setOnPreferenceClickListener {
            if (UserPreferences.traktClientId.isBlank() || UserPreferences.traktClientSecret.isBlank()) {
                Toast.makeText(context, R.string.platform_trakt_device_auth_failed, Toast.LENGTH_LONG).show()
                return@setOnPreferenceClickListener true
            }
            scope.launch {
                val code = TraktClient.requestDeviceCode()
                if (code == null) {
                    Toast.makeText(context, R.string.platform_trakt_device_auth_failed, Toast.LENGTH_LONG).show()
                    return@launch
                }
                AlertDialog.Builder(context)
                    .setTitle(R.string.platform_trakt_device_auth_title)
                    .setMessage(
                        context.getString(
                            R.string.platform_trakt_device_auth_code_message,
                            code.userCode,
                            code.verificationUrl,
                        ),
                    )
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(code.verificationUrl)),
                )
                val ok = TraktClient.pollDeviceToken(
                    deviceCode = code.deviceCode,
                    intervalSeconds = code.intervalSeconds,
                    expiresInSeconds = code.expiresInSeconds,
                )
                Toast.makeText(
                    context,
                    if (ok) R.string.platform_trakt_device_auth_success
                    else R.string.platform_trakt_device_auth_failed,
                    Toast.LENGTH_LONG,
                ).show()
                if (ok) {
                    bindText(
                        key = "TRAKT_ACCESS_TOKEN",
                        get = { UserPreferences.traktAccessToken },
                        set = { UserPreferences.traktAccessToken = it },
                        mask = true,
                    )
                }
            }
            true
        }

        findPreference("jellyfin_login_submit")?.setOnPreferenceClickListener {
            val userPref = findPreference("jellyfin_login_user") as? EditTextPreference
            val passPref = findPreference("jellyfin_login_password") as? EditTextPreference
            val user = userPref?.text.orEmpty()
            val pass = passPref?.text.orEmpty()
            if (UserPreferences.jellyfinBaseUrl.isBlank() || user.isBlank() || pass.isBlank()) {
                Toast.makeText(context, R.string.platform_jellyfin_login_missing, Toast.LENGTH_LONG).show()
                return@setOnPreferenceClickListener true
            }
            scope.launch {
                val ok = JellyfinApi().authenticateByName(user, pass)
                Toast.makeText(
                    context,
                    if (ok) R.string.platform_jellyfin_login_success
                    else R.string.platform_jellyfin_login_failed,
                    Toast.LENGTH_LONG,
                ).show()
                if (ok) {
                    passPref?.text = ""
                    bindText(
                        key = "JELLYFIN_USER_ID",
                        get = { UserPreferences.jellyfinUserId },
                        set = { UserPreferences.jellyfinUserId = it },
                    )
                    bindText(
                        key = "JELLYFIN_ACCESS_TOKEN",
                        get = { UserPreferences.jellyfinAccessToken },
                        set = { UserPreferences.jellyfinAccessToken = it },
                        mask = true,
                    )
                    runCatching {
                        PluginRegistry.clear()
                        PluginRegistry.bootstrapBuiltins()
                    }
                }
            }
            true
        }

        findPreference("jellyfin_quick_connect")?.setOnPreferenceClickListener {
            if (UserPreferences.jellyfinBaseUrl.isBlank()) {
                Toast.makeText(context, R.string.platform_jellyfin_qc_missing_url, Toast.LENGTH_LONG).show()
                return@setOnPreferenceClickListener true
            }
            scope.launch {
                val api = JellyfinApi()
                val started = api.initiateQuickConnect()
                if (started == null) {
                    Toast.makeText(context, R.string.platform_jellyfin_qc_failed, Toast.LENGTH_LONG).show()
                    return@launch
                }
                AlertDialog.Builder(context)
                    .setTitle(R.string.platform_jellyfin_qc_title)
                    .setMessage(
                        context.getString(R.string.platform_jellyfin_qc_code_message, started.code),
                    )
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                val ok = api.pollQuickConnect(started.secret)
                Toast.makeText(
                    context,
                    if (ok) R.string.platform_jellyfin_qc_success
                    else R.string.platform_jellyfin_qc_failed,
                    Toast.LENGTH_LONG,
                ).show()
                if (ok) {
                    bindText(
                        key = "JELLYFIN_USER_ID",
                        get = { UserPreferences.jellyfinUserId },
                        set = { UserPreferences.jellyfinUserId = it },
                    )
                    bindText(
                        key = "JELLYFIN_ACCESS_TOKEN",
                        get = { UserPreferences.jellyfinAccessToken },
                        set = { UserPreferences.jellyfinAccessToken = it },
                        mask = true,
                    )
                    runCatching {
                        PluginRegistry.clear()
                        PluginRegistry.bootstrapBuiltins()
                    }
                }
            }
            true
        }

        bindSwitch(
            key = "plugin_disable_jellyfin",
            get = { UserPreferences.isPluginDisabled("builtin:Jellyfin") },
            set = {
                UserPreferences.setPluginDisabled("builtin:Jellyfin", it)
                runCatching {
                    PluginRegistry.clear()
                    PluginRegistry.bootstrapBuiltins()
                }
            },
        )
        bindSwitch(
            key = "plugin_disable_plex",
            get = { UserPreferences.isPluginDisabled("builtin:Plex") },
            set = {
                UserPreferences.setPluginDisabled("builtin:Plex", it)
                runCatching {
                    PluginRegistry.clear()
                    PluginRegistry.bootstrapBuiltins()
                }
            },
        )

        findPreference("platform_plugin_catalog")?.setOnPreferenceClickListener {
            runCatching {
                com.dskja.betterstreamflix.platform.plugins.PluginCatalog.reload(context)
            }
            val entries = com.dskja.betterstreamflix.platform.plugins.PluginCatalog.loadMerged(context)
            val lines = if (entries.isEmpty()) {
                listOf(context.getString(R.string.platform_plugin_catalog_empty))
            } else {
                entries.map { entry ->
                    com.dskja.betterstreamflix.platform.plugins.PluginCatalog.summaryLine(
                        entry,
                        UserPreferences.isPluginDisabled(entry.id),
                    )
                }
            }
            AlertDialog.Builder(context)
                .setTitle(R.string.platform_plugin_catalog_title_dialog)
                .setItems(lines.toTypedArray(), null)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            true
        }

        findPreference("platform_plugin_reload")?.setOnPreferenceClickListener {
            val results = runCatching {
                com.dskja.betterstreamflix.platform.plugins.PluginCatalog.reload(context)
                com.dskja.betterstreamflix.platform.plugins.PluginApkLoader.loadInstalled(context)
            }.getOrElse { emptyList() }
            val ok = results.count { it.success }
            Toast.makeText(
                context,
                context.getString(R.string.platform_plugin_reload_ok, ok),
                Toast.LENGTH_SHORT,
            ).show()
            runCatching {
                com.dskja.betterstreamflix.utils.ProviderChangeNotifier.notifyProviderChanged()
            }
            true
        }

        findPreference("platform_plugin_install_local")?.setOnPreferenceClickListener {
            val entries = com.dskja.betterstreamflix.platform.plugins.PluginCatalog.loadMerged(context)
                .filter {
                    it.source == com.dskja.betterstreamflix.platform.plugins.PluginManifest.Source.LOCAL &&
                        it.sha256.isNotBlank() &&
                        it.entryClass.isNotBlank()
                }
            if (entries.isEmpty()) {
                val hint = com.dskja.betterstreamflix.platform.plugins.PluginApkLoader.apksDir(context)
                    .absolutePath
                Toast.makeText(
                    context,
                    context.getString(
                        R.string.platform_plugin_install_fail,
                        "catalog needs sha256+entryClass; APK path: $hint",
                    ),
                    Toast.LENGTH_LONG,
                ).show()
                return@setOnPreferenceClickListener true
            }
            val labels = entries.map { "${it.name} (${it.id})" }.toTypedArray()
            AlertDialog.Builder(context)
                .setTitle(R.string.platform_plugin_install_local_title)
                .setItems(labels) { _, which ->
                    val entry = entries[which]
                    val apk = com.dskja.betterstreamflix.platform.plugins.PluginApkLoader
                        .apkFileFor(context, entry.id)
                    val result = if (!apk.isFile) {
                        com.dskja.betterstreamflix.platform.plugins.PluginApkLoader.LoadResult(
                            entry.id,
                            entry.name,
                            false,
                            apk.absolutePath,
                        )
                    } else {
                        com.dskja.betterstreamflix.platform.plugins.PluginApkLoader
                            .loadVerified(context, apk, entry)
                    }
                    Toast.makeText(
                        context,
                        if (result.success) {
                            context.getString(R.string.platform_plugin_install_ok, result.name)
                        } else {
                            context.getString(R.string.platform_plugin_install_fail, result.message)
                        },
                        Toast.LENGTH_LONG,
                    ).show()
                    if (result.success) {
                        runCatching {
                            com.dskja.betterstreamflix.utils.ProviderChangeNotifier.notifyProviderChanged()
                        }
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            true
        }

        findPreference("platform_test_jellyfin")?.setOnPreferenceClickListener {
            scope.launch {
                val ok = runCatching {
                    JellyfinApi().configured() && JellyfinApi().resumeItems(1).length() >= 0
                }.getOrDefault(false)
                Toast.makeText(
                    context,
                    if (ok) R.string.platform_test_ok else R.string.platform_test_fail,
                    Toast.LENGTH_SHORT,
                ).show()
            }
            true
        }
        findPreference("platform_test_plex")?.setOnPreferenceClickListener {
            scope.launch {
                val ok = runCatching {
                    val api = com.dskja.betterstreamflix.platform.plex.PlexApi()
                    api.configured() && api.librarySections().length() >= 0
                }.getOrDefault(false)
                Toast.makeText(
                    context,
                    if (ok) R.string.platform_test_ok else R.string.platform_test_fail,
                    Toast.LENGTH_SHORT,
                ).show()
            }
            true
        }
    }

    private fun defaultSummaryRes(key: String): Int = when (key) {
        "TRAKT_CLIENT_ID" -> R.string.platform_trakt_client_id_summary
        "TRAKT_CLIENT_SECRET" -> R.string.platform_trakt_client_secret_summary
        "TRAKT_ACCESS_TOKEN" -> R.string.platform_trakt_token_summary
        "JELLYFIN_BASE_URL" -> R.string.platform_jellyfin_url_summary
        "JELLYFIN_USER_ID" -> R.string.platform_jellyfin_user_id_summary
        "JELLYFIN_ACCESS_TOKEN" -> R.string.platform_jellyfin_token_summary
        "PLEX_BASE_URL" -> R.string.platform_plex_url_summary
        "PLEX_TOKEN" -> R.string.platform_plex_token_summary
        "REAL_DEBRID_TOKEN" -> R.string.platform_rd_token_summary
        "PREMIUMIZE_API_KEY" -> R.string.platform_premiumize_key_summary
        "ALLDEBRID_API_KEY" -> R.string.platform_alldebrid_key_summary
        "TORBOX_API_KEY" -> R.string.platform_torbox_key_summary
        "SIMKL_CLIENT_ID" -> R.string.platform_simkl_client_id_summary
        "SIMKL_ACCESS_TOKEN" -> R.string.platform_simkl_token_summary
        "OPENSUBTITLES_API_KEY" -> R.string.platform_opensubtitles_key_summary
        "OPENSUBTITLES_LANGUAGES" -> R.string.platform_opensubtitles_langs_summary
        else -> R.string.platform_settings_summary
    }
}
