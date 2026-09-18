package com.dskja.betterstreamflix.fragments.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreference
import androidx.preference.SwitchPreferenceCompat
import com.dskja.betterstreamflix.R
import com.dskja.betterstreamflix.platform.trakt.TraktConfig
import com.dskja.betterstreamflix.utils.UserPreferences

object PlatformSettingsController {
    fun bind(
        fragment: Fragment,
        @Suppress("UNUSED_PARAMETER") scope: LifecycleCoroutineScope,
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
                        true
                    }
                }
                is SwitchPreference -> {
                    pref.isChecked = get()
                    pref.setOnPreferenceChangeListener { _, v ->
                        set(v as Boolean)
                        true
                    }
                }
            }
        }

        fun bindText(
            key: String,
            get: () -> String,
            set: (String) -> Unit,
            mask: Boolean = false,
        ) {
            val pref = findPreference(key) as? EditTextPreference ?: return
            val value = get()
            pref.text = value
            pref.summary = when {
                value.isBlank() -> pref.context.getString(
                    when (key) {
                        "TRAKT_CLIENT_ID" -> R.string.platform_trakt_client_id_summary
                        "TRAKT_ACCESS_TOKEN" -> R.string.platform_trakt_token_summary
                        "JELLYFIN_BASE_URL" -> R.string.platform_jellyfin_url_summary
                        "JELLYFIN_USER_ID" -> R.string.platform_jellyfin_user_id_summary
                        "JELLYFIN_ACCESS_TOKEN" -> R.string.platform_jellyfin_token_summary
                        "PLEX_BASE_URL" -> R.string.platform_plex_url_summary
                        "PLEX_TOKEN" -> R.string.platform_plex_token_summary
                        "REAL_DEBRID_TOKEN" -> R.string.platform_rd_token_summary
                        else -> R.string.platform_settings_summary
                    },
                )
                mask -> "••••••••"
                else -> value
            }
            pref.setOnPreferenceChangeListener { preference, newValue ->
                val typed = newValue.toString().trim()
                set(typed)
                preference.summary = when {
                    typed.isBlank() -> preference.summary
                    mask -> "••••••••"
                    else -> typed
                }
                Toast.makeText(context, R.string.platform_settings_saved, Toast.LENGTH_SHORT).show()
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
            key = "TRAKT_ACCESS_TOKEN",
            get = { UserPreferences.traktAccessToken },
            set = { UserPreferences.traktAccessToken = it },
            mask = true,
        )
        bindText(
            key = "JELLYFIN_BASE_URL",
            get = { UserPreferences.jellyfinBaseUrl },
            set = { UserPreferences.jellyfinBaseUrl = it },
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

        findPreference("trakt_device_auth_help")?.setOnPreferenceClickListener {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(TraktConfig.authorizeHelpUrl(context))),
            )
            true
        }
    }
}
