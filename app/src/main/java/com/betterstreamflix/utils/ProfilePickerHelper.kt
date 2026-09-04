package com.betterstreamflix.utils

import android.app.Dialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.FragmentActivity
import com.betterstreamflix.compose.screens.ProfilePickerScreen
import com.betterstreamflix.compose.theme.BetterStreamflixTheme

/**
 * Shows a profile picker when multiple local profiles exist.
 */
object ProfilePickerHelper {

    @Volatile
    private var shownThisProcess = false

    fun showIfNeeded(activity: FragmentActivity) {
        if (shownThisProcess) return
        val profiles = UserProfiles.list()
        if (profiles.size <= 1) return
        shownThisProcess = true
        show(activity)
    }

    fun show(activity: FragmentActivity) {
        val dialog = Dialog(activity)
        dialog.setContentView(
            ComposeView(activity).apply {
                setContent {
                    var profiles by remember { mutableStateOf(UserProfiles.list()) }
                    var activeId by remember { mutableStateOf(UserProfiles.active().id) }

                    BetterStreamflixTheme {
                        ProfilePickerScreen(
                            profiles = profiles,
                            activeId = activeId,
                            isTvLayout = AppConfig.isTv,
                            onSelect = { profile ->
                                UserProfiles.setActive(profile.id)
                                activeId = profile.id
                                dialog.dismiss()
                            },
                            onAddProfile = { name ->
                                UserProfiles.create(name)
                                profiles = UserProfiles.list()
                            },
                            onDeleteProfile = { profile ->
                                UserProfiles.delete(profile.id)
                                profiles = UserProfiles.list()
                                activeId = UserProfiles.active().id
                            },
                            onEditName = { profile, name ->
                                UserProfiles.upsert(profile.copy(name = name.trim().ifBlank { profile.name }))
                                profiles = UserProfiles.list()
                            },
                            onToggleKids = { profile, isKids ->
                                UserProfiles.upsert(profile.copy(isKids = isKids))
                                profiles = UserProfiles.list()
                            },
                        )
                    }
                }
            },
        )
        dialog.setCancelable(true)
        dialog.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
        )
        dialog.show()
    }
}
