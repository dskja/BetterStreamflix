package com.dskja.betterstreamflix.utils

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lightweight local crash / error reporting (no third-party SDK required).
 * Writes under filesDir/crash-logs and chains the previous default handler.
 */
object CrashReporter {
    private const val TAG = "CrashReporter"
    private const val DIR = "crash-logs"
    private const val MAX_FILES = 20
    private val installed = AtomicBoolean(false)

    @Volatile
    private var appContext: Context? = null

    fun install(context: Context) {
        if (!installed.compareAndSet(false, true)) return
        appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeCrash(context.applicationContext, thread, throwable) }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun logNonFatal(tag: String, message: String, error: Throwable? = null) {
        Log.e(tag, message, error)
        val ctx = appContext ?: return
        runCatching {
            val sw = StringWriter()
            error?.printStackTrace(PrintWriter(sw))
            appendEvent(
                ctx,
                buildString {
                    appendLine("NON_FATAL $tag: $message")
                    if (error != null) append(sw.toString())
                },
            )
        }
    }

    fun latestCrashText(context: Context): String? {
        val latest = crashDir(context).listFiles()
            ?.filter { it.isFile && it.name.endsWith(".txt") }
            ?.maxByOrNull { it.lastModified() }
            ?: return null
        return runCatching { latest.readText() }.getOrNull()
    }

    fun hasRecentCrash(context: Context, withinMs: Long = 7L * 24 * 60 * 60 * 1000): Boolean {
        val latest = crashDir(context).listFiles()
            ?.filter { it.isFile }
            ?.maxByOrNull { it.lastModified() }
            ?: return false
        return System.currentTimeMillis() - latest.lastModified() <= withinMs
    }

    private fun writeCrash(context: Context, thread: Thread, throwable: Throwable) {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val body = buildString {
            appendLine("BetterStreamflix crash")
            appendLine("time=$stamp")
            appendLine("thread=${thread.name}")
            appendLine("sdk=${Build.VERSION.SDK_INT}")
            appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("brand=${Build.BRAND}")
            appendLine("---")
            append(sw.toString())
        }
        val dir = crashDir(context)
        dir.mkdirs()
        File(dir, "crash-$stamp.txt").writeText(body)
        trimOld(dir)
    }

    private fun appendEvent(context: Context, body: String) {
        val dir = crashDir(context)
        dir.mkdirs()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        File(dir, "error-$stamp.txt").writeText(body)
        trimOld(dir)
    }

    private fun crashDir(context: Context): File = File(context.filesDir, DIR)

    private fun trimOld(dir: File) {
        val files = dir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() }
            ?: return
        files.drop(MAX_FILES).forEach { it.delete() }
    }
}
