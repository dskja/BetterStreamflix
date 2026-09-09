package com.dskja.betterstreamflix.fragments.settings.about

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.leanback.preference.LeanbackPreferenceFragmentCompat
import androidx.preference.Preference
import com.dskja.betterstreamflix.BuildConfig
import com.dskja.betterstreamflix.R

class SettingsAboutTvFragment : LeanbackPreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_about_tv, rootKey)

        displaySettingsAbout()
    }

    private fun displaySettingsAbout() {
        findPreference<Preference>("p_settings_about_version")?.apply {
            summary = getString(R.string.settings_about_version_name, BuildConfig.VERSION_NAME)
        }

        findPreference<Preference>("p_settings_github")?.setOnPreferenceClickListener {
            openUrl("https://github.com/dskja/BetterStreamflix")
            true
        }

        findPreference<Preference>("p_settings_buy_me_a_coffee")?.setOnPreferenceClickListener {
            openUrl("https://buymeacoffee.com/betterstreamflix")
            true
        }

        findPreference<Preference>("p_settings_upstream")?.setOnPreferenceClickListener {
            openUrl("https://github.com/streamflix-reborn2/streamflix")
            true
        }
    }

    private fun openUrl(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
