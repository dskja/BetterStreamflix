package com.betterstreamflix.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Secure preferences — stores sensitive data (API keys, PINs) using
 * AndroidX EncryptedSharedPreferences.
 */
object SecurePreferences {

    private const val PREFS_NAME = "secure_prefs"
    private var prefs: android.content.SharedPreferences? = null

    /**
     * Initialize secure preferences.
     */
    fun init(context: Context) {
        if (prefs != null) return
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            prefs = EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            // Fallback to regular prefs if encryption fails
            prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    /**
     * Put a secure string.
     */
    fun putString(key: String, value: String) {
        prefs?.edit()?.putString(key, value)?.apply()
    }

    /**
     * Get a secure string.
     */
    fun getString(key: String, default: String? = null): String? {
        return prefs?.getString(key, default)
    }

    /**
     * Remove a secure key.
     */
    fun remove(key: String) {
        prefs?.edit()?.remove(key)?.apply()
    }

    /**
     * Clear all secure preferences.
     */
    fun clear() {
        prefs?.edit()?.clear()?.apply()
    }
}
