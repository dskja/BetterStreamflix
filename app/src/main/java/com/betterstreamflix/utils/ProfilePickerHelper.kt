package com.betterstreamflix.utils

import android.app.Dialog
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

        val dialog = Dialog(activity)
        dialog.setContentView(
            ComposeView(activity).apply {
                setContent {
                    BetterStreamflixTheme {
                        ProfilePickerScreen(
                            profiles = profiles,
                            activeId = UserProfiles.active().id,
                            onSelect = { profile ->
                                UserProfiles.setActive(profile.id)
                                dialog.dismiss()
                            },
                        )
                    }
                }
            },
        )
        dialog.setCancelable(true)
        dialog.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        dialog.show()
    }
}
