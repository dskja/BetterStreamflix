package com.betterstreamflix.fragments.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.betterstreamflix.R
import com.betterstreamflix.activities.main.MainTvActivity
import com.betterstreamflix.analytics.AnalyticsManager
import com.betterstreamflix.backup.BackupRestoreManager
import com.betterstreamflix.backup.ProviderBackupContext
import com.betterstreamflix.compose.ComposeHostFragment
import com.betterstreamflix.compose.screens.SettingsActions
import com.betterstreamflix.compose.screens.SettingsDestination
import com.betterstreamflix.compose.screens.SettingsExperience
import com.betterstreamflix.database.AppDatabase
import com.betterstreamflix.providers.Provider
import com.betterstreamflix.providers.TmdbProvider
import com.betterstreamflix.utils.AppLanguageManager
import com.betterstreamflix.utils.CacheUtils
import com.betterstreamflix.utils.UserDataCache
import com.betterstreamflix.utils.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsTvFragment : ComposeHostFragment() {

    private lateinit var backupRestoreManager: BackupRestoreManager
    private var backupLoadingDialog: AlertDialog? = null
    private var destinationState by mutableStateOf(SettingsDestination.Hub)
    private var revision by mutableIntStateOf(0)

    private val exportBackupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let { lifecycleScope.launch { performBackupExport(it) } } }

    private val importBackupLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { lifecycleScope.launch { performBackupImport(it) } } }

    private val exportDbBackupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> uri?.let { lifecycleScope.launch { performDatabaseBackupExport(it) } } }

    private val importDbBackupLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { lifecycleScope.launch { performDatabaseBackupImport(it) } } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val providers = Provider.providers.keys.toMutableList().apply {
            listOf("it", "en", "es", "de", "fr").forEach { add(TmdbProvider(it)) }
        }
        backupRestoreManager = BackupRestoreManager(
            requireContext(),
            providers.mapNotNull { provider ->
                runCatching {
                    val db = AppDatabase.getInstanceForProvider(provider.name, requireContext())
                    ProviderBackupContext(
                        name = provider.name,
                        movieDao = db.movieDao(),
                        tvShowDao = db.tvShowDao(),
                        episodeDao = db.episodeDao(),
                        seasonDao = db.seasonDao(),
                        provider = provider,
                    )
                }.onFailure {
                    Log.w("BackupRestore", "Skipping ${provider.name}: ${it.message}")
                }.getOrNull()
            },
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (destinationState != SettingsDestination.Hub) {
                        destinationState = SettingsDestination.Hub
                    } else {
                        findNavController().navigate(R.id.providers)
                    }
                }
            },
        )
    }

    @Composable
    override fun ScreenContent() {
        val tick = revision
        val destination = destinationState
        val state = remember(tick) { SettingsComposeBridge.buildState(requireContext()) }

        val actions = remember(tick, destination) {
            SettingsActions(
                onBack = {
                    if (destinationState != SettingsDestination.Hub) {
                        destinationState = SettingsDestination.Hub
                    } else {
                        findNavController().navigate(R.id.providers)
                    }
                },
                onOpenDownloads = { findNavController().navigate(R.id.downloads) },
                onOpenAbout = { findNavController().navigate(R.id.action_settings_to_settings_about) },
                onOpenHelp = {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/dskja/BetterStreamflix")))
                },
                onOpenTelegram = {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/BetterStreamflix")))
                },
                onScanResolverQr = {
                    Toast.makeText(requireContext(), R.string.settings_scan_resolver_qr_title, Toast.LENGTH_SHORT).show()
                },
                onExportBackup = {
                    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                    runCatching { exportBackupLauncher.launch("betterstreamflix-backup-$stamp.json") }
                        .onFailure {
                            Toast.makeText(requireContext(), R.string.backup_picker_unavailable, Toast.LENGTH_LONG).show()
                        }
                },
                onImportBackup = {
                    runCatching { importBackupLauncher.launch(arrayOf("application/json", "*/*")) }
                        .onFailure {
                            Toast.makeText(requireContext(), R.string.backup_picker_unavailable, Toast.LENGTH_LONG).show()
                        }
                },
                onExportDb = {
                    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                    runCatching { exportDbBackupLauncher.launch("betterstreamflix-db-$stamp.zip") }
                        .onFailure {
                            Toast.makeText(requireContext(), R.string.backup_picker_unavailable, Toast.LENGTH_LONG).show()
                        }
                },
                onImportDb = {
                    runCatching { importDbBackupLauncher.launch(arrayOf("application/zip", "*/*")) }
                        .onFailure {
                            Toast.makeText(requireContext(), R.string.backup_picker_unavailable, Toast.LENGTH_LONG).show()
                        }
                },
                onClearCache = {
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) { CacheUtils.clearAppCache(requireContext()) }
                        UserDataCache.clearAll(requireContext())
                        Toast.makeText(requireContext(), R.string.clear_cache_done, Toast.LENGTH_SHORT).show()
                        bump()
                    }
                },
                onThemeSelected = { themeId ->
                    UserPreferences.selectedTheme = themeId
                    requireActivity().apply {
                        finish()
                        startActivity(
                            Intent(this, MainTvActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                            },
                        )
                    }
                },
                onLanguageSelected = { language ->
                    AppLanguageManager.setSelectedLanguage(language)
                    requireActivity().apply {
                        finish()
                        startActivity(
                            Intent(this, MainTvActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                            },
                        )
                    }
                },
                onDohSelected = { url ->
                    UserPreferences.dohProviderUrl = url
                    Toast.makeText(requireContext(), R.string.doh_provider_updated, Toast.LENGTH_LONG).show()
                    bump()
                },
                onQualitySelected = { height ->
                    UserPreferences.qualityHeight = height.toIntOrNull()
                    bump()
                },
                onToggle = { key, value ->
                    SettingsComposeBridge.applyToggle(requireContext(), key, value)
                    bump()
                },
                onEditText = { key, value ->
                    SettingsComposeBridge.applyEditText(key, value)
                    bump()
                },
                onAction = { key ->
                    when (key) {
                        "shareDiagnostics" -> AnalyticsManager.shareDiagnosticReport(requireContext())
                        "clearNewContentHistory" -> {
                            com.betterstreamflix.notifications.NewContentNotifier.clearSeenContent(requireContext())
                            Toast.makeText(
                                requireContext(),
                                R.string.settings_new_content_clear_history_done,
                                Toast.LENGTH_SHORT,
                            ).show()
                            bump()
                        }
                        "openAccessibilitySettings" -> {
                            com.betterstreamflix.accessibility.AccessibilityHelper.openAccessibilitySettings(requireContext())
                        }
                        "openDisplaySettings" -> {
                            com.betterstreamflix.accessibility.AccessibilityHelper.openDisplaySettings(requireContext())
                        }
                        else -> {
                            SettingsComposeBridge.applyAction(key)
                            bump()
                        }
                    }
                },
                onRefresh = {
                    lifecycleScope.launch {
                        withContext(Dispatchers.IO) { CacheUtils.clearAppCache(requireContext()) }
                        Toast.makeText(requireContext(), R.string.settings_refresh_cache_success, Toast.LENGTH_SHORT).show()
                        bump()
                    }
                },
            )
        }

        SettingsExperience(
            destination = destination,
            onNavigate = { destinationState = it },
            state = state,
            actions = actions,
            isTv = true,
        )
    }

    private fun bump() {
        revision += 1
    }

    private suspend fun performBackupExport(uri: Uri) {
        withBackupLoading(R.string.backup_export_title) {
            val jsonData = withContext(Dispatchers.IO) { backupRestoreManager.exportUserData() }
            if (jsonData != null) {
                runCatching {
                    requireContext().contentResolver.openOutputStream(uri)?.use { it.writer().use { w -> w.write(jsonData) } }
                    Toast.makeText(requireContext(), R.string.backup_export_success, Toast.LENGTH_LONG).show()
                }.onFailure {
                    Toast.makeText(requireContext(), R.string.backup_export_error_write, Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(requireContext(), R.string.backup_data_not_generated, Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun performBackupImport(uri: Uri) {
        withBackupLoading(R.string.backup_import_title) {
            val jsonData = withContext(Dispatchers.IO) {
                requireContext().contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            }
            if (jsonData.isBlank()) {
                Toast.makeText(requireContext(), R.string.backup_import_empty_file, Toast.LENGTH_LONG).show()
                return@withBackupLoading
            }
            val success = withContext(Dispatchers.IO) { backupRestoreManager.importUserData(jsonData) }
            Toast.makeText(
                requireContext(),
                if (success) R.string.backup_import_success else R.string.backup_import_error,
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private suspend fun performDatabaseBackupExport(uri: Uri) {
        withBackupLoading(R.string.backup_db_export_title) {
            val zipData = withContext(Dispatchers.IO) { backupRestoreManager.exportDatabaseZip() }
            if (zipData != null) {
                runCatching {
                    requireContext().contentResolver.openOutputStream(uri)?.use { it.write(zipData) }
                    Toast.makeText(requireContext(), R.string.backup_db_export_success, Toast.LENGTH_LONG).show()
                }.onFailure {
                    Toast.makeText(requireContext(), R.string.backup_export_error_write, Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(requireContext(), R.string.backup_data_not_generated, Toast.LENGTH_LONG).show()
            }
        }
    }

    private suspend fun performDatabaseBackupImport(uri: Uri) {
        withBackupLoading(R.string.backup_db_import_title) {
            val zipBytes = withContext(Dispatchers.IO) {
                requireContext().contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }
            if (zipBytes == null || zipBytes.isEmpty()) {
                Toast.makeText(requireContext(), R.string.backup_import_empty_file, Toast.LENGTH_LONG).show()
                return@withBackupLoading
            }
            val success = withContext(Dispatchers.IO) { backupRestoreManager.importDatabaseZip(zipBytes) }
            Toast.makeText(
                requireContext(),
                if (success) R.string.backup_db_import_success else R.string.backup_import_error,
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    private suspend fun <T> withBackupLoading(titleRes: Int, block: suspend () -> T): T {
        if (isAdded) {
            backupLoadingDialog = AlertDialog.Builder(requireContext())
                .setTitle(titleRes)
                .setMessage(R.string.settings_refresh_cache_message)
                .setCancelable(false)
                .create()
                .also { it.show() }
        }
        return try {
            block()
        } finally {
            backupLoadingDialog?.dismiss()
            backupLoadingDialog = null
        }
    }
}
