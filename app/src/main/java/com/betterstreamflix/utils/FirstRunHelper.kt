package com.betterstreamflix.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import com.betterstreamflix.R

/**
 * First-run helper — detects and tracks first app launch, first-run legal
 * disclaimer acceptance, and post-update "what's new" notices.
 */
object FirstRunHelper {

    private const val PREFS_NAME = "first_run"
    private const val KEY_FIRST_RUN = "is_first_run"
    private const val KEY_FIRST_RUN_VERSION = "first_run_version"
    private const val KEY_LAUNCH_COUNT = "launch_count"
    private const val KEY_LEGAL_ACCEPTED = "legal_accepted"
    private const val KEY_LAST_SEEN_VERSION = "last_seen_version"

    private const val RELEASES_URL = "https://github.com/dskja/BetterStreamflix/releases/latest"

    private fun getPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Check if this is the first app launch.
     */
    fun isFirstRun(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_FIRST_RUN, true)
    }

    /**
     * Mark first run as complete.
     */
    fun markFirstRunComplete(context: Context) {
        getPrefs(context).edit {
            putBoolean(KEY_FIRST_RUN, false)
            putInt(KEY_FIRST_RUN_VERSION, AppConfig.versionCode)
        }
    }

    /**
     * Check if this is the first launch for a specific version.
     * Useful for showing "what's new" dialogs after updates.
     */
    fun isFirstRunForVersion(context: Context): Boolean {
        val lastVersion = getPrefs(context).getInt(KEY_FIRST_RUN_VERSION, 0)
        return lastVersion < AppConfig.versionCode
    }

    /**
     * Mark version-specific first run as complete.
     */
    fun markVersionFirstRunComplete(context: Context) {
        getPrefs(context).edit {
            putInt(KEY_FIRST_RUN_VERSION, AppConfig.versionCode)
        }
    }

    /**
     * Get the total number of app launches.
     */
    fun getLaunchCount(context: Context): Int {
        return getPrefs(context).getInt(KEY_LAUNCH_COUNT, 0)
    }

    /**
     * Increment the launch counter.
     */
    fun incrementLaunchCount(context: Context) {
        getPrefs(context).edit {
            putInt(KEY_LAUNCH_COUNT, getLaunchCount(context) + 1)
        }
    }

    /**
     * Check if user has launched the app at least N times.
     */
    fun hasLaunchedAtLeast(context: Context, times: Int): Boolean {
        return getLaunchCount(context) >= times
    }

    /**
     * Whether the user has accepted the legal disclaimer.
     */
    fun isLegalAccepted(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_LEGAL_ACCEPTED, false)
    }

    /**
     * Mark the legal disclaimer as accepted.
     */
    fun setLegalAccepted(context: Context) {
        getPrefs(context).edit {
            putBoolean(KEY_LEGAL_ACCEPTED, true)
        }
    }

    /**
     * The app version name the user last saw the "what's new" notice for, if any.
     */
    fun getLastSeenVersion(context: Context): String? {
        return getPrefs(context).getString(KEY_LAST_SEEN_VERSION, null)
    }

    /**
     * Record the current app version as seen, so the "what's new" notice
     * is not shown again until the next update.
     */
    fun updateLastSeenVersion(context: Context, version: String = AppConfig.versionName) {
        getPrefs(context).edit {
            putString(KEY_LAST_SEEN_VERSION, version)
        }
    }

    /**
     * True when the app has been updated since the user last saw the
     * "what's new" notice (including the very first launch).
     */
    fun hasNewVersionSinceLastSeen(context: Context): Boolean {
        return getLastSeenVersion(context) != AppConfig.versionName
    }

    /**
     * Presents the onboarding dialogs for the given activity, in order:
     * 1. The first-run legal disclaimer, if not yet accepted (non-cancelable).
     * 2. The "what's new" notice, if the app was updated since last seen —
     *    only shown once the legal disclaimer has been accepted.
     */
    fun presentOnboardingDialogs(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return

        if (!isLegalAccepted(activity)) {
            showLegalDisclaimerDialog(activity) {
                setLegalAccepted(activity)
                maybeShowWhatsNewDialog(activity)
            }
        } else {
            maybeShowWhatsNewDialog(activity)
        }
    }

    private fun showLegalDisclaimerDialog(activity: Activity, onAccepted: () -> Unit) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.legal_disclaimer_title)
            .setMessage(R.string.legal_disclaimer_message)
            .setCancelable(false)
            .setPositiveButton(R.string.legal_disclaimer_accept) { _, _ -> onAccepted() }
            .show()
    }

    private fun maybeShowWhatsNewDialog(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        if (!hasNewVersionSinceLastSeen(activity)) return

        AlertDialog.Builder(activity)
            .setTitle(activity.getString(R.string.whats_new_title, AppConfig.versionName))
            .setMessage(R.string.whats_new_message)
            .setCancelable(true)
            .setNeutralButton(R.string.whats_new_view_changelog) { _, _ ->
                runCatching {
                    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_URL)))
                }
                updateLastSeenVersion(activity)
            }
            .setPositiveButton(R.string.whats_new_dismiss) { _, _ ->
                updateLastSeenVersion(activity)
            }
            .setOnCancelListener {
                updateLastSeenVersion(activity)
            }
            .show()
    }
}
