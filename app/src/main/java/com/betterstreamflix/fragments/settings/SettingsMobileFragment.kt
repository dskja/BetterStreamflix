package com.betterstreamflix.fragments.settings

import android.app.Activity
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.betterstreamflix.R
import com.betterstreamflix.activities.main.MainMobileActivity
import com.betterstreamflix.activities.tools.QrScannerActivity
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.betterstreamflix.download.DownloadFeature
import com.betterstreamflix.notifications.NewContentNotifier
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
import com.betterstreamflix.sync.TraktSettings
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

class SettingsMobileFragment : ComposeHostFragment() {

    private lateinit var backupRestoreManager: BackupRestoreManager
    private var backupLoadingDialog: AlertDialog? = null
    private var currentDestination: SettingsDestination = SettingsDestination.Hub
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

    private val scanResolverQrLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val rawValue = result.data?.getStringExtra(QrScannerActivity.EXTRA_QR_VALUE).orEmpty()
        val uri = rawValue.takeIf { it.startsWith("streamflix://resolve") }?.let(Uri::parse)
        if (uri == null) {
            Toast.makeText(requireContext(), R.string.settings_scan_resolver_invalid_qr, Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        startActivity(
            Intent(requireContext(), MainMobileActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = uri
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
        )
    }

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
                    if (currentDestination != SettingsDestination.Hub) {
                        currentDestination = SettingsDestination.Hub
                        bump()
                    } else {
                        findNavController().navigate(R.id.providers)
                    }
                }
            },
        )
    }

    @Composable
    override fun ScreenContent() {
        var destination by rememberSaveable { mutableStateOf(currentDestination) }
        val tick = revision
        destination = currentDestination
        val state = remember(tick) { SettingsComposeBridge.buildState(requireContext()) }

        val actions = remember(tick, destination) {
            SettingsActions(
                onBack = {
                    if (destination != SettingsDestination.Hub) {
                        destination = SettingsDestination.Hub
                        currentDestination = SettingsDestination.Hub
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
                    runCatching {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=BetterStreamflix")))
                    }.onFailure {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/BetterStreamflix")))
                    }
                },
                onScanResolverQr = {
                    scanResolverQrLauncher.launch(Intent(requireContext(), QrScannerActivity::class.java))
                },
                onExportBackup = {
                    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                    exportBackupLauncher.launch("betterstreamflix-backup-$stamp.json")
                },
                onImportBackup = { importBackupLauncher.launch(arrayOf("application/json", "*/*")) },
                onExportDb = {
                    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                    exportDbBackupLauncher.launch("betterstreamflix-db-$stamp.zip")
                },
                onImportDb = { importDbBackupLauncher.launch(arrayOf("application/zip", "*/*")) },
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
                            Intent(this, MainMobileActivity::class.java).apply {
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
                            Intent(this, MainMobileActivity::class.java).apply {
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
                    if (key == "newContentNotifications" && value) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val granted = ContextCompat.checkSelfPermission(
                                requireContext(),
                                Manifest.permission.POST_NOTIFICATIONS,
                            ) == PackageManager.PERMISSION_GRANTED
                            if (!granted) {
                                ActivityCompat.requestPermissions(
                                    requireActivity(),
                                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                                    DownloadFeature.NOTIFICATION_PERMISSION_REQUEST,
                                )
                            }
                        }
                    }
                    if (key == "immersiveMode") {
                        (activity as? MainMobileActivity)?.updateImmersiveMode()
                    }
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
                            NewContentNotifier.clearSeenContent(requireContext())
                            Toast.makeText(
                                requireContext(),
                                R.string.settings_new_content_clear_history_done,
                                Toast.LENGTH_SHORT,
                            ).show()
                            bump()
                        }
                        "openTraktSync" -> {
                            TraktSettings.setEnabled(requireContext(), !TraktSettings.isEnabled(requireContext()))
                            bump()
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
            onNavigate = {
                destination = it
                currentDestination = it
            },
            state = state,
            actions = actions,
            isTv = false,
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
        showLoading(titleRes)
        return try {
            block()
        } finally {
            showLoading(null)
        }
    }

    private fun showLoading(titleRes: Int?) {
        if (titleRes == null) {
            backupLoadingDialog?.dismiss()
            backupLoadingDialog = null
            return
        }
        if (backupLoadingDialog?.isShowing == true) {
            backupLoadingDialog?.setTitle(titleRes)
            return
        }
        backupLoadingDialog = AlertDialog.Builder(requireContext())
            .setTitle(titleRes)
            .setMessage(R.string.settings_refresh_cache_message)
            .setCancelable(false)
            .create()
            .also { it.show() }
    }
}
