package com.betterstreamflix.fragments.settings

import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import com.betterstreamflix.R
import com.betterstreamflix.utils.ProviderChangeNotifier
import com.betterstreamflix.utils.UserPreferences

/**
 * Shared parental-control PIN preference wiring for [SettingsMobileFragment] and
 * [SettingsTvFragment]. Handles the PIN/admin-PIN editors, the max-age gate, the
 * temporary/hard lockout messaging, and the unlock flow so both fragments no longer
 * need to duplicate this logic.
 */
object ParentalSettingsController {
    fun bind(
        fragment: Fragment,
        findPreference: (String) -> Preference?,
    ) {
        val pinPreference = findPreference("PARENTAL_CONTROL_PIN") as? EditTextPreference
        val adminPinPreference = findPreference("PARENTAL_CONTROL_ADMIN_PIN") as? EditTextPreference
        val removePinPreference = findPreference("PARENTAL_CONTROL_REMOVE_PIN")
        val removeAdminPinPreference = findPreference("PARENTAL_CONTROL_REMOVE_ADMIN_PIN")
        val maxAgePreference = findPreference("PARENTAL_CONTROL_MAX_AGE") as? ListPreference
        val unlockPreference = findPreference("PARENTAL_CONTROL_UNLOCK")

        fun bindPinEditText(editText: EditText) {
            editText.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            editText.imeOptions = EditorInfo.IME_ACTION_DONE
            editText.hint = fragment.getString(R.string.settings_parental_pin_hint)
            editText.setText("")
        }

        pinPreference?.setOnBindEditTextListener(::bindPinEditText)
        adminPinPreference?.setOnBindEditTextListener(::bindPinEditText)

        pinPreference?.setOnPreferenceClickListener {
            showParentalPinEditor(fragment, findPreference, maxAgePreference)
            true
        }

        adminPinPreference?.setOnPreferenceClickListener {
            showAdminPinEditor(fragment, findPreference)
            true
        }

        removePinPreference?.setOnPreferenceClickListener {
            changeParentalSettingWithPinCheck(fragment, findPreference) {
                UserPreferences.parentalControlPin = ""
                UserPreferences.parentalControlMaxAge = null
                maxAgePreference?.value = ""
                UserPreferences.unlockParentalControls()
                Toast.makeText(fragment.requireContext(), fragment.getString(R.string.settings_parental_pin_removed), Toast.LENGTH_SHORT).show()
                ProviderChangeNotifier.notifyProviderChanged()
                updateParentalControlPreferenceState(fragment, findPreference)
            }
            true
        }

        removeAdminPinPreference?.setOnPreferenceClickListener {
            changeAdminSettingWithPinCheck(fragment, findPreference) {
                UserPreferences.parentalControlAdminPin = ""
                Toast.makeText(fragment.requireContext(), fragment.getString(R.string.settings_parental_admin_pin_removed), Toast.LENGTH_SHORT).show()
                updateParentalControlPreferenceState(fragment, findPreference)
            }
            true
        }

        maxAgePreference?.setOnPreferenceChangeListener { _, newValue ->
            if (!UserPreferences.enableTmdb) return@setOnPreferenceChangeListener false
            if (UserPreferences.parentalControlPin.isBlank()) {
                Toast.makeText(fragment.requireContext(), fragment.getString(R.string.settings_parental_set_pin_first), Toast.LENGTH_SHORT).show()
                return@setOnPreferenceChangeListener false
            }

            val newMaxAgeValue = newValue as String
            val newMaxAge = newMaxAgeValue.toIntOrNull()

            changeParentalSettingWithPinCheck(fragment, findPreference) {
                UserPreferences.parentalControlMaxAge = newMaxAge
                maxAgePreference.value = newMaxAgeValue
                Toast.makeText(fragment.requireContext(), fragment.getString(R.string.settings_parental_max_age_saved), Toast.LENGTH_SHORT).show()
                ProviderChangeNotifier.notifyProviderChanged()
                updateParentalControlPreferenceState(fragment, findPreference)
            }

            false
        }

        unlockPreference?.setOnPreferenceClickListener {
            if (UserPreferences.parentalControlAdminPin.isBlank()) {
                Toast.makeText(fragment.requireContext(), fragment.getString(R.string.settings_parental_set_admin_pin_first), Toast.LENGTH_SHORT).show()
            } else {
                promptForAdminPin(fragment) {
                    UserPreferences.unlockParentalControls()
                    Toast.makeText(fragment.requireContext(), fragment.getString(R.string.settings_parental_unlocked), Toast.LENGTH_SHORT).show()
                    updateParentalControlPreferenceState(fragment, findPreference)
                }
            }
            true
        }

        updateParentalControlPreferenceState(fragment, findPreference)
    }

    /**
     * Refreshes the summaries/enabled-state of the parental preferences. Also called from
     * outside [bind] (e.g. when the TMDB toggle changes) since that affects whether the
     * parental controls section is usable at all.
     */
    fun updateParentalControlPreferenceState(
        fragment: Fragment,
        findPreference: (String) -> Preference?,
    ) {
        val tmdbEnabled = UserPreferences.enableTmdb
        val pinPreference = findPreference("PARENTAL_CONTROL_PIN") as? EditTextPreference
        val adminPinPreference = findPreference("PARENTAL_CONTROL_ADMIN_PIN") as? EditTextPreference
        val removePinPreference = findPreference("PARENTAL_CONTROL_REMOVE_PIN")
        val removeAdminPinPreference = findPreference("PARENTAL_CONTROL_REMOVE_ADMIN_PIN")
        val maxAgePreference = findPreference("PARENTAL_CONTROL_MAX_AGE") as? ListPreference
        val unlockPreference = findPreference("PARENTAL_CONTROL_UNLOCK")
        val isLocked = UserPreferences.isParentalControlTemporarilyLocked || UserPreferences.parentalControlHardLocked

        pinPreference?.apply {
            isEnabled = tmdbEnabled && !isLocked
            text = ""
            summary = when {
                !tmdbEnabled -> fragment.getString(R.string.settings_parental_requires_tmdb)
                UserPreferences.parentalControlHardLocked -> fragment.getString(R.string.settings_parental_locked_hard)
                UserPreferences.isParentalControlTemporarilyLocked -> fragment.getString(
                    R.string.settings_parental_locked_temporary,
                    lockRemainingMinutes(),
                )
                UserPreferences.parentalControlPin.isBlank() -> fragment.getString(R.string.settings_parental_pin_not_set)
                else -> fragment.getString(R.string.settings_parental_pin_set)
            }
        }

        adminPinPreference?.apply {
            isEnabled = tmdbEnabled
            text = ""
            summary = when {
                !tmdbEnabled -> fragment.getString(R.string.settings_parental_requires_tmdb)
                UserPreferences.parentalControlAdminPin.isBlank() -> fragment.getString(R.string.settings_parental_admin_pin_not_set)
                else -> fragment.getString(R.string.settings_parental_admin_pin_set)
            }
        }

        removePinPreference?.apply {
            isVisible = tmdbEnabled && UserPreferences.parentalControlPin.isNotBlank()
            isEnabled = !isLocked
        }

        removeAdminPinPreference?.apply {
            isVisible = tmdbEnabled && UserPreferences.parentalControlAdminPin.isNotBlank()
            isEnabled = true
        }

        maxAgePreference?.apply {
            isEnabled = tmdbEnabled && !isLocked
            value = UserPreferences.parentalControlMaxAge?.toString().orEmpty()
            summary = when {
                !tmdbEnabled -> fragment.getString(R.string.settings_parental_requires_tmdb)
                UserPreferences.parentalControlHardLocked -> fragment.getString(R.string.settings_parental_locked_hard)
                UserPreferences.isParentalControlTemporarilyLocked -> fragment.getString(
                    R.string.settings_parental_locked_temporary,
                    lockRemainingMinutes(),
                )
                UserPreferences.parentalControlPin.isBlank() -> fragment.getString(R.string.settings_parental_set_pin_first)
                UserPreferences.parentalControlMaxAge == null -> fragment.getString(R.string.settings_parental_max_age_disabled)
                else -> "${UserPreferences.parentalControlMaxAge}+"
            }
        }

        unlockPreference?.apply {
            isVisible = isLocked
            isEnabled = tmdbEnabled && UserPreferences.parentalControlAdminPin.isNotBlank()
            summary = when {
                UserPreferences.parentalControlAdminPin.isBlank() -> fragment.getString(R.string.settings_parental_set_admin_pin_first)
                UserPreferences.parentalControlHardLocked -> fragment.getString(R.string.settings_parental_locked_hard)
                UserPreferences.isParentalControlTemporarilyLocked -> fragment.getString(
                    R.string.settings_parental_locked_temporary,
                    lockRemainingMinutes(),
                )
                else -> fragment.getString(R.string.settings_parental_unlock_summary)
            }
        }
    }

    /**
     * Gate for changing a parental setting: honors hard/temporary lockouts, then requires the
     * current PIN (if one is set) before invoking [onVerified]. Public because callers outside
     * the parental section (e.g. disabling TMDB) also need to gate behind the PIN.
     */
    fun changeParentalSettingWithPinCheck(
        fragment: Fragment,
        findPreference: (String) -> Preference?,
        onVerified: () -> Unit,
    ) {
        when {
            UserPreferences.parentalControlHardLocked -> {
                Toast.makeText(fragment.requireContext(), fragment.getString(R.string.settings_parental_locked_hard), Toast.LENGTH_SHORT).show()
                updateParentalControlPreferenceState(fragment, findPreference)
                return
            }
            UserPreferences.isParentalControlTemporarilyLocked -> {
                Toast.makeText(
                    fragment.requireContext(),
                    fragment.getString(R.string.settings_parental_locked_temporary, lockRemainingMinutes()),
                    Toast.LENGTH_SHORT,
                ).show()
                updateParentalControlPreferenceState(fragment, findPreference)
                return
            }
        }

        val currentPin = UserPreferences.parentalControlPin
        if (currentPin.isBlank()) {
            onVerified()
            return
        }

        promptForPin(
            fragment = fragment,
            titleRes = R.string.settings_parental_enter_current_pin_title,
            messageRes = R.string.settings_parental_enter_current_pin_message,
            onSubmit = { enteredPin ->
                if (enteredPin == currentPin) {
                    UserPreferences.registerParentalPinSuccess()
                    onVerified()
                    null
                } else {
                    UserPreferences.registerParentalPinFailure()
                    updateParentalControlPreferenceState(fragment, findPreference)
                    when {
                        UserPreferences.parentalControlHardLocked -> R.string.settings_parental_locked_hard
                        UserPreferences.isParentalControlTemporarilyLocked -> R.string.settings_parental_locked_temporary
                        else -> R.string.settings_parental_invalid_pin
                    }.let { failureMessageRes ->
                        if (failureMessageRes == R.string.settings_parental_locked_temporary) {
                            fragment.getString(failureMessageRes, lockRemainingMinutes())
                        } else {
                            fragment.getString(failureMessageRes)
                        }
                    }
                }
            },
        )
    }

    private fun changeAdminSettingWithPinCheck(
        fragment: Fragment,
        findPreference: (String) -> Preference?,
        onVerified: () -> Unit,
    ) {
        val currentAdminPin = UserPreferences.parentalControlAdminPin
        if (currentAdminPin.isBlank()) {
            onVerified()
            return
        }

        promptForAdminPin(fragment, onVerified)
    }

    private fun promptForAdminPin(fragment: Fragment, onVerified: () -> Unit) {
        val currentAdminPin = UserPreferences.parentalControlAdminPin
        if (currentAdminPin.isBlank()) {
            Toast.makeText(fragment.requireContext(), fragment.getString(R.string.settings_parental_set_admin_pin_first), Toast.LENGTH_SHORT).show()
            return
        }

        promptForPin(
            fragment = fragment,
            titleRes = R.string.settings_parental_enter_admin_pin_title,
            messageRes = R.string.settings_parental_enter_admin_pin_message,
            onSubmit = { enteredPin ->
                if (enteredPin == currentAdminPin) {
                    UserPreferences.unlockParentalControls()
                    onVerified()
                    null
                } else {
                    fragment.getString(R.string.settings_parental_invalid_admin_pin)
                }
            },
        )
    }

    private fun showParentalPinEditor(
        fragment: Fragment,
        findPreference: (String) -> Preference?,
        maxAgePreference: ListPreference?,
    ) {
        if (!UserPreferences.enableTmdb) {
            Toast.makeText(fragment.requireContext(), fragment.getString(R.string.settings_parental_requires_tmdb), Toast.LENGTH_SHORT).show()
            return
        }

        changeParentalSettingWithPinCheck(fragment, findPreference) {
            promptForPinValue(
                fragment = fragment,
                titleRes = R.string.settings_parental_pin_title,
                messageRes = if (UserPreferences.parentalControlPin.isBlank()) {
                    R.string.settings_parental_set_new_pin_message
                } else {
                    R.string.settings_parental_change_pin_message
                },
                allowBlank = UserPreferences.parentalControlPin.isNotBlank(),
                onSubmit = { newPin ->
                    when {
                        newPin.isBlank() -> {
                            UserPreferences.parentalControlPin = ""
                            UserPreferences.parentalControlMaxAge = null
                            maxAgePreference?.value = ""
                            UserPreferences.unlockParentalControls()
                            Toast.makeText(fragment.requireContext(), fragment.getString(R.string.settings_parental_pin_removed), Toast.LENGTH_SHORT).show()
                            ProviderChangeNotifier.notifyProviderChanged()
                            updateParentalControlPreferenceState(fragment, findPreference)
                            null
                        }
                        newPin.length < 4 -> fragment.getString(R.string.settings_parental_pin_too_short)
                        else -> {
                            UserPreferences.parentalControlPin = newPin
                            Toast.makeText(fragment.requireContext(), fragment.getString(R.string.settings_parental_pin_saved), Toast.LENGTH_SHORT).show()
                            ProviderChangeNotifier.notifyProviderChanged()
                            updateParentalControlPreferenceState(fragment, findPreference)
                            null
                        }
                    }
                },
            )
        }
    }

    private fun showAdminPinEditor(
        fragment: Fragment,
        findPreference: (String) -> Preference?,
    ) {
        changeAdminSettingWithPinCheck(fragment, findPreference) {
            promptForPinValue(
                fragment = fragment,
                titleRes = R.string.settings_parental_admin_pin_title,
                messageRes = if (UserPreferences.parentalControlAdminPin.isBlank()) {
                    R.string.settings_parental_set_new_admin_pin_message
                } else {
                    R.string.settings_parental_change_admin_pin_message
                },
                allowBlank = UserPreferences.parentalControlAdminPin.isNotBlank(),
                onSubmit = { newPin ->
                    when {
                        newPin.isBlank() -> {
                            UserPreferences.parentalControlAdminPin = ""
                            Toast.makeText(fragment.requireContext(), fragment.getString(R.string.settings_parental_admin_pin_removed), Toast.LENGTH_SHORT).show()
                            updateParentalControlPreferenceState(fragment, findPreference)
                            null
                        }
                        newPin.length < 4 -> fragment.getString(R.string.settings_parental_pin_too_short)
                        else -> {
                            UserPreferences.parentalControlAdminPin = newPin
                            Toast.makeText(fragment.requireContext(), fragment.getString(R.string.settings_parental_admin_pin_saved), Toast.LENGTH_SHORT).show()
                            updateParentalControlPreferenceState(fragment, findPreference)
                            null
                        }
                    }
                },
            )
        }
    }

    private fun promptForPin(
        fragment: Fragment,
        titleRes: Int,
        messageRes: Int,
        onSubmit: (String) -> String?,
    ) {
        val context = fragment.requireContext()
        val input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            imeOptions = EditorInfo.IME_ACTION_DONE
            hint = fragment.getString(R.string.settings_parental_pin_hint)
        }

        val dialog = AlertDialog.Builder(context)
            .setTitle(titleRes)
            .setMessage(messageRes)
            .setView(input)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                input.error = null
                val errorMessage = onSubmit(input.text?.toString()?.trim().orEmpty())
                if (errorMessage == null) {
                    dialog.dismiss()
                } else {
                    input.setText("")
                    input.error = errorMessage
                    input.requestFocus()
                }
            }
        }

        dialog.show()
    }

    private fun promptForPinValue(
        fragment: Fragment,
        titleRes: Int,
        messageRes: Int,
        allowBlank: Boolean,
        onSubmit: (String) -> String?,
    ) {
        val context = fragment.requireContext()
        val input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            imeOptions = EditorInfo.IME_ACTION_DONE
            hint = fragment.getString(R.string.settings_parental_pin_hint)
        }

        val dialog = AlertDialog.Builder(context)
            .setTitle(titleRes)
            .setMessage(messageRes)
            .setView(input)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                input.error = null
                val newValue = input.text?.toString()?.trim().orEmpty()
                if (newValue.isBlank() && !allowBlank) {
                    input.setText("")
                    input.error = fragment.getString(R.string.settings_parental_pin_too_short)
                    input.requestFocus()
                    return@setOnClickListener
                }

                val errorMessage = onSubmit(newValue)
                if (errorMessage == null) {
                    dialog.dismiss()
                } else {
                    input.setText("")
                    input.error = errorMessage
                    input.requestFocus()
                }
            }
        }

        dialog.show()
    }

    private fun lockRemainingMinutes(): Int {
        val millis = UserPreferences.parentalControlLockRemainingMillis
        return ((millis + 60_000L - 1L) / 60_000L).toInt().coerceAtLeast(1)
    }
}
