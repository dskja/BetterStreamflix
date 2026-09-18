package com.dskja.betterstreamflix.utils

import android.app.Application
import androidx.navigation.NavController
import com.dskja.betterstreamflix.BuildConfig
import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid
import io.sentry.protocol.Feedback
import io.sentry.protocol.User
import io.sentry.ProfileLifecycle
import kotlinx.coroutines.CancellationException

/**
 * Central Sentry wiring for BetterStreamflix: init, release/environment,
 * scrubbing, navigation breadcrumbs, and cloud-user identity.
 */
object SentryBootstrap {

    const val DSN =
        "https://a725644155c0150523b89069e26ec38c@o4512107753177088.ingest.de.sentry.io/4512107833655376"

    private val sensitiveHeaderNames = setOf(
        "authorization",
        "cookie",
        "set-cookie",
        "x-api-key",
        "proxy-authorization",
    )

    fun init(application: Application) {
        SentryAndroid.init(application) { options ->
            options.dsn = DSN
            options.isForceInit = true
            options.release =
                "${BuildConfig.APPLICATION_ID}@${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"
            options.environment = if (BuildConfig.DEBUG) "debug" else "production"
            options.dist = BuildConfig.VERSION_CODE.toString()
            options.isDebug = BuildConfig.DEBUG
            options.isSendDefaultPii = true
            options.isEnableUserInteractionTracing = true
            options.isAttachScreenshot = true
            options.isAttachViewHierarchy = true
            options.isEnableAutoSessionTracking = true
            options.isAnrEnabled = true
            options.isCollectAdditionalContext = true
            options.tracesSampleRate = if (BuildConfig.DEBUG) 1.0 else 0.35
            options.profileSessionSampleRate = if (BuildConfig.DEBUG) 1.0 else 0.15
            options.profileLifecycle = ProfileLifecycle.TRACE
            options.sessionReplay.sessionSampleRate = if (BuildConfig.DEBUG) 0.2 else 0.08
            options.sessionReplay.onErrorSampleRate = 1.0
            options.logs.isEnabled = true

            // Coroutine cancellations are normal lifecycle noise, not defects.
            options.addIgnoredExceptionForType(CancellationException::class.java)
            options.setIgnoredErrors(
                listOf(
                    ".*Job was cancelled.*",
                    ".*HTTP Client Error with status code: 4\\d\\d.*",
                    ".*HTTP Client Error with status code: 5\\d\\d.*",
                    ".*No extractors found.*",
                ),
            )

            options.beforeSend =
                SentryOptions.BeforeSendCallback { event: SentryEvent, _: Hint ->
                    if (event.level == SentryLevel.DEBUG && !BuildConfig.DEBUG) {
                        return@BeforeSendCallback null
                    }
                    if (shouldDropExpectedNoise(event)) {
                        return@BeforeSendCallback null
                    }
                    scrubEvent(event)
                    event
                }

            options.beforeBreadcrumb =
                SentryOptions.BeforeBreadcrumbCallback { breadcrumb, _ ->
                    scrubBreadcrumb(breadcrumb)
                    breadcrumb
                }
        }

        Sentry.configureScope { scope ->
            scope.setTag("app_layout", BuildConfig.APP_LAYOUT)
            scope.setTag("debug_build", BuildConfig.DEBUG.toString())
            scope.setTag("version_name", BuildConfig.VERSION_NAME)
            scope.setTag("version_code", BuildConfig.VERSION_CODE.toString())
        }
    }

    fun setCloudUser(userId: String?, email: String?) {
        if (userId.isNullOrBlank()) {
            clearUser()
            return
        }
        Sentry.setUser(
            User().apply {
                id = userId
                this.email = email?.takeIf { it.isNotBlank() }
            },
        )
    }

    fun clearUser() {
        Sentry.setUser(null)
    }

    fun trackNavigation(navController: NavController) {
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val label = destination.label?.toString()
                ?.takeIf { it.isNotBlank() }
                ?: destination.route
                ?: destination.id.toString()
            val crumb = Breadcrumb.navigation(label, "navigate").apply {
                category = "navigation"
                level = SentryLevel.INFO
                setData("destination_id", destination.id)
            }
            Sentry.addBreadcrumb(crumb)
            Sentry.configureScope { scope ->
                scope.setTag("screen", label.take(64))
            }
        }
    }

    fun captureFeedback(message: String, name: String? = null, email: String? = null) {
        val trimmed = message.trim()
        if (trimmed.isEmpty()) return
        val feedback = Feedback(trimmed).apply {
            name?.takeIf { it.isNotBlank() }?.let { setName(it) }
            email?.takeIf { it.isNotBlank() }?.let { setContactEmail(it) }
        }
        Sentry.feedback().capture(feedback)
    }

    private fun scrubEvent(event: SentryEvent) {
        event.request?.headers?.keys
            ?.filter { sensitiveHeaderNames.contains(it.lowercase()) }
            ?.forEach { key -> event.request?.headers?.put(key, "[Filtered]") }
        event.request?.cookies = event.request?.cookies?.let { "[Filtered]" }
    }

    private fun shouldDropExpectedNoise(event: SentryEvent): Boolean {
        val exceptions = event.exceptions.orEmpty()
        for (ex in exceptions) {
            val type = ex.type.orEmpty()
            val value = ex.value.orEmpty()
            if (type.contains("CancellationException", ignoreCase = true) ||
                value.contains("Job was cancelled", ignoreCase = true)
            ) {
                return true
            }
            if (type.contains("HttpException", ignoreCase = true) ||
                type.contains("SentryHttpClientException", ignoreCase = true)
            ) {
                val code = Regex("""\b([45]\d\d)\b""").find(value)?.groupValues?.getOrNull(1)
                    ?.toIntOrNull()
                if (code != null && code in 400..599) return true
            }
            if (CrashReporter.isExpectedProviderNoise(
                    Throwable(value),
                    "$type $value ${event.message?.formatted.orEmpty()}",
                )
            ) {
                return true
            }
        }
        val message = event.message?.formatted.orEmpty()
        return CrashReporter.isExpectedProviderNoise(null, message)
    }

    private fun scrubBreadcrumb(breadcrumb: Breadcrumb) {
        val data = breadcrumb.data ?: return
        data.keys
            .filter { key ->
                val lower = key.lowercase()
                lower.contains("cookie") ||
                    lower.contains("authorization") ||
                    lower.contains("token") ||
                    lower.contains("password")
            }
            .forEach { key -> data[key] = "[Filtered]" }
    }
}
