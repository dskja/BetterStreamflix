package com.betterstreamflix.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Secure preferences for storing sensitive data (API keys, tokens, etc.)
 * Uses AndroidX EncryptedSharedPreferences for encryption at rest.
 */
object SecurePreferences {

    private const val FILE_NAME = "secure_prefs"
    private const val KEY_SUPABASE_URL = "supabase_url"
    private const val KEY_SUPABASE_KEY = "supabase_key"
    private const val KEY_API_TOKENS = "api_tokens"

    private var prefs: SharedPreferences? = null

    /**
     * Initialize secure preferences. Call from Application.onCreate().
     */
    fun init(context: Context) {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            prefs = EncryptedSharedPreferences.create(
                context,
                FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            // Fallback to regular preferences if encryption fails
            prefs = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
        }
    }

    /**
     * Store a secure string value.
     */
    fun putString(key: String, value: String) {
        prefs?.edit()?.putString(key, value)?.apply()
    }

    /**
     * Get a secure string value.
     */
    fun getString(key: String, default: String? = null): String? {
        return prefs?.getString(key, default)
    }

    /**
     * Remove a secure value.
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
