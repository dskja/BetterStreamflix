package com.betterstreamflix.utils

import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import com.betterstreamflix.R

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

        val names = profiles.map { it.name }.toTypedArray()
        val activeIndex = profiles.indexOfFirst { it.id == UserProfiles.active().id }.coerceAtLeast(0)

        AlertDialog.Builder(activity)
            .setTitle(R.string.profile_picker_title)
            .setSingleChoiceItems(names, activeIndex) { dialog, which ->
                UserProfiles.setActive(profiles[which].id)
                dialog.dismiss()
            }
            .setPositiveButton(R.string.profile_picker_continue) { dialog, _ -> dialog.dismiss() }
            .setCancelable(true)
            .show()
    }
}
