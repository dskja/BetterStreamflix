package com.betterstreamflix.sync

import android.content.Context
import android.util.Log
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
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

    private var activeUserId: String? = null
    private var channel: RealtimeChannel? = null
    private var collectorJob: Job? = null

    suspend fun start(context: Context, userId: String) {
        if (!SupabaseProvider.isConfigured) return
        val appContext = context.applicationContext

        lifecycleMutex.withLock {
            if (activeUserId == userId &&
                channel?.status?.value == RealtimeChannel.Status.SUBSCRIBED
            ) {
                return@withLock
            }

            stopLocked()

            val newChannel = SupabaseProvider.client.realtime.channel(
                "user-media-state-$userId",
            )
            val changes = newChannel.postgresChangeFlow<PostgresAction>(schema = "public") {
                table = TABLE
                filter("user_id", FilterOperator.EQ, userId)
            }

            val newCollector = changes
                .onEach { action ->
                    when (action) {
                        is PostgresAction.Delete -> {
                            action.oldRecord?.decodeRecordOrNull<RemoteMediaState>()?.let { state ->
                                CloudSyncManager.deleteRealtimeState(appContext, state)
                            }
                        }
                        is PostgresAction.Insert, is PostgresAction.Update -> {
                            action.decodeRecordOrNull<RemoteMediaState>()?.let { state ->
                                CloudSyncManager.applyRealtimeState(appContext, state)
                            }
                        }
                        else -> {}
                    }
                }
                .catch { error ->
                    Log.w(TAG, "Realtime media synchronization stopped, will retry", error)
                    CloudSyncScheduler.enqueue(appContext)
                    val reconnectUserId = userId
                    var backoffMs = 2000L
                    repeat(5) { attempt ->
                        delay(backoffMs)
                        if (activeUserId != reconnectUserId) return@repeat
                        Log.i(TAG, "Attempting realtime reconnect (attempt ${attempt + 1}/5)")
                        runCatching { start(appContext, reconnectUserId) }
                            .onFailure {
                                Log.w(TAG, "Reconnect attempt ${attempt + 1} failed", it)
                            }
                            .onSuccess {
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
                activeUserId = userId
                channel = newChannel
                collectorJob = newCollector
                Log.i(TAG, "Listening for media changes")
            } catch (error: Throwable) {
                newCollector.cancel()
                runCatching {
                    SupabaseProvider.client.realtime.removeChannel(newChannel)
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

    private suspend fun stopLocked() {
        collectorJob?.cancelAndJoin()
        collectorJob = null
        channel?.let { existingChannel ->
            runCatching {
                SupabaseProvider.client.realtime.removeChannel(existingChannel)
            }.onFailure { error ->
                Log.w(TAG, "Could not stop realtime media synchronization", error)
            }
        }
        channel = null
        activeUserId = null
    }
}
