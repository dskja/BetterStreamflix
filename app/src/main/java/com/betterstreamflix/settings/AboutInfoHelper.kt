package com.betterstreamflix.settings

import com.betterstreamflix.BuildConfig
import com.betterstreamflix.utils.AppConfig

/**
 * About page info helper — provides app information for the About settings page.
 */
object AboutInfoHelper {

    /**
     * Get app version string.
     */
    fun getVersionString(): String {
        return "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
    }

    /**
     * Get app information as a list of key-value pairs.
     */
    fun getAppInfo(): List<Pair<String, String>> {
        return listOf(
            "Version" to getVersionString(),
            "Build Type" to if (BuildConfig.DEBUG) "Debug" else "Release",
            "Application ID" to BuildConfig.APPLICATION_ID,
            "SDK Level" to android.os.Build.VERSION.SDK_INT.toString(),
            "Device" to "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
            "Android" to android.os.Build.VERSION.RELEASE,
            "Is TV" to AppConfig.isTv.toString(),
        )
    }

    /**
     * Get links for the about page.
     */
    fun getLinks(): List<Pair<String, String>> {
        return listOf(
            "GitHub" to "https://github.com/${com.betterstreamflix.utils.Constants.GITHUB_REPO_OWNER}/${com.betterstreamflix.utils.Constants.GITHUB_REPO_NAME}",
            "Releases" to "https://github.com/${com.betterstreamflix.utils.Constants.GITHUB_REPO_OWNER}/${com.betterstreamflix.utils.Constants.GITHUB_REPO_NAME}/releases",
            "Report Bug" to "https://github.com/${com.betterstreamflix.utils.Constants.GITHUB_REPO_OWNER}/${com.betterstreamflix.utils.Constants.GITHUB_REPO_NAME}/issues",
        )
    }
}
