package com.dskja.betterstreamflix.support

import android.app.Activity
import android.app.Dialog
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.navigation.fragment.NavHostFragment
import com.dskja.betterstreamflix.R
import com.dskja.betterstreamflix.fragments.player.PlayerMobileFragment
import com.dskja.betterstreamflix.fragments.player.PlayerTvFragment
import com.dskja.betterstreamflix.ui.support.SupportStartupMobileDialog
import com.dskja.betterstreamflix.ui.support.SupportStartupTvDialog
import com.dskja.betterstreamflix.utils.UserPreferences
import com.dskja.betterstreamflix.utils.getCurrentFragment

/**
 * Shows the premium startup support presentation once the main surface is ready.
 * Never blocks playback; respects [UserPreferences.neverShowSupportOnStart].
 */
object SupportStartupController {

    private const val SHOW_DELAY_MS = 1_600L

    fun schedule(activity: FragmentActivity, isTv: Boolean) {
        if (UserPreferences.neverShowSupportOnStart) return
        if (activity.isFinishing) return

        activity.window.decorView.postDelayed({
            if (activity.isFinishing || activity.isDestroyed) return@postDelayed
            if (!activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return@postDelayed
            if (UserPreferences.neverShowSupportOnStart) return@postDelayed
            if (isPlaybackActive(activity)) return@postDelayed

            show(activity, isTv)
        }, SHOW_DELAY_MS)
    }

    private fun show(activity: FragmentActivity, isTv: Boolean) {
        val openHub: () -> Unit = {
            runCatching {
                val navHost = activity.supportFragmentManager
                    .findFragmentById(R.id.nav_main_fragment) as? NavHostFragment
                navHost?.navController?.navigate(R.id.support)
            }
            Unit
        }
        val dialog: Dialog = if (isTv) {
            SupportStartupTvDialog(activity, onOpenSupportHub = openHub)
        } else {
            SupportStartupMobileDialog(activity, onOpenSupportHub = openHub)
        }
        runCatching { dialog.show() }
    }

    private fun isPlaybackActive(activity: FragmentActivity): Boolean {
        val current = activity.getCurrentFragment()
        return current is PlayerMobileFragment || current is PlayerTvFragment
    }
}
