package com.streamflixreborn.streamflix.utils

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsServiceConnection
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsSession

class CustomTabHelper {

    private var client: CustomTabsClient? = null
    private var session: CustomTabsSession? = null

    fun warmup(context: Context) {
        CustomTabsClient.bindCustomTabsService(
            context,
            "com.android.chrome",
            object : CustomTabsServiceConnection() {
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
                }
            }
        )
    }

    fun open(context: Context, url: String) {
        if (context !is Activity) {
            throw IllegalArgumentException("CustomTabs requires Activity context")
        }

        val intent = CustomTabsIntent.Builder(session)
            .setShowTitle(true)
            .build()

        intent.launchUrl(context, Uri.parse(url))
    }


}