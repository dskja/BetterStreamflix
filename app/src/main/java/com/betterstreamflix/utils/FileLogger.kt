package com.betterstreamflix.utils

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Comprehensive file logger that writes everything to a file on the device.
 * The log file is at: /sdcard/Android/data/com.betterstreamflix/files/logs/app_debug.log
 * Pull it via: adb pull /sdcard/Android/data/com.betterstreamflix/files/logs/app_debug.log
 */
object FileLogger {
    private const val TAG = "FileLogger"
    private const val MAX_FILE_SIZE = 5 * 1024 * 1024L // 5 MB, rotate after that

    @Volatile
    private var logFile: File? = null

    @Volatile
    private var enabled = true

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    private val lock = Any()

    fun init(context: Context) {
        try {
            val logsDir = File(context.getExternalFilesDir(null), "logs").apply { mkdirs() }
            logFile = File(logsDir, "app_debug.log")
            // Also create a fresh log for this session
            val sessionFile = File(logsDir, "app_debug_latest.log")
            if (sessionFile.exists()) sessionFile.delete()
            sessionFile.createNewFile()

            // Write session header
            val header = buildString {
                appendLine("=" .repeat(80))
                appendLine("BetterStreamflix Debug Log — Session started ${dateFormat.format(Date())}")
                appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                appendLine("App: ${getAppVersion(context)}")
                appendLine("Process PID: ${android.os.Process.myPid()}")
                appendLine("=" .repeat(80))
            }
            sessionFile.writeText(header)
            logFile = sessionFile

            // Also append to the persistent log
            File(logsDir, "app_debug.log").appendText(header)

            Log.i(TAG, "FileLogger initialized. Log at: ${sessionFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to init FileLogger", e)
            enabled = false
        }
    }

    fun log(level: String, tag: String, message: String, throwable: Throwable? = null) {
        if (!enabled) return
        synchronized(lock) {
            val file = logFile ?: return
            try {
                val timestamp = dateFormat.format(Date())
                val threadName = Thread.currentThread().name
                val sb = StringBuilder()
                sb.append("$timestamp [$level] [$threadName] $tag: $message")
                if (throwable != null) {
                    sb.append("\n")
                    val sw = StringWriter()
                    val pw = PrintWriter(sw)
                    throwable.printStackTrace(pw)
                    sb.append(sw.toString())
                }
                sb.append("\n")
                file.appendText(sb.toString())

                // Also write to persistent log
                val dir = file.parentFile
                if (dir != null) {
                    File(dir, "app_debug.log").appendText(sb.toString())
                }
            } catch (_: Exception) {
                // Don't let logging crash the app
            }
        }
    }

    fun d(tag: String, message: String) = log("D", tag, message)
    fun i(tag: String, message: String) = log("I", tag, message)
    fun w(tag: String, message: String, throwable: Throwable? = null) = log("W", tag, message, throwable)
    fun e(tag: String, message: String, throwable: Throwable? = null) = log("E", tag, message, throwable)

    fun logLifecycle(stage: String, extra: String = "") {
        i("Lifecycle", "=== $stage === $extra")
    }

    fun logInitialization(component: String, result: Result<*>) {
        if (result.isSuccess) {
            i("Init", "✓ $component initialized successfully: ${result.getOrNull()}")
        } else {
            e("Init", "✗ $component FAILED", result.exceptionOrNull())
        }
    }

    fun getLogFilePath(): String? = logFile?.absolutePath

    fun getPersistentLogPath(): String? = logFile?.parentFile?.let { File(it, "app_debug.log").absolutePath }

    private fun getAppVersion(context: Context): String {
        return try {
            val pi = context.packageManager.getPackageInfo(context.packageName, 0)
            "${pi.versionName} (${pi.longVersionCode})"
        } catch (_: Exception) {
            "unknown"
        }
    }

    /**
     * Install a global uncaught exception handler that logs crashes to the file
     * before delegating to the default handler.
     */
    fun installCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            e("FATAL", "Uncaught exception on thread ${thread.name}", throwable)
            // Give the logger a moment to flush
            Thread.sleep(500)
            defaultHandler?.uncaughtException(thread, throwable)
        }
        i("Init", "Crash handler installed")
    }
}
