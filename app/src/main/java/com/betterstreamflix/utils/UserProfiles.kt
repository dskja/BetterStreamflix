package com.betterstreamflix.utils

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Lightweight local profiles (household / kids) stored in SharedPreferences.
 * Cloud mapping via profile_id can be layered on later.
 */
object UserProfiles {

    data class Profile(
        val id: String,
        val name: String,
        val parentalMaxAge: Int? = null,
        val isKids: Boolean = false,
    )

    private const val KEY_PROFILES = "LOCAL_PROFILES_JSON"
    private const val KEY_ACTIVE = "LOCAL_PROFILE_ACTIVE_ID"

    fun list(): List<Profile> {
        if (!UserPreferences.isReady()) return emptyList()
        val raw = UserPreferences.prefs.getString(KEY_PROFILES, null) ?: return listOf(defaultProfile())
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        Profile(
                            id = o.getString("id"),
                            name = o.getString("name"),
                            parentalMaxAge = o.optInt("parentalMaxAge", -1).takeIf { it >= 0 },
                            isKids = o.optBoolean("isKids", false),
                        )
                    )
                }
            }.ifEmpty { listOf(defaultProfile()) }
        }.getOrElse { listOf(defaultProfile()) }
    }

    fun active(): Profile {
        val profiles = list()
        val activeId = if (UserPreferences.isReady()) {
            UserPreferences.prefs.getString(KEY_ACTIVE, null)
        } else null
        return profiles.find { it.id == activeId } ?: profiles.first()
    }

    fun setActive(profileId: String) {
        if (!UserPreferences.isReady()) return
        UserPreferences.prefs.edit().putString(KEY_ACTIVE, profileId).apply()
        val profile = list().find { it.id == profileId } ?: return
        UserPreferences.parentalControlMaxAge = profile.parentalMaxAge
    }

    fun upsert(profile: Profile) {
        val updated = list().filterNot { it.id == profile.id } + profile
        persist(updated)
    }

    fun create(name: String, parentalMaxAge: Int? = null, isKids: Boolean = false): Profile {
        val profile = Profile(
            id = UUID.randomUUID().toString(),
            name = name.trim().ifBlank { "Profile" },
            parentalMaxAge = parentalMaxAge,
            isKids = isKids,
        )
        upsert(profile)
        return profile
    }

    fun delete(profileId: String) {
        val remaining = list().filterNot { it.id == profileId }.ifEmpty { listOf(defaultProfile()) }
        persist(remaining)
        if (active().id == profileId) {
            setActive(remaining.first().id)
        }
    }

    private fun defaultProfile() = Profile(id = "default", name = "Default")

    private fun persist(profiles: List<Profile>) {
        if (!UserPreferences.isReady()) return
        val arr = JSONArray()
        profiles.forEach { p ->
            arr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("parentalMaxAge", p.parentalMaxAge ?: -1)
                    .put("isKids", p.isKids),
            )
        }
        UserPreferences.prefs.edit().putString(KEY_PROFILES, arr.toString()).apply()
    }
}
