package com.betterstreamflix.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * DataStore-based preference manager.
 * Designed as a modern replacement for SharedPreferences (UserPreferences).
 * Uses async I/O and avoids blocking the main thread.
 *
 * Migration: Use migrateFromSharedPreferences() once to move existing data.
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "betterstreamflix_settings")

class DataStorePreferences(private val context: Context) {

    // === Keys ===
    companion object {
        val PROVIDER_URL = stringPreferencesKey("provider_url")
        val SELECTED_PROVIDER = stringPreferencesKey("selected_provider")
        val APP_LAYOUT = stringPreferencesKey("app_layout")
        val QUALITY_HEIGHT = intPreferencesKey("quality_height")
        val AUTOPLAY = booleanPreferencesKey("autoplay")
        val AUTOPLAY_BUFFER = intPreferencesKey("autoplay_buffer")
        val DOH_PROVIDER_URL = stringPreferencesKey("doh_provider_url")
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val SUBTITLE_LANGUAGE = stringPreferencesKey("subtitle_language")
    }

    // === Read ===
    fun providerUrl(): Flow<String> = context.dataStore.data.map { it[PROVIDER_URL] ?: "" }
    fun selectedProvider(): Flow<String?> = context.dataStore.data.map { it[SELECTED_PROVIDER] }
    fun appLayout(): Flow<String> = context.dataStore.data.map { it[APP_LAYOUT] ?: "" }
    fun qualityHeight(): Flow<Int?> = context.dataStore.data.map { it[QUALITY_HEIGHT] }
    fun autoplay(): Flow<Boolean> = context.dataStore.data.map { it[AUTOPLAY] ?: true }
    fun autoplayBuffer(): Flow<Int> = context.dataStore.data.map { it[AUTOPLAY_BUFFER] ?: 10 }
    fun appLanguage(): Flow<String> = context.dataStore.data.map { it[APP_LANGUAGE] ?: "en" }
    fun subtitleLanguage(): Flow<String> = context.dataStore.data.map { it[SUBTITLE_LANGUAGE] ?: "en" }

    // === Write ===
    suspend fun setProviderUrl(value: String) { context.dataStore.edit { it[PROVIDER_URL] = value } }
    suspend fun setSelectedProvider(value: String?) {
        context.dataStore.edit { prefs ->
            if (value != null) prefs[SELECTED_PROVIDER] = value
            else prefs.remove(SELECTED_PROVIDER)
        }
    }
    suspend fun setAppLayout(value: String) { context.dataStore.edit { it[APP_LAYOUT] = value } }
    suspend fun setQualityHeight(value: Int) { context.dataStore.edit { it[QUALITY_HEIGHT] = value } }
    suspend fun setAutoplay(value: Boolean) { context.dataStore.edit { it[AUTOPLAY] = value } }
    suspend fun setAutoplayBuffer(value: Int) { context.dataStore.edit { it[AUTOPLAY_BUFFER] = value } }
    suspend fun setAppLanguage(value: String) { context.dataStore.edit { it[APP_LANGUAGE] = value } }
    suspend fun setSubtitleLanguage(value: String) { context.dataStore.edit { it[SUBTITLE_LANGUAGE] = value } }

    // === Clear ===
    suspend fun clearAll() { context.dataStore.edit { it.clear() } }
}
