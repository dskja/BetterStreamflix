package com.dskja.betterstreamflix.fragments.settings

import android.content.Context
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.dskja.betterstreamflix.R
import com.dskja.betterstreamflix.platform.plugins.PluginApkLoader
import com.dskja.betterstreamflix.platform.plugins.PluginCatalog
import com.dskja.betterstreamflix.platform.plugins.PluginManager
import com.dskja.betterstreamflix.platform.plugins.PluginManifest
import com.dskja.betterstreamflix.utils.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Rich Sources / Plugin marketplace binding for Settings.
 */
object PluginSettingsController {

    fun bind(
        fragment: Fragment,
        scope: LifecycleCoroutineScope,
        findPreference: (String) -> Preference?,
    ) {
        val context = fragment.requireContext()

        findPreference("platform_plugin_catalog")?.setOnPreferenceClickListener {
            showCatalogDialog(fragment, context)
            true
        }

        findPreference("platform_plugin_manage")?.setOnPreferenceClickListener {
            showManageDialog(fragment, context)
            true
        }

        findPreference("platform_plugin_reload")?.setOnPreferenceClickListener {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    PluginManager.reload(context)
                }
                Toast.makeText(
                    context,
                    context.getString(R.string.platform_plugin_reload_ok, ok),
                    Toast.LENGTH_SHORT,
                ).show()
                refreshDemoSwitch(findPreference)
            }
            true
        }

        findPreference("platform_plugin_install_local")?.setOnPreferenceClickListener {
            showInstallDialog(fragment, context)
            true
        }

        findPreference("platform_plugin_extensions")?.apply {
            summary = buildExtensionsSummary()
            setOnPreferenceClickListener {
                val claims = PluginManager.extractorClaims()
                val subs = PluginManager.subtitleClaims()
                val hooks = PluginManager.statuses(context)
                    .filter { it.active && it.extensions.isNotEmpty() }
                    .joinToString("\n") { "${it.name}: ${it.extensions.joinToString()}" }
                    .ifBlank { context.getString(R.string.platform_plugin_extensions_empty) }
                val message = buildString {
                    append(hooks)
                    if (claims.isNotEmpty()) {
                        append("\n\n")
                        append(context.getString(R.string.platform_plugin_extractors_claimed))
                        append("\n")
                        append(claims.joinToString(", "))
                    }
                    if (subs.isNotEmpty()) {
                        append("\n\n")
                        append(context.getString(R.string.platform_plugin_subtitles_claimed))
                        append("\n")
                        append(subs.joinToString(", "))
                    }
                    append("\n\n")
                    append(PluginManager.diagnostics())
                }
                AlertDialog.Builder(context)
                    .setTitle(R.string.platform_plugin_extensions_title)
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok, null)
                    .setNeutralButton(R.string.platform_plugin_actions_title) { _, _ ->
                        showSettingsActions(context)
                    }
                    .show()
                true
            }
        }

        (findPreference("plugin_disable_demo") as? SwitchPreferenceCompat)?.apply {
            isChecked = UserPreferences.isPluginDisabled(
                com.dskja.betterstreamflix.platform.plugins.DemoAddonPlugin.ID,
            )
            setOnPreferenceChangeListener { _, newValue ->
                val hide = newValue as Boolean
                PluginManager.setEnabled(
                    com.dskja.betterstreamflix.platform.plugins.DemoAddonPlugin.ID,
                    enabled = !hide,
                )
                true
            }
        }

        bindHideSwitch(findPreference, "plugin_disable_jellyfin", "builtin:Jellyfin")
        bindHideSwitch(findPreference, "plugin_disable_plex", "builtin:Plex")
        refreshDemoSwitch(findPreference)
    }

    private fun bindHideSwitch(
        findPreference: (String) -> Preference?,
        key: String,
        pluginId: String,
    ) {
        (findPreference(key) as? SwitchPreferenceCompat)?.apply {
            isChecked = UserPreferences.isPluginDisabled(pluginId)
            setOnPreferenceChangeListener { _, newValue ->
                PluginManager.setEnabled(pluginId, enabled = !(newValue as Boolean))
                true
            }
        }
    }

    private fun refreshDemoSwitch(findPreference: (String) -> Preference?) {
        (findPreference("plugin_disable_demo") as? SwitchPreferenceCompat)?.isChecked =
            UserPreferences.isPluginDisabled(
                com.dskja.betterstreamflix.platform.plugins.DemoAddonPlugin.ID,
            )
    }

    private fun buildExtensionsSummary(): String {
        val active = PluginManager.statuses().count { it.active && it.extensions.isNotEmpty() }
        return if (active == 0) {
            "No active extension hooks"
        } else {
            "$active addon(s) exposing home/search/playback/metadata hooks"
        }
    }

    private fun showSettingsActions(context: Context) {
        val actions = PluginManager.settingsActions()
        if (actions.isEmpty()) {
            Toast.makeText(context, R.string.platform_plugin_actions_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val labels = actions.map { (pluginId, action) ->
            "${action.label} · $pluginId"
        }.toTypedArray()
        AlertDialog.Builder(context)
            .setTitle(R.string.platform_plugin_actions_title)
            .setItems(labels) { _, which ->
                val (pluginId, action) = actions[which]
                val handled = when (pluginId) {
                    com.dskja.betterstreamflix.platform.plugins.DemoAddonPlugin.ID ->
                        com.dskja.betterstreamflix.platform.plugins.DemoAddonPlugin
                            .handleSettingsAction(action.id)
                    else -> false
                }
                Toast.makeText(
                    context,
                    if (handled) R.string.platform_plugin_action_done
                    else R.string.platform_plugin_action_unsupported,
                    Toast.LENGTH_SHORT,
                ).show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showCatalogDialog(fragment: Fragment, context: Context) {
        val statuses = PluginManager.statuses(context)
        if (statuses.isEmpty()) {
            Toast.makeText(context, R.string.platform_plugin_catalog_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val lines = statuses.map { statusLine(context, it) }.toTypedArray()
        AlertDialog.Builder(context)
            .setTitle(R.string.platform_plugin_catalog_title_dialog)
            .setItems(lines) { _, which ->
                val status = statuses[which]
                showPluginDetail(fragment, context, status)
            }
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showManageDialog(fragment: Fragment, context: Context) {
        val statuses = PluginManager.statuses(context)
            .filter {
                it.source != PluginManifest.Source.BUILTIN ||
                    it.id == com.dskja.betterstreamflix.platform.plugins.DemoAddonPlugin.ID ||
                    it.id == "builtin:Jellyfin" ||
                    it.id == "builtin:Plex"
            }
        if (statuses.isEmpty()) {
            Toast.makeText(context, R.string.platform_plugin_catalog_empty, Toast.LENGTH_SHORT).show()
            return
        }
        val labels = statuses.map { status ->
            val mark = if (status.enabled) "ON" else "OFF"
            "[$mark] ${status.name} · ${status.source.name.lowercase()}"
        }.toTypedArray()
        AlertDialog.Builder(context)
            .setTitle(R.string.platform_plugin_manage_title)
            .setItems(labels) { _, which ->
                showPluginDetail(fragment, context, statuses[which])
            }
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showPluginDetail(
        fragment: Fragment,
        context: Context,
        status: PluginManager.PluginStatus,
    ) {
        val body = buildString {
            append(status.description.ifBlank { status.name })
            append("\n\n")
            append("id: ${status.id}\n")
            if (status.author.isNotBlank()) append("author: ${status.author}\n")
            append("v${status.version} · ${status.source.name.lowercase()}\n")
            append("extensions: ${status.extensions.joinToString().ifBlank { "—" }}\n")
            if (status.settingsSummary.isNotBlank()) append("${status.settingsSummary}\n")
            if (status.lastError.isNotBlank()) append("error: ${status.lastError}\n")
            if (status.apkPresent) append("APK staged\n")
        }
        val builder = AlertDialog.Builder(context)
            .setTitle(status.name)
            .setMessage(body)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(
                if (status.enabled) R.string.platform_plugin_action_disable
                else R.string.platform_plugin_action_enable,
            ) { _, _ ->
                PluginManager.setEnabled(status.id, enabled = !status.enabled)
                Toast.makeText(
                    context,
                    if (!status.enabled) R.string.platform_plugin_enabled_toast
                    else R.string.platform_plugin_disabled_toast,
                    Toast.LENGTH_SHORT,
                ).show()
            }
        if (status.source == PluginManifest.Source.LOCAL) {
            builder.setNeutralButton(R.string.platform_plugin_action_uninstall) { _, _ ->
                PluginManager.uninstall(context, status.id)
                Toast.makeText(context, R.string.platform_plugin_uninstalled_toast, Toast.LENGTH_SHORT)
                    .show()
            }
        }
        builder.show()
    }

    private fun showInstallDialog(fragment: Fragment, context: Context) {
        val entries = PluginCatalog.loadMerged(context)
            .filter {
                it.source == PluginManifest.Source.LOCAL &&
                    it.sha256.isNotBlank() &&
                    it.entryClass.isNotBlank()
            }
        if (entries.isEmpty()) {
            val hint = PluginApkLoader.apksDir(context).absolutePath
            Toast.makeText(
                context,
                context.getString(R.string.platform_plugin_install_fail, "No pinned LOCAL entries. Stage APK under $hint"),
                Toast.LENGTH_LONG,
            ).show()
            return
        }
        val labels = entries.map { "${it.name} · v${it.version}" }.toTypedArray()
        AlertDialog.Builder(context)
            .setTitle(R.string.platform_plugin_install_local_title)
            .setItems(labels) { _, which ->
                val entry = entries[which]
                val apk = PluginApkLoader.apkFileFor(context, entry.id)
                val result = if (apk.isFile) {
                    PluginManager.installLocalApk(context, apk, entry)
                } else {
                    PluginApkLoader.loadVerified(context, apk, entry)
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
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun statusLine(context: Context, status: PluginManager.PluginStatus): String {
        val state = when {
            !status.enabled -> "hidden"
            status.active -> "active"
            status.loaded -> "registered"
            else -> "listed"
        }
        val ext = status.extensions.takeIf { it.isNotEmpty() }?.joinToString(",", "[", "]").orEmpty()
        return "${status.name} · v${status.version} · ${status.source.name.lowercase()} · $state $ext".trim()
    }
}
