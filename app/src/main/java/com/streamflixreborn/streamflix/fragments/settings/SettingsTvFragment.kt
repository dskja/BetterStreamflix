package com.streamflixreborn.streamflix.fragments.settings

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.InputType
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.leanback.preference.LeanbackPreferenceFragmentCompat
import androidx.lifecycle.lifecycleScope
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreference
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import com.streamflixreborn.streamflix.BuildConfig
import com.streamflixreborn.streamflix.R
import com.streamflixreborn.streamflix.activities.main.MainTvActivity
import com.streamflixreborn.streamflix.backup.BackupRestoreManager
import com.streamflixreborn.streamflix.backup.ProviderBackupContext
import com.streamflixreborn.streamflix.database.AppDatabase
import com.streamflixreborn.streamflix.database.dao.EpisodeDao
import com.streamflixreborn.streamflix.database.dao.MovieDao
import com.streamflixreborn.streamflix.database.dao.TvShowDao
import com.streamflixreborn.streamflix.database.dao.SeasonDao
import com.streamflixreborn.streamflix.providers.FrenchStreamProvider
import com.streamflixreborn.streamflix.providers.Provider
import com.streamflixreborn.streamflix.providers.ProviderConfigUrl
import com.streamflixreborn.streamflix.providers.ProviderPortalUrl
import com.streamflixreborn.streamflix.providers.StreamingCommunityProvider
import com.streamflixreborn.streamflix.providers.TmdbProvider
import com.streamflixreborn.streamflix.utils.DnsResolver
import com.streamflixreborn.streamflix.utils.ProviderChangeNotifier
import com.streamflixreborn.streamflix.utils.UserPreferences
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsTvFragment : LeanbackPreferenceFragmentCompat() {

    private val DEFAULT_DOMAIN_VALUE = "streamingunity.biz"
    private val DEFAULT_CUEVANA_DOMAIN_VALUE = "cuevana3.la"
    private val DEFAULT_POSEIDON_DOMAIN_VALUE = "www.poseidonhd2.co"
    private val PREFS_ERROR_VALUE = "PREFS_NOT_INIT_ERROR"

    private lateinit var db: AppDatabase
    private lateinit var movieDao: MovieDao
    private lateinit var tvShowDao: TvShowDao
    private lateinit var episodeDao: EpisodeDao
    private lateinit var seasonDao: SeasonDao
    private lateinit var backupRestoreManager: BackupRestoreManager

    private val exportBackupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let {
            viewLifecycleOwner.lifecycleScope.launch {
                performBackupExport(it)
            }
        }
    }

    private val importBackupLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            viewLifecycleOwner.lifecycleScope.launch {
                performBackupImport(it)
            }
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_tv, rootKey)

        db = AppDatabase.getInstance(requireContext())
        movieDao = db.movieDao()
        tvShowDao = db.tvShowDao()
        episodeDao = db.episodeDao()
        seasonDao = db.seasonDao()
        
        val allProvidersToBackup = Provider.providers.keys.toMutableList().apply {
            listOf("it", "en", "es", "de", "fr").forEach { lang ->
                add(TmdbProvider(lang))
            }
        }

        backupRestoreManager = BackupRestoreManager(
            requireContext(),
            allProvidersToBackup.mapNotNull { provider ->
                try {
                    val db = AppDatabase.getInstanceForProvider(provider.name, requireContext())
                    ProviderBackupContext(
                        name = provider.name,
                        movieDao = db.movieDao(),
                        tvShowDao = db.tvShowDao(),
                        episodeDao = db.episodeDao(),
                        seasonDao = db.seasonDao()
                    )
                } catch (e: Exception) {
                    Log.w("BackupRestore", "Skipping ${provider.name}: ${e.message}")
                    null
                }
            }
        )


        displaySettings()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }

    private fun displaySettings() {
        
        findPreference<PreferenceCategory>("pc_streamingcommunity_settings")?.apply {
            isVisible = UserPreferences.currentProvider is StreamingCommunityProvider
        }

        findPreference<PreferenceCategory>("pc_cuevana_settings")?.apply {
            isVisible = UserPreferences.currentProvider?.name == "Cuevana 3"
        }

        findPreference<PreferenceCategory>("pc_poseidon_settings")?.apply {
            isVisible = UserPreferences.currentProvider?.name == "Poseidonhd2"
        }

        findPreference<EditTextPreference>("provider_streamingcommunity_domain")?.apply {
            val currentValue = UserPreferences.streamingcommunityDomain
            summary = currentValue
            if (currentValue == DEFAULT_DOMAIN_VALUE || currentValue == PREFS_ERROR_VALUE) {
                text = null
            } else {
                text = currentValue
            }
            setOnPreferenceChangeListener { preference, newValue ->
                val typed = (newValue as String).trim()
                val BLOCKED = listOf("streamingcommunityz.green", "streamingunity.club")
                val effectiveDomain = if (BLOCKED.any { typed.contains(it) }) DEFAULT_DOMAIN_VALUE else typed
                UserPreferences.streamingcommunityDomain = effectiveDomain
                preference.summary = effectiveDomain
                if (effectiveDomain != typed) {
                    findPreference<EditTextPreference>("provider_streamingcommunity_domain")?.text = null
                    Toast.makeText(requireContext(), getString(R.string.settings_streamingcommunity_domain_blocked), Toast.LENGTH_LONG).show()
                }
                if (UserPreferences.currentProvider is StreamingCommunityProvider) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        (UserPreferences.currentProvider as StreamingCommunityProvider).rebuildService()
                        requireActivity().apply {
                            finish()
                            startActivity(Intent(this, this::class.java))
                        }
                    }
                }
                true
            }
        }

        findPreference<Preference>("provider_streamingcommunity_domain_reset")?.setOnPreferenceClickListener {
            UserPreferences.streamingcommunityDomain = DEFAULT_DOMAIN_VALUE
            findPreference<EditTextPreference>("provider_streamingcommunity_domain")?.apply {
                summary = DEFAULT_DOMAIN_VALUE
                text = null
            }
            Toast.makeText(requireContext(), getString(R.string.settings_streamingcommunity_domain_reset_done), Toast.LENGTH_SHORT).show()
            if (UserPreferences.currentProvider is StreamingCommunityProvider) {
                viewLifecycleOwner.lifecycleScope.launch {
                    (UserPreferences.currentProvider as StreamingCommunityProvider).rebuildService()
                    requireActivity().apply {
                        finish()
                        startActivity(Intent(this, this::class.java))
                    }
                }
            }
            true
        }

        findPreference<EditTextPreference>("provider_cuevana_domain")?.apply {
            val currentValue = UserPreferences.cuevanaDomain
            summary = currentValue
            if (currentValue == DEFAULT_CUEVANA_DOMAIN_VALUE) {
                text = null
            } else {
                text = currentValue
            }
            setOnPreferenceChangeListener { preference, newValue ->
                val newDomainFromDialog = newValue as String
                UserPreferences.cuevanaDomain = newDomainFromDialog
                preference.summary = UserPreferences.cuevanaDomain
                if (UserPreferences.currentProvider?.name == "Cuevana 3") {
                    requireActivity().apply {
                        finish()
                        startActivity(Intent(this, this::class.java))
                    }
                }
                true
            }
        }

        findPreference<EditTextPreference>("provider_poseidon_domain")?.apply {
            val currentValue = UserPreferences.poseidonDomain
            summary = currentValue
            if (currentValue == DEFAULT_POSEIDON_DOMAIN_VALUE) {
                text = null
            } else {
                text = currentValue
            }
            setOnPreferenceChangeListener { preference, newValue ->
                val newDomainFromDialog = newValue as String
                UserPreferences.poseidonDomain = newDomainFromDialog
                preference.summary = UserPreferences.poseidonDomain
                if (UserPreferences.currentProvider?.name == "Poseidonhd2") {
                    requireActivity().apply {
                        finish()
                        startActivity(Intent(this, this::class.java))
                    }
                }
                true
            }
        }

        findPreference<EditTextPreference>("TMDB_API_KEY")?.apply {
            summary = if (UserPreferences.tmdbApiKey.isEmpty()) getString(R.string.settings_tmdb_api_key_summary) else UserPreferences.tmdbApiKey
            text = UserPreferences.tmdbApiKey
            setOnPreferenceChangeListener { _, newValue ->
                val newKey = (newValue as String).trim()
                UserPreferences.tmdbApiKey = newKey
                summary = if (newKey.isEmpty()) getString(R.string.settings_tmdb_api_key_summary) else newKey
                val message = if (newKey.isEmpty()) {
                    getString(R.string.settings_tmdb_api_key_reset)
                } else {
                    getString(R.string.settings_tmdb_api_key_success)
                }
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                true
            }
        }
        findPreference<SwitchPreferenceCompat>("ENABLE_TMDB")?.apply {
            isChecked = UserPreferences.enableTmdb

            setOnPreferenceChangeListener { _, newValue ->
                val enabled = newValue as Boolean
                UserPreferences.enableTmdb = enabled

                val message = if (enabled) {
                    getString(R.string.settings_enable_tmdb_enabled)
                } else {
                    getString(R.string.settings_enable_tmdb_disabled)
                }

                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                true
            }
        }

        findPreference<EditTextPreference>("SUBDL_API_KEY")?.apply {
            summary = if (UserPreferences.subdlApiKey.isEmpty()) getString(R.string.settings_subdl_api_key_summary) else UserPreferences.subdlApiKey
            text = UserPreferences.subdlApiKey
            setOnPreferenceChangeListener { _, newValue ->
                val newKey = (newValue as String).trim()
                UserPreferences.subdlApiKey = newKey
                summary = if (newKey.isEmpty()) getString(R.string.settings_subdl_api_key_summary) else newKey
                val message = if (newKey.isEmpty()) {
                    getString(R.string.settings_subdl_api_key_reset)
                } else {
                    getString(R.string.settings_subdl_api_key_success)
                }
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                true
            }
        }

        findPreference<Preference>("p_settings_about")?.apply {
            val titleStr = getString(R.string.settings_version_tv)
            val spannableTitle = SpannableString(titleStr)
            spannableTitle.setSpan(ForegroundColorSpan(Color.WHITE), 0, titleStr.length, 0)
            title = spannableTitle
            
            val summaryStr = BuildConfig.VERSION_NAME
            val spannableSummary = SpannableString(summaryStr)
            spannableSummary.setSpan(ForegroundColorSpan(Color.LTGRAY), 0, summaryStr.length, 0)
            summary = spannableSummary

            isSelectable = false
            setOnPreferenceClickListener(null)
        }

        findPreference<SwitchPreference>("AUTOPLAY")?.isChecked = UserPreferences.autoplay
        findPreference<SwitchPreference>("AUTOPLAY")?.setOnPreferenceChangeListener { _, newValue ->
            UserPreferences.autoplay = newValue as Boolean
            true
        }

        findPreference<SwitchPreference>("FORCE_EXTRA_BUFFERING")?.apply {
            isChecked = UserPreferences.forceExtraBuffering
            setOnPreferenceChangeListener { _, newValue ->
                UserPreferences.forceExtraBuffering = newValue as Boolean
                true
            }
        }

        findPreference<EditTextPreference>("p_settings_autoplay_buffer")?.apply {
            summaryProvider = Preference.SummaryProvider<EditTextPreference> { pref ->
                val value = pref.text?.toLongOrNull() ?: 3L
                "$value s"
            }
            setOnPreferenceChangeListener { _, newValue ->
                UserPreferences.autoplayBuffer = (newValue as String).toLongOrNull() ?: 3L
                true
            }
        }

        findPreference<SwitchPreference>("SERVER_AUTO_SUBTITLES_DISABLED")?.apply {
            isChecked = UserPreferences.serverAutoSubtitlesDisabled
            setOnPreferenceChangeListener { _, newValue ->
                UserPreferences.serverAutoSubtitlesDisabled = newValue as Boolean
                true
            }
        }

        val HasConfigProvider = UserPreferences.currentProvider is ProviderConfigUrl
        findPreference<PreferenceCategory>("pc_provider_settings")?.apply {
            isVisible = HasConfigProvider
        }

        if (HasConfigProvider) {
            val provider = UserPreferences.currentProvider
            val configProvider = provider as? ProviderConfigUrl
            val portalProvider = provider as? ProviderPortalUrl
            var autoUpdateVal = false

            findPreference<SwitchPreference>("provider_autoupdate")?.apply {
                isVisible = portalProvider != null
                if (isVisible) {
                    autoUpdateVal = UserPreferences
                        .getProviderCache(
                            provider!!, UserPreferences
                                .PROVIDER_AUTOUPDATE
                        ) != "false"
                    isChecked = autoUpdateVal
                    setOnPreferenceChangeListener { _, newValue ->
                        val newState = newValue as Boolean
                        UserPreferences.setProviderCache(
                            null,
                            UserPreferences.PROVIDER_AUTOUPDATE,
                            newState.toString()
                        )
                        findPreference<EditTextPreference>("provider_url")?.isEnabled = newState == false
                        true
                    }
                }
            }

            findPreference<EditTextPreference>("provider_url")?.apply {
                isVisible = configProvider != null
                isEnabled = autoUpdateVal == false
                if (isVisible && provider != null && configProvider != null) {
                    summary = UserPreferences
                        .getProviderCache(
                            provider, UserPreferences
                                .PROVIDER_URL
                        )
                        .ifBlank { provider.defaultBaseUrl }
                    setOnBindEditTextListener { editText ->
                        editText.inputType = InputType.TYPE_CLASS_TEXT
                        editText.imeOptions = EditorInfo.IME_ACTION_DONE
                        editText.hint = configProvider.defaultBaseUrl

                        editText.setText(summary)
                    }
                    setOnPreferenceChangeListener { _, newValue ->
                        val toSave = (newValue as String)
                            .ifBlank { configProvider.defaultBaseUrl }
                            .trim()
                            .removeSuffix("/") + "/"
                        UserPreferences.setProviderCache(
                            null,
                            UserPreferences.PROVIDER_URL,
                            toSave
                        )
                        summary = toSave
                        viewLifecycleOwner.lifecycleScope.launch {
                            configProvider.onChangeUrl()
                            ProviderChangeNotifier.notifyProviderChanged()
                        }
                        true
                    }
                }
            }

            findPreference<EditTextPreference>("provider_portal_url")?.apply {
                isVisible = portalProvider != null
                if (isVisible && provider != null && portalProvider != null) {
                    summary = UserPreferences
                        .getProviderCache(
                            provider, UserPreferences
                                .PROVIDER_PORTAL_URL
                        )
                        .ifBlank { portalProvider.defaultPortalUrl }
                    setOnBindEditTextListener { editText ->
                        editText.inputType = InputType.TYPE_CLASS_TEXT
                        editText.imeOptions = EditorInfo.IME_ACTION_DONE
                        editText.hint = portalProvider.defaultPortalUrl
                        editText.setText(summary)
                    }
                    setOnPreferenceChangeListener { _, newValue ->
                        val toSave = (newValue as String)
                            .ifBlank { portalProvider.defaultPortalUrl }
                            .trim()
                            .removeSuffix("/") + "/"
                        summary = toSave
                        UserPreferences.setProviderCache(
                            null,
                            UserPreferences.PROVIDER_PORTAL_URL,
                            toSave
                        )
                        true
                    }
                }
            }

            findPreference<Preference>("provider_autoupdate_now")?.apply {
                isVisible = portalProvider != null
                setOnPreferenceClickListener {
                    viewLifecycleOwner.lifecycleScope.launch {
                        findPreference<EditTextPreference>("provider_url")?.summary =
                            configProvider!!.onChangeUrl(true)
                    }
                    true
                }
            }
        }

        findPreference<ListPreference>("p_doh_provider_url")?.apply {
            value = UserPreferences.dohProviderUrl
            summary = entry
            setOnPreferenceChangeListener { preference, newValue ->
                val newUrl = newValue as String
                UserPreferences.dohProviderUrl = newUrl
                DnsResolver.setDnsUrl(newUrl)
                if (preference is ListPreference) {
                    val index = preference.findIndexOfValue(newUrl)
                    if (index >= 0 && preference.entries != null && index < preference.entries.size) {
                        preference.summary = preference.entries[index]
                    } else {
                        preference.summary = null
                    }
                }
                if (UserPreferences.currentProvider is StreamingCommunityProvider) {
                    viewLifecycleOwner.lifecycleScope.launch {
                        (UserPreferences.currentProvider as StreamingCommunityProvider).rebuildService()
                        requireActivity().apply {
                            finish()
                            startActivity(Intent(this, this::class.java))
                        }
                    }
                } else {
                    Toast.makeText(requireContext(), getString(R.string.doh_provider_updated), Toast.LENGTH_LONG).show()
                }
                true
            }
        }

        findPreference<SwitchPreference>("pc_frenchstream_new_interface")?.apply {
            isVisible = UserPreferences.currentProvider is FrenchStreamProvider
            if (isVisible) {
                val useNewInterface = UserPreferences
                    .getProviderCache(
                        UserPreferences.currentProvider!!, UserPreferences
                            .PROVIDER_NEW_INTERFACE
                    ) != "false"
                isChecked = useNewInterface
                setOnPreferenceChangeListener { _, newValue ->
                    val newState = newValue as Boolean
                    UserPreferences.setProviderCache(
                        null,
                        UserPreferences.PROVIDER_NEW_INTERFACE,
                        newState.toString()
                    )
                    true
                }
            }
        }

        val networkSettingsCategory = findPreference<PreferenceCategory>("pc_network_settings")
        if (networkSettingsCategory != null) {
            val originalTitle = getString(R.string.settings_category_network_title)
            val currentProviderName = UserPreferences.currentProvider?.name
            if (currentProviderName != null && currentProviderName.isNotEmpty()) {
                networkSettingsCategory.title = "$originalTitle $currentProviderName"
            } else {
                networkSettingsCategory.title = originalTitle
            }
        }

        findPreference<Preference>("key_backup_export_tv")?.setOnPreferenceClickListener {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "streamflix_tv_backup_$timestamp.json"
            showBackupExportOptions(fileName)
            true
        }

        findPreference<Preference>("key_backup_import_tv")?.setOnPreferenceClickListener {
            showBackupImportOptions()
            true
        }

        findPreference<SeekBarPreference>("scale_x")?.apply {
            seekBarIncrement = 1
            value = UserPreferences.paddingX
            setOnPreferenceChangeListener { preference, newValue ->
                UserPreferences.paddingX = newValue as Int
                (activity as? MainTvActivity)?.adjustLayoutDelta(UserPreferences.paddingX, null)
                true
            }
        }

        findPreference<SeekBarPreference>("scale_y")?.apply {
            seekBarIncrement = 1
            value = UserPreferences.paddingY
            setOnPreferenceChangeListener { preference, newValue ->
                UserPreferences.paddingY = newValue as Int
                (activity as? MainTvActivity)?.adjustLayoutDelta(null, UserPreferences.paddingY)
                true
            }
        }

        findPreference<Preference>("preferred_player_reset")?.setOnPreferenceClickListener {
            PreferenceManager.getDefaultSharedPreferences(requireContext())
                .edit()
                .remove("preferred_smarttube_package")
                .apply()
            Toast.makeText(requireContext(), R.string.settings_trailer_player_reset, Toast.LENGTH_SHORT).show()
            true
        }

        findPreference<ListPreference>("theme_preference")?.apply {
            summaryProvider = Preference.SummaryProvider<ListPreference> { pref ->
                val selectedTheme = pref.value ?: "default"
                when (selectedTheme) {
                    "default" -> getString(R.string.theme_default)
                    "nero_amoled_oled" -> getString(R.string.theme_nero_amoled_oled)
                    else -> getString(R.string.theme_default)
                }
            }

            setOnPreferenceChangeListener { _, newValue ->
                val newTheme = newValue as String
                UserPreferences.selectedTheme = newTheme

                // Apply the theme and restart the activity
                requireActivity().apply {
                    finish()
                    startActivity(Intent(this, MainTvActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                    overridePendingTransition(0, 0) // Disable transition animation
                }
                true
            }
        }
    }

    private suspend fun performBackupExport(uri: Uri) {
        val jsonData = backupRestoreManager.exportUserData()
        if (jsonData != null) {
            try {
                requireContext().contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.writer().use { it.write(jsonData) }
                    Toast.makeText(requireContext(), getString(R.string.backup_export_success), Toast.LENGTH_LONG).show()
                } ?: run {
                    Toast.makeText(requireContext(), getString(R.string.backup_export_error_write), Toast.LENGTH_LONG).show()
                }
            } catch (e: IOException) {
                Toast.makeText(requireContext(), getString(R.string.backup_export_error_write), Toast.LENGTH_LONG).show()
                Log.e("BackupExportTV", "Error writing backup file", e)
            }
        } else {
            Toast.makeText(requireContext(), getString(R.string.backup_data_not_generated), Toast.LENGTH_LONG).show()
        }
    }

    private suspend fun performBackupImport(uri: Uri) {
        try {
            val stringBuilder = StringBuilder()
            requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { stringBuilder.append(it) }
                }
            }
            val jsonData = stringBuilder.toString()
            if (jsonData.isNotBlank()) {
                val success = backupRestoreManager.importUserData(jsonData)
                if (success) {
                    Toast.makeText(requireContext(), getString(R.string.backup_import_success), Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(requireContext(), getString(R.string.backup_import_error), Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(requireContext(), getString(R.string.backup_import_empty_file), Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), getString(R.string.backup_import_read_error), Toast.LENGTH_LONG).show()
            Log.e("BackupImportTV", "Error reading/processing backup file", e)
        }
    }

    private fun showBackupExportOptions(fileName: String) {
        val options = mutableListOf<Pair<String, () -> Unit>>()
        if (hasCreateDocumentHandler()) {
            options += getString(R.string.backup_export_picker_option) to {
                try {
                    exportBackupLauncher.launch(fileName)
                } catch (error: ActivityNotFoundException) {
                    Log.w("BackupExportTV", "No document picker available, using local fallback", error)
                    Toast.makeText(requireContext(), getString(R.string.backup_picker_unavailable), Toast.LENGTH_LONG).show()
                    exportBackupToLocalFile(fileName)
                }
            }
        }
        options += getString(R.string.backup_export_local_option) to {
            exportBackupToLocalFile(fileName)
        }
        if (canWriteToDownloads()) {
            options += getString(R.string.backup_export_downloads_option) to {
                exportBackupToDownloads(fileName)
            }
        }

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.backup_export_title)
            .setItems(options.map { it.first }.toTypedArray()) { _, which ->
                options[which].second.invoke()
            }
            .show()
    }

    private fun showBackupImportOptions() {
        val options = mutableListOf<Pair<String, () -> Unit>>()
        if (hasOpenDocumentHandler()) {
            options += getString(R.string.backup_import_picker_option) to {
                try {
                    importBackupLauncher.launch(arrayOf("application/json"))
                } catch (error: ActivityNotFoundException) {
                    Log.w("BackupImportTV", "No document picker available, using local fallback", error)
                    Toast.makeText(requireContext(), getString(R.string.backup_picker_unavailable), Toast.LENGTH_LONG).show()
                    showLocalBackupPicker()
                }
            }
        }
        options += getString(R.string.backup_import_local_option) to {
            showLocalBackupPicker()
        }

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.backup_import_title)
            .setItems(options.map { it.first }.toTypedArray()) { _, which ->
                options[which].second.invoke()
            }
            .show()
    }

    private fun exportBackupToLocalFile(fileName: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val jsonData = backupRestoreManager.exportUserData()
            if (jsonData.isNullOrBlank()) {
                Toast.makeText(requireContext(), getString(R.string.backup_data_not_generated), Toast.LENGTH_LONG).show()
                return@launch
            }

            runCatching {
                val file = File(getBackupDirectory(), fileName).apply {
                    parentFile?.mkdirs()
                    writeText(jsonData)
                }
                Toast.makeText(
                    requireContext(),
                    getString(R.string.backup_export_saved_to, file.absolutePath),
                    Toast.LENGTH_LONG
                ).show()
            }.onFailure { error ->
                Toast.makeText(requireContext(), getString(R.string.backup_export_error_write), Toast.LENGTH_LONG).show()
                Log.e("BackupExportTV", "Error writing local backup file", error)
            }
        }
    }

    private fun exportBackupToDownloads(fileName: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val jsonData = backupRestoreManager.exportUserData()
            if (jsonData.isNullOrBlank()) {
                Toast.makeText(requireContext(), getString(R.string.backup_data_not_generated), Toast.LENGTH_LONG).show()
                return@launch
            }

            runCatching {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "application/json")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/StreamFlix")
                }
                val uri = requireContext().contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: error("Unable to create download entry")
                requireContext().contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.writer().use { it.write(jsonData) }
                } ?: error("Unable to open output stream")

                Toast.makeText(
                    requireContext(),
                    getString(R.string.backup_export_saved_to, "Downloads/StreamFlix/$fileName"),
                    Toast.LENGTH_LONG
                ).show()
            }.onFailure { error ->
                Toast.makeText(requireContext(), getString(R.string.backup_export_error_write), Toast.LENGTH_LONG).show()
                Log.e("BackupExportTV", "Error writing backup to downloads", error)
            }
        }
    }

    private fun showLocalBackupPicker() {
        val backups = getBackupDirectory()
            .listFiles { file -> file.isFile && file.extension.equals("json", ignoreCase = true) }
            ?.sortedByDescending { it.lastModified() }
            .orEmpty()

        if (backups.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.backup_import_no_local_files), Toast.LENGTH_LONG).show()
            return
        }

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.backup_import_local_option)
            .setItems(backups.map { it.name }.toTypedArray()) { _, which ->
                importBackupFromFile(backups[which])
            }
            .show()
    }

    private fun importBackupFromFile(file: File) {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                val jsonData = file.readText()
                if (jsonData.isBlank()) {
                    Toast.makeText(requireContext(), getString(R.string.backup_import_empty_file), Toast.LENGTH_LONG).show()
                    return@launch
                }
                val success = backupRestoreManager.importUserData(jsonData)
                if (success) {
                    Toast.makeText(requireContext(), getString(R.string.backup_import_success), Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(requireContext(), getString(R.string.backup_import_error), Toast.LENGTH_LONG).show()
                }
            }.onFailure { error ->
                Toast.makeText(requireContext(), getString(R.string.backup_import_read_error), Toast.LENGTH_LONG).show()
                Log.e("BackupImportTV", "Error importing local backup file", error)
            }
        }
    }

    private fun getBackupDirectory(): File {
        return File(requireContext().getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: requireContext().filesDir, "backups")
    }

    private fun hasCreateDocumentHandler(): Boolean {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("application/json")
        return intent.resolveActivity(requireContext().packageManager) != null
    }

    private fun hasOpenDocumentHandler(): Boolean {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("application/json")
        return intent.resolveActivity(requireContext().packageManager) != null
    }

    private fun canWriteToDownloads(): Boolean {
        return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q
    }

    override fun onResume() {
        super.onResume()
        
        findPreference<PreferenceCategory>("pc_streamingcommunity_settings")?.isVisible =
            UserPreferences.currentProvider is StreamingCommunityProvider

        findPreference<PreferenceCategory>("pc_cuevana_settings")?.isVisible =
            UserPreferences.currentProvider?.name == "Cuevana 3"

        findPreference<PreferenceCategory>("pc_poseidon_settings")?.isVisible =
            UserPreferences.currentProvider?.name == "Poseidonhd2"

        findPreference<EditTextPreference>("provider_streamingcommunity_domain")?.apply {
            val currentValue = UserPreferences.streamingcommunityDomain
            summary = currentValue
            if (currentValue == DEFAULT_DOMAIN_VALUE || currentValue == PREFS_ERROR_VALUE) {
                text = null
            } else {
                text = currentValue
            }
        }

        findPreference<EditTextPreference>("TMDB_API_KEY")?.apply {
            summary = if (UserPreferences.tmdbApiKey.isEmpty()) getString(R.string.settings_tmdb_api_key_summary) else UserPreferences.tmdbApiKey
            text = UserPreferences.tmdbApiKey
        }

        findPreference<EditTextPreference>("SUBDL_API_KEY")?.apply {
            summary = if (UserPreferences.subdlApiKey.isEmpty()) getString(R.string.settings_subdl_api_key_summary) else UserPreferences.subdlApiKey
            text = UserPreferences.subdlApiKey
        }

        findPreference<ListPreference>("p_doh_provider_url")?.apply {
            summary = entry
        }

        val networkSettingsCategory = findPreference<PreferenceCategory>("pc_network_settings")
        if (networkSettingsCategory != null) {
            val originalTitle = getString(R.string.settings_category_network_title)
            val currentProviderName = UserPreferences.currentProvider?.name
            if (currentProviderName != null && currentProviderName.isNotEmpty()) {
                networkSettingsCategory.title = "$originalTitle $currentProviderName"
            } else {
                networkSettingsCategory.title = originalTitle
            }
        }
        findPreference<SwitchPreference>("AUTOPLAY")?.isChecked = UserPreferences.autoplay
        findPreference<SwitchPreference>("FORCE_EXTRA_BUFFERING")?.isChecked = UserPreferences.forceExtraBuffering
        findPreference<SwitchPreference>("SERVER_AUTO_SUBTITLES_DISABLED")?.isChecked = UserPreferences.serverAutoSubtitlesDisabled
        
        val bufferPref: EditTextPreference? = findPreference("p_settings_autoplay_buffer") 
        bufferPref?.summaryProvider = Preference.SummaryProvider<EditTextPreference> { pref ->
            val value = pref.text?.toLongOrNull() ?: 3L
            "$value s"
        }
    }
}
