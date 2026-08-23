package com.betterstreamflix.ui

import android.app.Activity
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment

/**
 * Helper for double-back-to-exit pattern on mobile.
 */
class DoubleBackToExitHelper(
    private val activity: Activity,
    private val exitMessage: String = "Press back again to exit",
    private val exitDelayMs: Long = 2000,
) {
    private var backPressedTime: Long = 0

    /**
     * Call this from onBackPressed or as OnBackPressedCallback.
     * Returns true if the back press was handled (showing toast), false if should exit.
     */
    fun handleBackPress(): Boolean {
        val now = System.currentTimeMillis()
        if (now - backPressedTime < exitDelayMs) {
            return false // Second press within delay — allow exit
        }
        backPressedTime = now
        Toast.makeText(activity, exitMessage, Toast.LENGTH_SHORT).show()
        return true
    }

    /**
     * Create an OnBackPressedCallback for use with the OnBackPressedDispatcher.
     */
    fun asCallback(): OnBackPressedCallback {
        return object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (!handleBackPress()) {
                    isEnabled = false
                    activity.onBackPressed()
                }
            }
        }
    }
}

/**
 * Fragment extension to enable double-back-to-exit.
 */
fun Fragment.enableDoubleBackToExit(
    exitMessage: String = "Press back again to exit",
    exitDelayMs: Long = 2000,
) {
    val helper = DoubleBackToExitHelper(requireActivity(), exitMessage, exitDelayMs)
    requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, helper.asCallback())
}
