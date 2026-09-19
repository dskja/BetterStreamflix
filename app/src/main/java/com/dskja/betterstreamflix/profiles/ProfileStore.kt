package com.dskja.betterstreamflix.profiles

import android.content.Context
import androidx.core.content.edit
import com.dskja.betterstreamflix.R
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object ProfileStore {

    const val DEFAULT_PROFILE_ID = "default"
    private const val DEFAULT_AVATAR_KEY = "crimson"

    private const val PREFS = "beta_profiles"
    private const val KEY_PROFILES = "profiles_json"
    private const val KEY_ACTIVE_PROFILE_ID = "active_profile_id"

    private val gson = Gson()
    private val listType = object : TypeToken<List<UserProfile>>() {}.type

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun loadAll(context: Context): List<UserProfile> {
        val raw = prefs(context).getString(KEY_PROFILES, null) ?: return emptyList()
        return runCatching {
            gson.fromJson<List<UserProfile>>(raw, listType)
        }.getOrDefault(emptyList())
    }

    fun saveAll(context: Context, profiles: List<UserProfile>) {
        prefs(context).edit {
            putString(KEY_PROFILES, gson.toJson(profiles))
        }
    }

    fun getActiveId(context: Context): String? =
        prefs(context).getString(KEY_ACTIVE_PROFILE_ID, null)

    fun setActiveId(context: Context, id: String) {
        prefs(context).edit {
            putString(KEY_ACTIVE_PROFILE_ID, id)
        }
    }

    fun ensureDefaultExists(context: Context): UserProfile {
        val appContext = context.applicationContext
        val profiles = loadAll(appContext).toMutableList()
        val existing = profiles.find { it.id == DEFAULT_PROFILE_ID }
        if (existing != null) {
            if (getActiveId(appContext) == null) {
                setActiveId(appContext, DEFAULT_PROFILE_ID)
            }
            return existing
        }

        val now = System.currentTimeMillis()
        val displayName = runCatching {
            appContext.getString(R.string.profile_default)
        }.getOrDefault("Default")

        val defaultProfile = UserProfile(
            id = DEFAULT_PROFILE_ID,
            displayName = displayName,
            avatarKey = DEFAULT_AVATAR_KEY,
            createdAtMillis = now,
            updatedAtMillis = now,
        )
        profiles.add(0, defaultProfile)
        saveAll(appContext, profiles)
        if (getActiveId(appContext) == null) {
            setActiveId(appContext, DEFAULT_PROFILE_ID)
        }
        return defaultProfile
    }
}
