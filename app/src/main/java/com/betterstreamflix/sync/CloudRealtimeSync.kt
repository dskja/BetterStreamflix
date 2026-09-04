package com.betterstreamflix.sync

import android.content.Context
import android.util.Log
import com.betterstreamflix.utils.UserProfiles
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeOldRecordOrNull
import io.github.jan.supabase.realtime.decodeRecordOrNull
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object CloudRealtimeSync {
    private const val TAG = "CloudRealtime"
    private const val TABLE = "user_media_state"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lifecycleMutex = Mutex()

    private var activeProfileId: String? = null
    private var activeUserId: String? = null
    private var activeClient: SupabaseClient? = null
    private var channel: RealtimeChannel? = null
    private var collectorJob: Job? = null

    suspend fun start(context: Context, profileId: String, userId: String) {
        if (!SupabaseProvider.isConfigured) return
        val appContext = context.applicationContext
        val client = SupabaseProvider.clientFor(appContext, profileId)

        lifecycleMutex.withLock {
            if (activeProfileId == profileId &&
                activeUserId == userId &&
                activeClient === client &&
                channel?.status?.value == RealtimeChannel.Status.SUBSCRIBED
            ) {
                return@withLock
            }

            stopLocked()

            val newChannel = client.realtime.channel(
                "user-media-state-$profileId-$userId",
            )
            val changes = newChannel.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = TABLE
                filter("user_id", FilterOperator.EQ, userId)
            }

            val newCollector = changes
                .onEach { action ->
                    if (UserProfiles.active().id != profileId) return@onEach
                    when (action) {
                        is PostgresAction.Delete -> {
                            action.decodeOldRecordOrNull<RemoteMediaState>()?.let { state ->
                                CloudSyncManager.deleteRealtimeState(appContext, profileId, state)
                            }
                        }
                        is PostgresAction.Insert, is PostgresAction.Update -> {
                            action.decodeRecordOrNull<RemoteMediaState>()?.let { state ->
                                CloudSyncManager.applyRealtimeState(appContext, profileId, state)
                            }
                        }
                        else -> {}
                    }
                }
                .catch { error ->
                    Log.w(TAG, "Realtime media synchronization stopped, will retry", error)
                    CloudSyncScheduler.enqueue(appContext, profileId, userId)
                    val reconnectProfileId = profileId
                    val reconnectUserId = userId
                    var backoffMs = 2000L
                    repeat(5) { attempt ->
                        delay(backoffMs)
                        if (activeProfileId != reconnectProfileId ||
                            activeUserId != reconnectUserId
                        ) {
                            return@repeat
                        }
                        Log.i(TAG, "Attempting realtime reconnect (attempt ${attempt + 1}/5)")
                        runCatching {
                            start(appContext, reconnectProfileId, reconnectUserId)
                        }.onFailure {
                            Log.w(TAG, "Reconnect attempt ${attempt + 1} failed", it)
                        }.onSuccess {
                            if (channel?.status?.value == RealtimeChannel.Status.SUBSCRIBED) {
                                Log.i(TAG, "Realtime reconnected successfully")
                                return@repeat
                            }
                        }
                        backoffMs *= 2
                    }
                }
                .launchIn(scope)

            try {
                newChannel.subscribe(blockUntilSubscribed = true)
                activeProfileId = profileId
                activeUserId = userId
                activeClient = client
                channel = newChannel
                collectorJob = newCollector
                Log.i(TAG, "Listening for media changes profile=$profileId user=$userId")
            } catch (error: Throwable) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                newCollector.cancel()
                runCatching {
                    client.realtime.removeChannel(newChannel)
                }
                Log.w(TAG, "Could not start realtime media synchronization", error)
            }
        }
    }

    suspend fun stop() {
        lifecycleMutex.withLock {
            stopLocked()
        }
    }

    suspend fun stopIfProfile(profileId: String) {
        lifecycleMutex.withLock {
            if (activeProfileId == profileId) {
                stopLocked()
            }
        }
    }

    private suspend fun stopLocked() {
        collectorJob?.cancelAndJoin()
        collectorJob = null
        val existingChannel = channel
        val client = activeClient
        if (existingChannel != null && client != null) {
            runCatching {
                client.realtime.removeChannel(existingChannel)
            }.onFailure { error ->
                Log.w(TAG, "Could not stop realtime media synchronization", error)
            }
        }
        channel = null
        activeClient = null
        activeUserId = null
        activeProfileId = null
    }
}
