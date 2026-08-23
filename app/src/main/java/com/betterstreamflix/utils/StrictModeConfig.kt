package com.betterstreamflix.utils

import android.content.Context
import android.os.StrictMode
import com.betterstreamflix.BuildConfig

/**
 * StrictMode configuration for detecting accidental main-thread I/O
 * and other violations during development.
 */
object StrictModeConfig {

    /**
     * Enable StrictMode in debug builds.
     */
    fun enable() {
        if (!BuildConfig.DEBUG) return

        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .detectCustomSlowCalls()
                .penaltyLog()
                .penaltyFlashScreen()
                .build()
        )

        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectActivityLeaks()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .detectLeakedRegistrationObjects()
                .detectFileUriExposure()
                .penaltyLog()
                .build()
        )
    }
}
