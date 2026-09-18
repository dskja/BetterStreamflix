package com.dskja.betterstreamflix.fragments.settings.about

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.dskja.betterstreamflix.BuildConfig
import com.dskja.betterstreamflix.R
import com.dskja.betterstreamflix.support.SupportLinkOpener
import com.dskja.betterstreamflix.support.SupportUrls

class SettingsAboutMobileFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.settings_about_mobile, rootKey)
        displaySettingsAbout()
    }

    private fun displaySettingsAbout() {
        findPreference<Preference>("p_settings_about_version")?.apply {
            summary = getString(R.string.settings_about_version_name, BuildConfig.VERSION_NAME)
        }

        findPreference<Preference>("p_settings_github")?.setOnPreferenceClickListener {
            SupportLinkOpener.open(requireContext(), SupportUrls.GITHUB_REPOSITORY_URL)
            true
        }

        findPreference<Preference>("p_settings_buy_me_a_coffee")?.setOnPreferenceClickListener {
            SupportLinkOpener.open(requireContext(), SupportUrls.BUY_ME_A_COFFEE_URL, markAppreciation = true)
            true
        }

        findPreference<Preference>("p_settings_github_sponsors")?.setOnPreferenceClickListener {
            SupportLinkOpener.open(requireContext(), SupportUrls.GITHUB_SPONSORS_URL, markAppreciation = true)
            true
        }

        findPreference<Preference>("p_settings_upstream")?.setOnPreferenceClickListener {
            SupportLinkOpener.open(requireContext(), SupportUrls.UPSTREAM_REPOSITORY_URL)
            true
        }
    }
}
