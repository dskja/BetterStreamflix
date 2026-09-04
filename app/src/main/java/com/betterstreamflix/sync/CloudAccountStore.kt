package com.betterstreamflix.sync

import android.content.Context
import com.betterstreamflix.utils.UserProfiles

object CloudAccountStore {
    private const val PREFS = "cloud_account_state"
    private const val ACTIVE_USER = "active_user_id"
    private const val ACTIVE_EMAIL = "active_user_email"
    private const val LEGACY_OWNER = "legacy_owner_id"
    private const val MIGRATED = "migrated_to_profiles_v1"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun activeUserKey(profileId: String) = "${ACTIVE_USER}_$profileId"
    private fun activeEmailKey(profileId: String) = "${ACTIVE_EMAIL}_$profileId"
    private fun legacyOwnerKey(profileId: String) = "${LEGACY_OWNER}_$profileId"

    private fun migrateLegacyIfNeeded(context: Context) {
        val prefs = prefs(context)
        if (prefs.getBoolean(MIGRATED, false)) return
        val editor = prefs.edit()
        val legacyUserId = prefs.getString(ACTIVE_USER, null)
        val legacyEmail = prefs.getString(ACTIVE_EMAIL, null)
        val legacyOwner = prefs.getString(LEGACY_OWNER, null)
        if (legacyUserId != null) {
            editor.putString(activeUserKey(UserProfiles.DEFAULT_ID), legacyUserId)
            editor.remove(ACTIVE_USER)
        }
        if (legacyEmail != null) {
            editor.putString(activeEmailKey(UserProfiles.DEFAULT_ID), legacyEmail)
            editor.remove(ACTIVE_EMAIL)
        }
        if (legacyOwner != null) {
            editor.putString(legacyOwnerKey(UserProfiles.DEFAULT_ID), legacyOwner)
            editor.remove(LEGACY_OWNER)
        }
        editor.putBoolean(MIGRATED, true).apply()
    }

    fun activeUserId(context: Context, profileId: String): String? {
        migrateLegacyIfNeeded(context)
        return prefs(context).getString(activeUserKey(profileId), null)
    }

    fun activeUserEmail(context: Context, profileId: String): String? {
        migrateLegacyIfNeeded(context)
        return prefs(context).getString(activeEmailKey(profileId), null)
    }

    fun setActiveAccount(
        context: Context,
        profileId: String,
        userId: String?,
        email: String?,
    ) {
        migrateLegacyIfNeeded(context)
        prefs(context).edit().apply {
            if (userId == null) {
                remove(activeUserKey(profileId))
            } else {
                putString(activeUserKey(profileId), userId)
            }
            if (email.isNullOrBlank()) {
                remove(activeEmailKey(profileId))
            } else {
                putString(activeEmailKey(profileId), email)
            }
        }.apply()
    }

    fun legacyOwnerId(context: Context, profileId: String): String? {
        migrateLegacyIfNeeded(context)
        return prefs(context).getString(legacyOwnerKey(profileId), null)
    }

    fun claimLegacyData(context: Context, profileId: String, userId: String) {
        migrateLegacyIfNeeded(context)
        prefs(context).edit().putString(legacyOwnerKey(profileId), userId).apply()
    }

    fun clearProfile(context: Context, profileId: String) {
        migrateLegacyIfNeeded(context)
        prefs(context).edit()
            .remove(activeUserKey(profileId))
            .remove(activeEmailKey(profileId))
            .remove(legacyOwnerKey(profileId))
            .apply()
    }

    /**
     * Reverse lookup: which local profile currently owns this Supabase user ID.
     * Used to reject linking the same cloud account to two profiles.
     */
    fun profileIdForUser(context: Context, userId: String): String? {
        migrateLegacyIfNeeded(context)
        val prefix = "${ACTIVE_USER}_"
        prefs(context).all.forEach { (key, value) ->
            if (key.startsWith(prefix) && value == userId) {
                return key.removePrefix(prefix)
            }
        }
        return null
    }

    /** Drop account metadata for profiles that no longer exist. */
    fun pruneOrphans(context: Context, existingProfileIds: Set<String>) {
        migrateLegacyIfNeeded(context)
        val prefs = prefs(context)
        val editor = prefs.edit()
        var changed = false
        prefs.all.keys.forEach { key ->
            val profileId = when {
                key.startsWith("${ACTIVE_USER}_") -> key.removePrefix("${ACTIVE_USER}_")
                key.startsWith("${ACTIVE_EMAIL}_") -> key.removePrefix("${ACTIVE_EMAIL}_")
                key.startsWith("${LEGACY_OWNER}_") -> key.removePrefix("${LEGACY_OWNER}_")
                else -> return@forEach
            }
            if (profileId !in existingProfileIds) {
                editor.remove(key)
                changed = true
            }
        }
        if (changed) editor.apply()
    }
}
