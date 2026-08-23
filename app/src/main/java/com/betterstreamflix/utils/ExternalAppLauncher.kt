package com.betterstreamflix.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.browser.customtabs.CustomTabsIntent

/**
 * External app launcher — handles opening URLs and external apps.
 */
object ExternalAppLauncher {

    /**
     * Open a URL in the browser (using Custom Tabs if available).
     */
    fun openUrl(context: Context, url: String) {
        val uri = Uri.parse(url)
        val customTabsIntent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
        try {
            customTabsIntent.launchUrl(context, uri)
        } catch (e: Exception) {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    /**
     * Open an app in the Google Play Store.
     */
    fun openPlayStore(context: Context, packageName: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            openUrl(context, "https://play.google.com/store/apps/details?id=$packageName")
        }
    }

    /**
     * Check if an app is installed.
     */
    fun isAppInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Send an email intent.
     */
    fun sendEmail(context: Context, to: String, subject: String = "", body: String = "") {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(to))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Send email"))
    }

    /**
     * Open a WhatsApp chat.
     */
    fun openWhatsApp(context: Context, phoneNumber: String) {
        val uri = Uri.parse("https://wa.me/$phoneNumber")
        openUrl(context, uri.toString())
    }
}
