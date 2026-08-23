package com.betterstreamflix.polish

import android.content.Context
import android.widget.Toast
import androidx.core.content.edit

/**
 * First run helper — detects and manages first-run experience
 * and migration from previous versions.
 */
object FirstRunHelper {

    private const val PREFS_NAME = "first_run"
    private const val KEY_FIRST_RUN_DONE = "first_run_done"
    private const val KEY_LAST_VERSION_CODE = "last_version_code"
    private const val KEY_MIGRATION_DONE = "migration_done"

    /**
     * Check if this is the first run.
     */
    fun isFirstRun(context: Context): Boolean {
        return !context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_FIRST_RUN_DONE, false)
    }

    /**
     * Mark first run as complete.
     */
    fun completeFirstRun(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_FIRST_RUN_DONE, true)
            .putInt(KEY_LAST_VERSION_CODE, com.betterstreamflix.utils.AppConfig.versionCode)
            .apply()
    }

    /**
     * Check if this is an app update.
     */
    fun isAppUpdate(context: Context): Boolean {
        val lastVersion = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_LAST_VERSION_CODE, 0)
        return lastVersion > 0 && lastVersion < com.betterstreamflix.utils.AppConfig.versionCode
    }

    /**
     * Check if migration is needed for a specific version.
     */
    fun needsMigration(context: Context, targetVersion: Int): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt("$KEY_MIGRATION_DONE:$targetVersion", 0) == 0
    }

    /**
     * Mark migration as done for a specific version.
     */
    fun markMigrationDone(context: Context, version: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putInt("$KEY_MIGRATION_DONE:$version", 1)
            .apply()
    }

    /**
     * Update the last version code after update processing.
     */
    fun updateVersionCode(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putInt(KEY_LAST_VERSION_CODE, com.betterstreamflix.utils.AppConfig.versionCode)
            .apply()
    }

    /**
     * Get the last version code.
     */
    fun getLastVersionCode(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_LAST_VERSION_CODE, 0)
    }

    /**
     * Show a welcome toast for new users.
     */
    fun showWelcomeToast(context: Context) {
        if (isFirstRun(context)) {
            Toast.makeText(context, "Welcome to BetterStreamflix!", Toast.LENGTH_LONG).show()
        }
    }
}
