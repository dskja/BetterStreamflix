package com.betterstreamflix.sync

import android.content.Context
import com.betterstreamflix.utils.UserProfiles
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

object CloudMutationStore {
    private const val PREFS = "cloud_sync_queue"
    private const val QUEUE = "pending_media_states"
    private const val MIGRATED = "migrated_to_profiles_v1"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(RemoteMediaState.serializer())

    private fun queueKey(profileId: String) = "${QUEUE}_$profileId"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun migrateLegacyIfNeeded(context: Context) {
        val prefs = prefs(context)
        if (prefs.getBoolean(MIGRATED, false)) return
        val editor = prefs.edit()
        val legacy = prefs.getString(QUEUE, null)
        if (legacy != null) {
            editor.putString(queueKey(UserProfiles.DEFAULT_ID), legacy)
            editor.remove(QUEUE)
        }
        editor.putBoolean(MIGRATED, true).commit()
    }

    @Synchronized
    fun enqueue(context: Context, profileId: String, state: RemoteMediaState) {
        migrateLegacyIfNeeded(context)
        val current = read(context, profileId).associateByTo(linkedMapOf()) { it.queueKey }
        current[state.queueKey] = state
        write(context, profileId, current.values.toList())
    }

    @Synchronized
    fun pendingForUser(
        context: Context,
        profileId: String,
        userId: String,
    ): List<RemoteMediaState> {
        migrateLegacyIfNeeded(context)
        return read(context, profileId).filter { it.userId == userId }
    }

    @Synchronized
    fun acknowledge(
        context: Context,
        profileId: String,
        uploaded: List<RemoteMediaState>,
    ) {
        migrateLegacyIfNeeded(context)
        if (uploaded.isEmpty()) return
        val uploadedVersions = uploaded.associate { it.queueKey to it.clientUpdatedAtMillis }
        val remaining = read(context, profileId).filter { state ->
            val uploadedVersion = uploadedVersions[state.queueKey]
            uploadedVersion == null || state.clientUpdatedAtMillis > uploadedVersion
        }
        write(context, profileId, remaining)
    }

    @Synchronized
    fun clearProfile(context: Context, profileId: String) {
        migrateLegacyIfNeeded(context)
        write(context, profileId, emptyList())
    }

    @Synchronized
    fun clearForUser(context: Context, profileId: String, userId: String?) {
        migrateLegacyIfNeeded(context)
        if (userId == null) {
            write(context, profileId, emptyList())
            return
        }
        val remaining = read(context, profileId).filter { it.userId != userId }
        write(context, profileId, remaining)
    }

    private fun read(context: Context, profileId: String): List<RemoteMediaState> {
        val raw = prefs(context).getString(queueKey(profileId), null) ?: return emptyList()
        return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())
    }

    private fun write(context: Context, profileId: String, states: List<RemoteMediaState>) {
        prefs(context)
            .edit()
            .putString(queueKey(profileId), json.encodeToString(serializer, states))
            .commit()
    }
}
