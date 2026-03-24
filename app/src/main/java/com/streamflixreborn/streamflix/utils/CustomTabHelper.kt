package com.streamflixreborn.streamflix.utils

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsServiceConnection
import androidx.browser.customtabs.CustomTabsSession

class CustomTabHelper {

    private var client: CustomTabsClient? = null
    private var session: CustomTabsSession? = null
    private var serviceConnection: CustomTabsServiceConnection? = null
    private var boundContext: Context? = null

    fun warmup(context: Context) {
        if (serviceConnection != null) return

        val appContext = context.applicationContext
        val connection = object : CustomTabsServiceConnection() {
            override fun onCustomTabsServiceConnected(
                name: ComponentName,
                customTabsClient: CustomTabsClient
            ) {
                client = customTabsClient
                client?.warmup(0L)
                session = client?.newSession(null)
            }

            override fun onServiceDisconnected(name: ComponentName) {
                client = null
                session = null
                serviceConnection = null
                boundContext = null
            }
        }

        val didBind = CustomTabsClient.bindCustomTabsService(
            appContext,
            "com.android.chrome",
            connection
        )

        if (didBind) {
            serviceConnection = connection
            boundContext = appContext
        }
    }

    fun open(context: Context, url: String) {
        if (context !is Activity) {
            throw IllegalArgumentException("CustomTabs requires Activity context")
        }

        session?.mayLaunchUrl(Uri.parse(url), null, null)

        val intent = CustomTabsIntent.Builder(session)
            .setShowTitle(true)
            .build()

        intent.launchUrl(context, Uri.parse(url))
    }

    fun release() {
        val context = boundContext
        val connection = serviceConnection
        if (context != null && connection != null) {
            runCatching { context.unbindService(connection) }
        }
        client = null
        session = null
        serviceConnection = null
        boundContext = null
    }
}
