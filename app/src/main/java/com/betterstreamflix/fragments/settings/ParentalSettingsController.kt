package com.betterstreamflix.fragments.settings

import androidx.preference.Preference
import com.betterstreamflix.R
import com.betterstreamflix.utils.UserPreferences

/**
 * Shared parental-control preference state helpers (Settings split — Megaplan P4).
 * Settings fragments still own dialogs; this consolidates enablement/summary logic.
 */
object ParentalSettingsController {

    fun refreshPreferenceState(
        findPreference: (String) -> Preference?,
        getString: (Int) -> String,
    ) {
        val hasPin = UserPreferences.parentalControlPin.isNotBlank()
        val hasAdmin = UserPreferences.parentalControlAdminPin.isNotBlank()
        findPreference("PARENTAL_CONTROL_PIN")?.summary =
            if (hasPin) getString(R.string.settings_parental_pin_set) else getString(R.string.settings_parental_pin_hint)
        findPreference("PARENTAL_CONTROL_ADMIN_PIN")?.isEnabled = hasPin
        findPreference("PARENTAL_CONTROL_REMOVE_PIN")?.isEnabled = hasPin
        findPreference("PARENTAL_CONTROL_REMOVE_ADMIN_PIN")?.isEnabled = hasAdmin
        findPreference("PARENTAL_CONTROL_MAX_AGE")?.isEnabled = hasPin
        findPreference("PARENTAL_CONTROL_UNLOCK")?.isEnabled =
            hasPin && (UserPreferences.isParentalControlTemporarilyLocked || UserPreferences.parentalControlHardLocked)
    }
}
