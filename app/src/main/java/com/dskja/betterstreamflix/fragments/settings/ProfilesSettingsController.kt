package com.dskja.betterstreamflix.fragments.settings

import android.content.Context
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat
import com.dskja.betterstreamflix.R
import com.dskja.betterstreamflix.profiles.ProfileManager
import com.dskja.betterstreamflix.profiles.UserProfile

/**
 * Shared Profiles (Beta) (screen_profiles) binder for Mobile + TV.
 */
object ProfilesSettingsController {

    private val integrationPrefKeys = mapOf(
        "PROFILE_INT_TRAKT" to UserProfile.Integration.TRAKT,
        "PROFILE_INT_JELLYFIN" to UserProfile.Integration.JELLYFIN,
        "PROFILE_INT_PLEX" to UserProfile.Integration.PLEX,
        "PROFILE_INT_DEBRID" to UserProfile.Integration.DEBRID,
        "PROFILE_INT_SIMKL" to UserProfile.Integration.SIMKL,
        "PROFILE_INT_OPENSUBTITLES" to UserProfile.Integration.OPENSUBTITLES,
        "PROFILE_INT_TMDB" to UserProfile.Integration.TMDB,
    )

    fun bind(
        fragment: Fragment,
        scope: LifecycleCoroutineScope,
        findPreference: (String) -> Preference?,
        onProfileSwitched: (() -> Unit)? = null,
    ) {
        val context = fragment.requireContext()

        findPreference("PROFILE_SWITCH")?.setOnPreferenceClickListener {
            showSwitchDialog(fragment, findPreference, onProfileSwitched)
            true
        }

        findPreference("PROFILE_CREATE")?.setOnPreferenceClickListener {
            showCreateDialog(fragment, findPreference, onProfileSwitched)
            true
        }

        findPreference("PROFILE_RENAME")?.setOnPreferenceClickListener {
            showRenameDialog(fragment, findPreference)
            true
        }

        findPreference("PROFILE_DELETE")?.setOnPreferenceClickListener {
            showDeleteDialog(fragment, findPreference, onProfileSwitched)
            true
        }

        findPreference("PROFILE_AVATAR")?.let { pref ->
            val list = pref as? ListPreference ?: return@let
            list.setOnPreferenceChangeListener { preference, newValue ->
                val key = newValue as String
                ProfileManager.updateAvatar(ProfileManager.activeProfileId, key)
                if (preference is ListPreference) {
                    preference.value = key
                    preference.summary = preference.entry
                }
                refresh(findPreference, context)
                true
            }
        }

        findPreference("PROFILE_KIDS")?.let { pref ->
            val switch = pref as? SwitchPreferenceCompat ?: return@let
            switch.setOnPreferenceChangeListener { _, newValue ->
                ProfileManager.updateKids(ProfileManager.activeProfileId, newValue as Boolean)
                refresh(findPreference, context)
                true
            }
        }

        findPreference("PROFILE_PIN")?.setOnPreferenceClickListener {
            showPinEditor(fragment, findPreference)
            true
        }

        findPreference("PROFILE_PIN_CLEAR")?.setOnPreferenceClickListener {
            ProfileManager.clearPin(ProfileManager.activeProfileId)
            Toast.makeText(context, R.string.profile_pin_cleared, Toast.LENGTH_SHORT).show()
            refresh(findPreference, context)
            true
        }

        integrationPrefKeys.forEach { (prefKey, integration) ->
            findPreference(prefKey)?.let { pref ->
                val switch = pref as? SwitchPreferenceCompat ?: return@let
                switch.setOnPreferenceChangeListener { _, newValue ->
                    ProfileManager.setIntegrationEnabled(
                        ProfileManager.activeProfileId,
                        integration,
                        newValue as Boolean,
                    )
                    refresh(findPreference, context)
                    true
                }
            }
        }

        refresh(findPreference, context)
    }

    fun refresh(findPreference: (String) -> Preference?, context: Context) {
        val profile = ProfileManager.activeProfile()
        val profileCount = ProfileManager.profiles().size

        findPreference("PROFILE_ACTIVE")?.summary = profile?.let { activeSummary(context, it) }
            ?: context.getString(R.string.profile_default)

        findPreference("PROFILE_DELETE")?.isEnabled = profileCount > 1

        (findPreference("PROFILE_AVATAR") as? ListPreference)?.apply {
            val key = profile?.avatarKey ?: ProfileManager.avatarKeys.first()
            value = key
            summary = entry
        }

        (findPreference("PROFILE_KIDS") as? SwitchPreferenceCompat)?.isChecked = profile?.isKids == true

        findPreference("PROFILE_PIN")?.summary = if (profile?.pinHash != null) {
            context.getString(R.string.profile_pin_set)
        } else {
            context.getString(R.string.profile_pin_not_set)
        }

        findPreference("PROFILE_PIN_CLEAR")?.isVisible = profile?.pinHash != null

        val active = profile
        integrationPrefKeys.forEach { (prefKey, integration) ->
            (findPreference(prefKey) as? SwitchPreferenceCompat)?.isChecked =
                active?.let { ProfileManager.isIntegrationEnabled(it, integration) } ?: true
        }
    }

    private fun activeSummary(context: Context, profile: UserProfile): String {
        val kids = if (profile.isKids) " · ${context.getString(R.string.profile_kids_title)}" else ""
        return "${profile.displayName}$kids"
    }

    private fun showSwitchDialog(
        fragment: Fragment,
        findPreference: (String) -> Preference?,
        onProfileSwitched: (() -> Unit)?,
    ) {
        val context = fragment.requireContext()
        val profiles = ProfileManager.profiles()
        if (profiles.isEmpty()) return

        val names = profiles.map { it.displayName }.toTypedArray()
        AlertDialog.Builder(context)
            .setTitle(R.string.profile_switch_title)
            .setItems(names) { _, which ->
                val target = profiles[which]
                if (target.id == ProfileManager.activeProfileId) return@setItems
                if (target.pinHash != null) {
                    promptPin(context, target) {
                        performSwitch(fragment, findPreference, target, onProfileSwitched)
                    }
                } else {
                    performSwitch(fragment, findPreference, target, onProfileSwitched)
                }
            }
            .show()
    }

    private fun showCreateDialog(
        fragment: Fragment,
        findPreference: (String) -> Preference?,
        onProfileSwitched: (() -> Unit)?,
    ) {
        val context = fragment.requireContext()
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (24 * context.resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, 0)
        }
        val nameInput = EditText(context).apply {
            hint = context.getString(R.string.profile_name_hint)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            imeOptions = EditorInfo.IME_ACTION_DONE
        }
        val kidsCheck = CheckBox(context).apply {
            text = context.getString(R.string.profile_kids_title)
            setPadding(0, (12 * context.resources.displayMetrics.density).toInt(), 0, 0)
        }
        container.addView(nameInput)
        container.addView(kidsCheck)

        AlertDialog.Builder(context)
            .setTitle(R.string.profile_create_title)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = nameInput.text?.toString()?.trim().orEmpty()
                if (name.isEmpty()) {
                    Toast.makeText(context, R.string.profile_name_empty, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val created = ProfileManager.create(name = name, isKids = kidsCheck.isChecked)
                Toast.makeText(
                    context,
                    context.getString(R.string.profile_created_toast, created.displayName),
                    Toast.LENGTH_SHORT,
                ).show()
                AlertDialog.Builder(context)
                    .setMessage(context.getString(R.string.profile_switch_prompt, created.displayName))
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        performSwitch(fragment, findPreference, created, onProfileSwitched)
                    }
                    .setNegativeButton(android.R.string.cancel) { _, _ ->
                        refresh(findPreference, context)
                    }
                    .show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showRenameDialog(
        fragment: Fragment,
        findPreference: (String) -> Preference?,
    ) {
        val context = fragment.requireContext()
        val profile = ProfileManager.activeProfile() ?: return
        val input = EditText(context).apply {
            setText(profile.displayName)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            imeOptions = EditorInfo.IME_ACTION_DONE
            setSelection(text?.length ?: 0)
            val pad = (24 * context.resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, pad / 2)
        }

        AlertDialog.Builder(context)
            .setTitle(R.string.profile_rename_title)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.isEmpty()) {
                    Toast.makeText(context, R.string.profile_name_empty, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                if (ProfileManager.rename(profile.id, name)) {
                    Toast.makeText(context, R.string.profile_renamed_toast, Toast.LENGTH_SHORT).show()
                    refresh(findPreference, context)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showDeleteDialog(
        fragment: Fragment,
        findPreference: (String) -> Preference?,
        onProfileSwitched: (() -> Unit)?,
    ) {
        val context = fragment.requireContext()
        val profile = ProfileManager.activeProfile() ?: return
        if (ProfileManager.profiles().size <= 1) {
            Toast.makeText(context, R.string.profile_cannot_delete_last, Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(context)
            .setMessage(context.getString(R.string.profile_delete_confirm, profile.displayName))
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val wasActive = profile.id == ProfileManager.activeProfileId
                if (ProfileManager.delete(profile.id)) {
                    Toast.makeText(context, R.string.profile_deleted_toast, Toast.LENGTH_SHORT).show()
                    refresh(findPreference, context)
                    if (wasActive) onProfileSwitched?.invoke()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun showPinEditor(
        fragment: Fragment,
        findPreference: (String) -> Preference?,
    ) {
        val context = fragment.requireContext()
        val input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            imeOptions = EditorInfo.IME_ACTION_DONE
            hint = context.getString(R.string.profile_pin_title)
            val pad = (24 * context.resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, pad / 2)
        }

        AlertDialog.Builder(context)
            .setTitle(R.string.profile_pin_title)
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val pin = input.text?.toString()?.trim().orEmpty()
                if (pin.isEmpty()) return@setPositiveButton
                if (ProfileManager.setPin(ProfileManager.activeProfileId, pin)) {
                    Toast.makeText(context, R.string.profile_pin_saved, Toast.LENGTH_SHORT).show()
                    refresh(findPreference, context)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun promptPin(context: Context, profile: UserProfile, onVerified: () -> Unit) {
        val input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            imeOptions = EditorInfo.IME_ACTION_DONE
            val pad = (24 * context.resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, pad / 2)
        }

        AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.profile_pin_enter, profile.displayName))
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val pin = input.text?.toString()?.trim().orEmpty()
                if (ProfileManager.verifyPin(profile.id, pin)) {
                    onVerified()
                } else {
                    Toast.makeText(context, R.string.profile_pin_invalid, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun performSwitch(
        fragment: Fragment,
        findPreference: (String) -> Preference?,
        target: UserProfile,
        onProfileSwitched: (() -> Unit)?,
    ) {
        val context = fragment.requireContext()
        if (!ProfileManager.switchTo(context, target.id)) return
        Toast.makeText(
            context,
            context.getString(R.string.profile_switched_toast, target.displayName),
            Toast.LENGTH_SHORT,
        ).show()
        refresh(findPreference, context)
        onProfileSwitched?.invoke()
    }
}
