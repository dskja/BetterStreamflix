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
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException

/**
 * Lightweight local crash / error reporting, with optional Sentry forwarding.
 * Writes under filesDir/crash-logs and chains the previous default handler
 * (Sentry installs its own handler via ContentProvider before Application.onCreate).
 *
 * Expected provider / extractor / network failures stay local-only so Sentry
 * is not flooded by SerienStream 404s, dead hosts, cancellations, etc.
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
        if (isExpectedProviderNoise(error, message)) return
        runCatching {
            if (error != null) {
                io.sentry.Sentry.captureException(error) { scope ->
                    scope.setTag("local_tag", tag)
                    scope.setExtra("message", message)
                }
            } else {
                io.sentry.Sentry.captureMessage("$tag: $message")
            }
        }
    }

    /**
     * Scraping / streaming sites routinely return 4xx/5xx, cancel jobs, or lack extractors.
     * Those are operational noise, not app defects.
     */
    fun isExpectedProviderNoise(error: Throwable?, message: String = ""): Boolean {
        val combined = buildString {
            append(message)
            generateSequence(error) { it.cause }.forEach { t ->
                append(' ')
                append(t::class.java.name)
                append(' ')
                append(t.message.orEmpty())
            }
        }.lowercase(Locale.US)

        if (combined.contains("job was cancelled") ||
            combined.contains("cancellationexception") ||
            combined.contains("coroutines.cancellation")
        ) {
            return true
        }
        if (error != null && generateSequence(error) { it.cause }.any { it is CancellationException }) {
            return true
        }

        val http = generateSequence(error) { it.cause }
            .filterIsInstance<HttpException>()
            .firstOrNull()
        if (http != null && http.code() in 400..599) return true

        // OkHttp / Sentry HTTP client wrappers for upstream site failures.
        if (combined.contains("sentryhttpclientexception") &&
            (combined.contains("status code: 4") || combined.contains("status code: 5"))
        ) {
            return true
        }

        val noiseHints = listOf(
            "no extractors found",
            "http 404",
            "http 410",
            "http 502",
            "http 503",
            "http 520",
            "http 521",
            "http 522",
            "http 524",
            "timed out",
            "unreachable",
            "cloudflare",
            "end of input at character 0",
            "gethome failed",
            "getvideo failed",
        )
        return noiseHints.any { combined.contains(it) }
    }

    fun latestCrashText(context: Context): String? {
        val latest = crashDir(context).listFiles()
            ?.filter { it.isFile && it.name.endsWith(".txt") }
            ?.maxByOrNull { it.lastModified() }
            ?: return null
        return runCatching { latest.readText() }.getOrNull()
    }

    fun clearAll(context: Context): Boolean {
        return runCatching {
            val dir = crashDir(context)
            dir.listFiles()?.forEach { it.delete() }
            true
        }.getOrDefault(false)
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
