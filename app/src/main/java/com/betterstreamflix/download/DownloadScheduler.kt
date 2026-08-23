package com.betterstreamflix.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Download scheduler — schedules downloads based on network conditions,
 * battery level, and user preferences.
 */
object DownloadScheduler {

    /**
     * Determine if downloads should start now.
     */
    fun shouldStartDownloads(context: Context): ScheduleDecision {
        val policy = DownloadPolicyManager.canDownload(context)
        if (policy is DownloadPolicyManager.DownloadPermission.Denied) {
            return ScheduleDecision.Wait(policy.reason)
        }

        val networkType = com.betterstreamflix.network.NetworkCapabilityChecker.getNetworkType(context)
        val isCharging = DownloadPolicyManager.isCharging(context)

        // If on cellular and not charging, defer non-urgent downloads
        if (networkType == com.betterstreamflix.network.NetworkCapabilityChecker.NetworkType.CELLULAR && !isCharging) {
            return ScheduleDecision.Defer("On cellular network and not charging. Downloads will start when conditions improve.")
        }

        return ScheduleDecision.Proceed
    }

    /**
     * Get recommended download time.
     */
    fun getRecommendedDownloadTime(context: Context): RecommendedTime {
        val isCharging = DownloadPolicyManager.isCharging(context)
        val networkType = com.betterstreamflix.network.NetworkCapabilityChecker.getNetworkType(context)
        val batteryLevel = DownloadPolicyManager.getBatteryLevel(context)

        return when {
            isCharging && networkType == com.betterstreamflix.network.NetworkCapabilityChecker.NetworkType.WIFI ->
                RecommendedTime.NOW
            networkType == com.betterstreamflix.network.NetworkCapabilityChecker.NetworkType.WIFI ->
                RecommendedTime.NOW
            batteryLevel < 20 -> RecommendedTime.WHEN_CHARGING
            networkType == com.betterstreamflix.network.NetworkCapabilityChecker.NetworkType.CELLULAR ->
                RecommendedTime.WHEN_WIFI
            else -> RecommendedTime.NOW
        }
    }

    /**
     * Get the maximum number of concurrent downloads based on conditions.
     */
    fun getRecommendedConcurrentDownloads(context: Context): Int {
        val networkType = com.betterstreamflix.network.NetworkCapabilityChecker.getNetworkType(context)
        val isCharging = DownloadPolicyManager.isCharging(context)

        return when {
            networkType == com.betterstreamflix.network.NetworkCapabilityChecker.NetworkType.WIFI && isCharging -> 3
            networkType == com.betterstreamflix.network.NetworkCapabilityChecker.NetworkType.WIFI -> 2
            networkType == com.betterstreamflix.network.NetworkCapabilityChecker.NetworkType.CELLULAR -> 1
            else -> 1
        }
    }

    sealed class ScheduleDecision {
        data object Proceed : ScheduleDecision()
        data class Wait(val reason: String) : ScheduleDecision()
        data class Defer(val message: String) : ScheduleDecision()
    }

    enum class RecommendedTime { NOW, WHEN_WIFI, WHEN_CHARGING }
}
