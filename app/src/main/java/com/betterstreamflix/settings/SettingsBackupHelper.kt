package com.betterstreamflix.settings

import com.betterstreamflix.utils.UserPreferences

/**
 * Settings export/import helper — allows users to backup and restore settings.
 */
object SettingsBackupHelper {

    /**
     * Export all settings to a JSON string.
     */
    fun exportSettings(): String {
        val obj = org.json.JSONObject()
        obj.put("providerUrl", UserPreferences.providerUrl)
        obj.put("selectedProvider", UserPreferences.currentProvider?.name ?: "")
        obj.put("appLayout", UserPreferences.appLayout)
        obj.put("qualityHeight", UserPreferences.qualityHeight ?: 0)
        obj.put("autoplay", UserPreferences.autoplay)
        obj.put("autoplayBuffer", UserPreferences.autoplayBuffer)
        obj.put("appLanguage", UserPreferences.appLanguage)
        obj.put("subtitleLanguage", UserPreferences.subtitleLanguage)
        obj.put("dohProviderUrl", UserPreferences.dohProviderUrl ?: "")
        obj.put("exportTime", System.currentTimeMillis())
        obj.put("version", com.betterstreamflix.BuildConfig.VERSION_NAME)
        return obj.toString(2)
    }

    /**
     * Import settings from a JSON string.
     */
    fun importSettings(json: String): Boolean {
        return try {
            val obj = org.json.JSONObject(json)
            UserPreferences.providerUrl = obj.optString("providerUrl", "")
            UserPreferences.appLayout = obj.optString("appLayout", "")
            UserPreferences.qualityHeight = obj.optInt("qualityHeight", 0).takeIf { it > 0 }
            UserPreferences.autoplay = obj.optBoolean("autoplay", true)
            UserPreferences.autoplayBuffer = obj.optLong("autoplayBuffer", 10L)
            UserPreferences.appLanguage = obj.optString("appLanguage", "en")
            UserPreferences.subtitleLanguage = obj.optString("subtitleLanguage", "en")
            UserPreferences.dohProviderUrl = obj.optString("dohProviderUrl", "")
            true
        } catch (e: Exception) {
            false
        }
    }
}
