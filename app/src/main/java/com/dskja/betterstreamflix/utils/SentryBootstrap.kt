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

/**
 * Central Sentry wiring for BetterStreamflix: init, release/environment,
 * scrubbing, navigation breadcrumbs, and cloud-user identity.
 *
 * Capture policy: debug builds send everything (100% sample rates).
 * Production dials traces/profiles/replay down to control cost/noise;
 * errors and error-triggered replays stay fully sampled. Sensitive
 * headers/cookies are scrubbed — no error-type filtering.
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
            options.isSendDefaultPii = false
            options.isEnableUserInteractionTracing = true
            options.isAttachScreenshot = true
            options.isAttachViewHierarchy = true
            options.isEnableAutoSessionTracking = true
            options.isAnrEnabled = true
            options.isCollectAdditionalContext = true
            // Production: balanced capture. Debug: full capture for local diagnosis.
            if (BuildConfig.DEBUG) {
                options.tracesSampleRate = 1.0
                options.profileSessionSampleRate = 1.0
                options.sessionReplay.sessionSampleRate = 1.0
                options.sessionReplay.onErrorSampleRate = 1.0
            } else {
                options.tracesSampleRate = 0.2
                options.profileSessionSampleRate = 0.1
                options.sessionReplay.sessionSampleRate = 0.05
                options.sessionReplay.onErrorSampleRate = 1.0
            }
            options.profileLifecycle = ProfileLifecycle.TRACE
            options.logs.isEnabled = true

            options.beforeSend =
                SentryOptions.BeforeSendCallback { event: SentryEvent, _: Hint ->
                    // Drop coroutine cancellation noise (JobCancellationException).
                    val values = event.exceptions.orEmpty()
                    if (values.any { ex ->
                            val type = ex.type.orEmpty()
                            val value = ex.value.orEmpty()
                            type.contains("CancellationException", ignoreCase = true) ||
                                value.contains("Job was cancelled", ignoreCase = true) ||
                                value.contains("StandaloneCoroutine was cancelled", ignoreCase = true)
                        }
                    ) {
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
