package com.betterstreamflix.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * First-run helper — detects and tracks first app launch.
 * Useful for showing onboarding or initial setup.
 */
object FirstRunHelper {

    private const val PREFS_NAME = "first_run"
    private const val KEY_FIRST_RUN = "is_first_run"
    private const val KEY_FIRST_RUN_VERSION = "first_run_version"
    private const val KEY_LAUNCH_COUNT = "launch_count"

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
}
