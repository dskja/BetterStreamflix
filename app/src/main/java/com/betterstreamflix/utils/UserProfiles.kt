package com.betterstreamflix.utils

import com.betterstreamflix.StreamFlixApp
import com.betterstreamflix.database.AppDatabase
import com.betterstreamflix.sync.CloudAccountStore
import com.betterstreamflix.sync.CloudSyncManager
import com.betterstreamflix.ui.UserDataNotifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Lightweight local profiles (household / kids) stored in SharedPreferences.
 * Cloud Auth sessions and sync queues are scoped by [Profile.id].
 */
object UserProfiles {

    const val DEFAULT_ID = "default"

    data class Profile(
        val id: String,
        val name: String,
        val parentalMaxAge: Int? = null,
        val isKids: Boolean = false,
    )

    private const val KEY_PROFILES = "LOCAL_PROFILES_JSON"
    private const val KEY_ACTIVE = "LOCAL_PROFILE_ACTIVE_ID"

    private val _activeProfileChanges = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /** Emits the new active profile id whenever [setActive] switches profiles. */
    val activeProfileChanges: SharedFlow<String> = _activeProfileChanges.asSharedFlow()

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
        val previousId = runCatching { active().id }.getOrNull()
        if (previousId == profileId) {
            val profile = list().find { it.id == profileId } ?: return
            UserPreferences.parentalControlMaxAge = profile.parentalMaxAge
            return
        }
        UserPreferences.prefs.edit().putString(KEY_ACTIVE, profileId).apply()
        val profile = list().find { it.id == profileId } ?: return
        UserPreferences.parentalControlMaxAge = profile.parentalMaxAge
        AppDatabase.resetInstance()
        UserDataNotifier.notifyChanged()
        _activeProfileChanges.tryEmit(profileId)
        runCatching {
            val app = StreamFlixApp.instance
            app.applicationScope.launch(Dispatchers.IO) {
                CloudSyncManager.onProfileChanged(app, profileId)
            }
        }
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
        if (profileId == DEFAULT_ID && list().size <= 1) return
        val remaining = list().filterNot { it.id == profileId }.ifEmpty { listOf(defaultProfile()) }
        val wasActive = active().id == profileId
        persist(remaining)
        runCatching {
            val app = StreamFlixApp.instance
            app.applicationScope.launch(Dispatchers.IO) {
                CloudSyncManager.onProfileDeleted(app, profileId)
            }
        }
        if (wasActive) {
            setActive(remaining.first().id)
        }
    }

    /** Drop cloud metadata for profiles that no longer exist. */
    fun pruneOrphanCloudState(context: android.content.Context) {
        CloudAccountStore.pruneOrphans(context, list().map { it.id }.toSet())
    }

    private fun defaultProfile() = Profile(id = DEFAULT_ID, name = "Default")

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
