package com.betterstreamflix.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.view.View
import com.betterstreamflix.R
import com.google.android.material.snackbar.Snackbar

/**
 * Lightweight offline indicator for catalog screens.
 */
object OfflineBanner {

    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun showIfOffline(anchor: View) {
        if (isOnline(anchor.context)) return
        Snackbar.make(anchor, R.string.offline_banner_message, Snackbar.LENGTH_LONG).show()
    }

    fun showStaleCache(anchor: View) {
        Snackbar.make(anchor, R.string.home_cached_content_banner, Snackbar.LENGTH_LONG).show()
    }
}
