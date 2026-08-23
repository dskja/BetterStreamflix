package com.betterstreamflix.utils

import android.app.PictureInPictureParams
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.util.Rational
import androidx.appcompat.app.AppCompatActivity

/**
 * Picture-in-Picture mode helper for video playback.
 */
class PipModeHelper(private val activity: AppCompatActivity) {

    /**
     * Check if PiP is supported on this device.
     */
    fun isSupported(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            activity.packageManager.hasSystemFeature("android.software.picture_in_picture")
    }

    /**
     * Enter PiP mode with the given aspect ratio.
     */
    fun enterPipMode(width: Int = 16, height: Int = 9): Boolean {
        if (!isSupported()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(width, height))
                .build()
            return try {
                activity.enterPictureInPictureMode(params)
                true
            } catch (e: Exception) {
                false
            }
        }
        return false
    }

    /**
     * Check if currently in PiP mode.
     */
    fun isInPipMode(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity.isInPictureInPictureMode
    }

    /**
     * Check if the activity should enter PiP when going to background.
     */
    fun shouldEnterPipOnBackground(): Boolean {
        return FeatureFlags.isEnabled(FeatureFlags.PIP_MODE) && isSupported()
    }
}
