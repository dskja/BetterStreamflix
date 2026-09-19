package com.dskja.betterstreamflix.support

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.dskja.betterstreamflix.R
import com.dskja.betterstreamflix.utils.UserPreferences
import java.util.concurrent.TimeUnit

object SupportLinkOpener {

    /** Soft thanks expires if the user never returns to Support within this window. */
    private val APPRECIATION_TTL_MS = TimeUnit.HOURS.toMillis(6)

    /** Prefs-backed soft thanks flag (survives process death; TTL-gated). */
    val pendingAppreciation: Boolean
        get() = hasFreshAppreciationPending()

    fun markAppreciationPending() {
        UserPreferences.supportAppreciationPending = true
        UserPreferences.supportAppreciationPendingAtMs = System.currentTimeMillis()
    }

    fun consumeAppreciationPending(): Boolean {
        if (!hasFreshAppreciationPending()) {
            clearAppreciation()
            return false
        }
        clearAppreciation()
        return true
    }

    private fun hasFreshAppreciationPending(): Boolean {
        if (!UserPreferences.supportAppreciationPending) return false
        val at = UserPreferences.supportAppreciationPendingAtMs
        if (at <= 0L) return false
        return System.currentTimeMillis() - at <= APPRECIATION_TTL_MS
    }

    private fun clearAppreciation() {
        UserPreferences.supportAppreciationPending = false
        UserPreferences.supportAppreciationPendingAtMs = 0L
    }

    fun open(context: Context, url: String, markAppreciation: Boolean = false): Boolean {
        return try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            if (markAppreciation) markAppreciationPending()
            true
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.support_unable_to_open_link, Toast.LENGTH_SHORT).show()
            false
        } catch (_: Exception) {
            Toast.makeText(context, R.string.support_unable_to_open_link, Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun openProvider(context: Context, provider: SupportProvider): Boolean {
        if (provider.telegramDeepLink) {
            return openTelegram(context)
        }
        return open(context, provider.url, markAppreciation = provider.marksAppreciation)
    }

    fun openTelegram(context: Context): Boolean {
        return try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SupportUrls.TELEGRAM_APP_URL)))
            true
        } catch (_: Exception) {
            Toast.makeText(context, R.string.settings_telegram_not_found, Toast.LENGTH_SHORT).show()
            open(context, SupportUrls.TELEGRAM_URL, markAppreciation = false)
        }
    }

    fun openIssues(context: Context): Boolean =
        open(context, SupportUrls.GITHUB_ISSUES_URL, markAppreciation = false)

    fun openReleases(context: Context): Boolean =
        open(context, SupportUrls.GITHUB_RELEASES_URL, markAppreciation = false)
}
