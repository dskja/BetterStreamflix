package com.dskja.betterstreamflix.support

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.dskja.betterstreamflix.R

object SupportLinkOpener {

    /** Set when the user leaves the app for a support provider; consumed on return. */
    @Volatile
    var pendingAppreciation: Boolean = false
        private set

    fun markAppreciationPending() {
        pendingAppreciation = true
    }

    fun consumeAppreciationPending(): Boolean {
        val pending = pendingAppreciation
        pendingAppreciation = false
        return pending
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
}
