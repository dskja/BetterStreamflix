package com.betterstreamflix.utils

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Runtime permissions helper — simplifies permission checking and requesting.
 */
object PermissionHelper {

    /**
     * Check if all permissions are granted.
     */
    fun hasPermissions(context: Context, permissions: Array<String>): Boolean {
        return permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Get list of permissions that are not yet granted.
     */
    fun getMissingPermissions(context: Context, permissions: Array<String>): List<String> {
        return permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Check if should show rationale for a permission.
     */
    fun shouldShowRationale(activity: android.app.Activity, permission: String): Boolean {
        return androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    }
}

/**
 * Common permission groups used in the app.
 */
object Permissions {
    const val INTERNET = android.Manifest.permission.INTERNET
    const val ACCESS_NETWORK_STATE = android.Manifest.permission.ACCESS_NETWORK_STATE
    const val WRITE_EXTERNAL_STORAGE = android.Manifest.permission.WRITE_EXTERNAL_STORAGE
    const val READ_EXTERNAL_STORAGE = android.Manifest.permission.READ_EXTERNAL_STORAGE
    const val POST_NOTIFICATIONS = android.Manifest.permission.POST_NOTIFICATIONS
    const val FOREGROUND_SERVICE = android.Manifest.permission.FOREGROUND_SERVICE
    const val WAKE_LOCK = android.Manifest.permission.WAKE_LOCK
    const val RECEIVE_BOOT_COMPLETED = android.Manifest.permission.RECEIVE_BOOT_COMPLETED
    const val REQUEST_INSTALL_PACKAGES = android.Manifest.permission.REQUEST_INSTALL_PACKAGES
    const val CAMERA = android.Manifest.permission.CAMERA
}
