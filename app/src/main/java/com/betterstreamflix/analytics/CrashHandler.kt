package com.betterstreamflix.analytics

import android.content.Context
import androidx.core.content.edit

/**
 * Crash handler — installs an uncaught exception handler that logs
 * crashes before the app terminates.
 */
object CrashHandler {

    private const val PREFS_NAME = "crash_handler"
    private const val KEY_LAST_CRASH = "last_crash"
    private const val KEY_CRASH_COUNT = "crash_count"

    private var previousHandler: Thread.UncaughtExceptionHandler? = null
    private var crashCallback: ((Throwable) -> Unit)? = null

    /**
     * Install the crash handler.
     */
    fun install(context: Context, callback: ((Throwable) -> Unit)? = null) {
        crashCallback = callback
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Save crash info
            saveCrashInfo(context, throwable)

            // Notify callback
            crashCallback?.invoke(throwable)

            // Log the crash
            DebugLogger.e("CrashHandler", "Uncaught exception in ${thread.name}: ${throwable.message}")
            DebugLogger.e("CrashHandler", throwable.stackTraceToString())

            // Let the previous handler deal with it
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    /**
     * Get the last crash info.
     */
    fun getLastCrash(context: Context): String? {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_LAST_CRASH, null)
    }

    /**
     * Get crash count.
     */
    fun getCrashCount(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_CRASH_COUNT, 0)
    }

    /**
     * Clear crash data.
     */
    fun clearCrashData(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            clear()
        }
    }

    private fun saveCrashInfo(context: Context, throwable: Throwable) {
        val crashInfo = buildString {
            appendLine("Time: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())}")
            appendLine("Exception: ${throwable.javaClass.name}")
            appendLine("Message: ${throwable.message}")
            appendLine("Stack Trace:")
            appendLine(throwable.stackTraceToString())
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val count = prefs.getInt(KEY_CRASH_COUNT, 0) + 1
        prefs.edit {
            putString(KEY_LAST_CRASH, crashInfo)
            putInt(KEY_CRASH_COUNT, count)
        }
    }
}
