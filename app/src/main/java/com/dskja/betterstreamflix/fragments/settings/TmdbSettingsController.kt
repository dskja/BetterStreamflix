package com.dskja.betterstreamflix.fragments.settings

import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.preference.Preference
import com.dskja.betterstreamflix.R
import com.dskja.betterstreamflix.utils.TMDb3
import com.dskja.betterstreamflix.utils.TmdbCache
import com.dskja.betterstreamflix.utils.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object TmdbSettingsController {

    fun bind(
        fragment: Fragment,
        scope: LifecycleCoroutineScope,
        findPreference: (String) -> Preference?,
    ) {
        val context = fragment.requireContext()
        val testPref = findPreference("TMDB_TEST_CONNECTION")
        val clearPref = findPreference("TMDB_CLEAR_CACHE")
        val statusPref = findPreference("TMDB_STATUS")

        fun refreshStatus() {
            statusPref?.summary = when {
                !UserPreferences.enableTmdb ->
                    context.getString(R.string.settings_tmdb_status_disabled)
                !TMDb3.hasApiKey() ->
                    context.getString(R.string.settings_tmdb_status_no_key)
                else ->
                    context.getString(R.string.settings_tmdb_status_ready)
            }
        }

        refreshStatus()

        testPref?.setOnPreferenceClickListener {
            if (!UserPreferences.enableTmdb) {
                Toast.makeText(
                    context,
                    R.string.settings_tmdb_test_disabled,
                    Toast.LENGTH_SHORT,
                ).show()
                return@setOnPreferenceClickListener true
            }
            if (!TMDb3.hasApiKey()) {
                Toast.makeText(
                    context,
                    R.string.settings_tmdb_api_key_missing,
                    Toast.LENGTH_LONG,
                ).show()
                return@setOnPreferenceClickListener true
            }
            testPref.isEnabled = false
            testPref.summary = context.getString(R.string.settings_tmdb_test_running)
            scope.launch {
                val ok = withContext(Dispatchers.IO) { TMDb3.ping() }
                testPref.isEnabled = true
                testPref.summary = context.getString(R.string.settings_tmdb_test_summary)
                refreshStatus()
                Toast.makeText(
                    context,
                    if (ok) R.string.settings_tmdb_test_success else R.string.settings_tmdb_test_failed,
                    Toast.LENGTH_SHORT,
                ).show()
            }
            true
        }

        clearPref?.setOnPreferenceClickListener {
            TmdbCache.clear()
            TMDb3.rebuildService()
            refreshStatus()
            Toast.makeText(context, R.string.settings_tmdb_cache_cleared, Toast.LENGTH_SHORT).show()
            true
        }
    }
}
