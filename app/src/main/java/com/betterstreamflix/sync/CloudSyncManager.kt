package com.betterstreamflix.sync

import android.content.Context
import android.util.Log
import com.betterstreamflix.database.AppDatabase
import com.betterstreamflix.models.Episode
import com.betterstreamflix.models.Movie
import com.betterstreamflix.models.Season
import com.betterstreamflix.models.TvShow
import com.betterstreamflix.models.WatchItem
import com.betterstreamflix.providers.Provider
import com.betterstreamflix.providers.TmdbProvider
import com.betterstreamflix.ui.UserDataNotifier
import com.betterstreamflix.utils.FileLogger
import com.betterstreamflix.utils.UserDataCache
import com.betterstreamflix.utils.UserProfiles
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object CloudSyncManager {
    private const val TAG = "CloudSync"
    private const val TABLE = "user_media_state"
    private const val FETCH_PAGE_SIZE = 500L
    private val accountSyncMutex = Mutex()

    @Volatile
    var isApplyingRemote: Boolean = false
        private set

    fun currentUserId(profileId: String = UserProfiles.active().id): String? =
        if (!SupabaseProvider.isConfigured) {
            null
        } else {
            SupabaseProvider.clientOrNull(profileId)?.auth?.currentSessionOrNull()?.user?.id
        }

    fun currentUserEmail(profileId: String = UserProfiles.active().id): String? =
        if (!SupabaseProvider.isConfigured) {
            null
        } else {
            SupabaseProvider.clientOrNull(profileId)?.auth?.currentSessionOrNull()?.user?.email
        }

    suspend fun initialize(context: Context) {
        FileLogger.i("CloudSyncManager", "initialize() called")
        val appContext = context.applicationContext
        val profileId = UserProfiles.active().id
        UserProfiles.pruneOrphanCloudState(appContext)
        if (!SupabaseProvider.isConfigured) {
            FileLogger.i("CloudSyncManager", "initialize: Supabase not configured, skipping")
            return
        }
        FileLogger.i("CloudSyncManager", "initialize: calling SupabaseProvider.initialize($profileId)")
        val client = SupabaseProvider.clientFor(appContext, profileId)

        FileLogger.i("CloudSyncManager", "initialize: awaiting auth initialization")
        client.auth.awaitInitialization()
        val userId = currentUserId(profileId)
        FileLogger.i("CloudSyncManager", "initialize: userId=$userId profile=$profileId")
        if (userId == null) {
            FileLogger.i("CloudSyncManager", "initialize: no user session, stopping sync")
            CloudRealtimeSync.stop()
            // Preserve ownership marker; only clear the active session user id.
            CloudAccountStore.setActiveAccount(
                appContext,
                profileId,
                userId = null,
                email = null,
            )
            return
        }
        FileLogger.i("CloudSyncManager", "initialize: activating account for userId=$userId")
        activateAccount(appContext, profileId, userId, client)
        CloudRealtimeSync.start(appContext, profileId, userId)
        CloudSyncScheduler.schedulePeriodic(appContext, profileId, userId)
        FileLogger.i("CloudSyncManager", "initialize: ✓ complete")
    }

    suspend fun onProfileChanged(context: Context, profileId: String) {
        val appContext = context.applicationContext
        CloudRealtimeSync.stop()
        CloudSyncScheduler.cancelPeriodic(appContext)
        AppDatabase.resetInstance()
        if (!SupabaseProvider.isConfigured) return
        runCatching {
            val client = SupabaseProvider.clientFor(appContext, profileId)
            client.auth.awaitInitialization()
            val userId = client.auth.currentSessionOrNull()?.user?.id
            if (userId == null) {
                CloudAccountStore.setActiveAccount(appContext, profileId, null, null)
                return
            }
            if (UserProfiles.active().id != profileId) return
            activateAccount(appContext, profileId, userId, client)
            if (UserProfiles.active().id != profileId) return
            CloudRealtimeSync.start(appContext, profileId, userId)
            CloudSyncScheduler.schedulePeriodic(appContext, profileId, userId)
        }.onFailure {
            Log.w(TAG, "onProfileChanged failed for profile=$profileId", it)
        }
    }

    suspend fun onProfileDeleted(context: Context, profileId: String) {
        val appContext = context.applicationContext
        CloudRealtimeSync.stopIfProfile(profileId)
        CloudSyncScheduler.cancelForProfile(appContext, profileId)
        CloudMutationStore.clearProfile(appContext, profileId)
        CloudAccountStore.clearProfile(appContext, profileId)
        SupabaseProvider.removeProfile(profileId)
        UserDataCache.clearProfile(appContext, profileId)
        AppDatabase.deleteProfileDatabases(appContext, profileId)
    }

    suspend fun signIn(
        context: Context,
        email: String,
        password: String,
        onProgress: (CloudSyncProgress) -> Unit = {},
    ) {
        requireConfigured()
        val appContext = context.applicationContext
        val profileId = UserProfiles.active().id
        val client = SupabaseProvider.clientFor(appContext, profileId)
        onProgress(CloudSyncProgress(CloudSyncProgress.Stage.AUTHENTICATING))
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        client.auth.awaitInitialization()
        val userId = client.auth.currentSessionOrNull()?.user?.id
            ?: error("Sign in did not create a session")
        ensureAccountNotLinkedElsewhere(appContext, profileId, userId)
        if (UserProfiles.active().id != profileId) {
            error("Active profile changed during sign-in")
        }
        activateAccount(
            context = appContext,
            profileId = profileId,
            userId = userId,
            client = client,
            onProgress = onProgress,
            mergeLocalOnLogin = true,
        )
        CloudRealtimeSync.start(appContext, profileId, userId)
        CloudSyncScheduler.schedulePeriodic(appContext, profileId, userId)
    }

    suspend fun signUp(
        context: Context,
        email: String,
        password: String,
        onProgress: (CloudSyncProgress) -> Unit = {},
    ): Boolean {
        requireConfigured()
        val appContext = context.applicationContext
        val profileId = UserProfiles.active().id
        val client = SupabaseProvider.clientFor(appContext, profileId)
        onProgress(CloudSyncProgress(CloudSyncProgress.Stage.AUTHENTICATING))
        client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
        client.auth.awaitInitialization()
        val userId = client.auth.currentSessionOrNull()?.user?.id ?: return false
        ensureAccountNotLinkedElsewhere(appContext, profileId, userId)
        if (UserProfiles.active().id != profileId) {
            error("Active profile changed during sign-up")
        }
        activateAccount(
            context = appContext,
            profileId = profileId,
            userId = userId,
            client = client,
            onProgress = onProgress,
            mergeLocalOnLogin = true,
        )
        CloudRealtimeSync.start(appContext, profileId, userId)
        CloudSyncScheduler.schedulePeriodic(appContext, profileId, userId)
        return true
    }

    suspend fun completeSignInAfterConflict(
        context: Context,
        email: String,
        password: String,
        keepLocal: Boolean,
        onProgress: (CloudSyncProgress) -> Unit = {},
    ) {
        requireConfigured()
        val appContext = context.applicationContext
        val profileId = UserProfiles.active().id
        val client = SupabaseProvider.clientFor(appContext, profileId)
        onProgress(CloudSyncProgress(CloudSyncProgress.Stage.AUTHENTICATING))
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        client.auth.awaitInitialization()
        val userId = client.auth.currentSessionOrNull()?.user?.id
            ?: error("Sign in did not create a session")
        ensureAccountNotLinkedElsewhere(appContext, profileId, userId)
        if (UserProfiles.active().id != profileId) {
            error("Active profile changed during sign-in")
        }
        accountSyncMutex.withLock {
            isApplyingRemote = true
            try {
                if (keepLocal) {
                    forceMergeLocalAccount(appContext, profileId, userId, client, onProgress)
                } else {
                    replaceLocalWithCloud(appContext, profileId, userId, client, onProgress)
                }
                onProgress(CloudSyncProgress(CloudSyncProgress.Stage.FINALIZING))
                persistActiveAccount(appContext, profileId, userId, client)
                markLastSynced(appContext, profileId)
            } finally {
                isApplyingRemote = false
            }
        }
        CloudRealtimeSync.start(appContext, profileId, userId)
        CloudSyncScheduler.schedulePeriodic(appContext, profileId, userId)
    }

    suspend fun signOut(context: Context) {
        val appContext = context.applicationContext
        val profileId = UserProfiles.active().id
        CloudRealtimeSync.stop()
        runCatching { flushPending(appContext, profileId) }
            .onFailure { Log.w(TAG, "Failed to flush pending mutations before sign-out", it) }
        if (SupabaseProvider.isConfigured) {
            runCatching {
                SupabaseProvider.clientFor(appContext, profileId).auth.signOut()
            }.onFailure { Log.w(TAG, "Remote sign-out failed; clearing local session anyway", it) }
        }
        CloudMutationStore.clearForUser(appContext, profileId, currentUserId(profileId))
        CloudSyncScheduler.cancelForProfile(appContext, profileId)
        CloudAccountStore.setActiveAccount(appContext, profileId, null, null)
    }

    suspend fun syncNow(
        context: Context,
        profileId: String = UserProfiles.active().id,
        onProgress: (CloudSyncProgress) -> Unit = {},
    ) = accountSyncMutex.withLock {
        syncNowLocked(context, profileId, onProgress)
    }

    private suspend fun syncNowLocked(
        context: Context,
        profileId: String,
        onProgress: (CloudSyncProgress) -> Unit,
    ) {
        val appContext = context.applicationContext
        val client = SupabaseProvider.clientFor(appContext, profileId)
        client.auth.awaitInitialization()
        val userId = client.auth.currentSessionOrNull()?.user?.id
            ?: error("Sign in before synchronizing")
        try {
            flushPending(appContext, profileId, client, onProgress)
            onProgress(CloudSyncProgress(CloudSyncProgress.Stage.CHECKING_CLOUD))
            val remote = fetchRemote(client)
            onProgress(
                CloudSyncProgress(
                    CloudSyncProgress.Stage.APPLYING_CLOUD,
                    current = remote.size,
                    total = remote.size,
                ),
            )
            if (UserProfiles.active().id != profileId) {
                Log.i(TAG, "Skipping apply; profile $profileId is no longer active")
                return
            }
            withContext(Dispatchers.IO) { applyRemote(appContext, profileId, remote) }
            onProgress(CloudSyncProgress(CloudSyncProgress.Stage.FINALIZING))
            persistActiveAccount(appContext, profileId, userId, client)
            markLastSynced(appContext, profileId)
        } catch (e: Exception) {
            Log.e(TAG, "syncNow failed for user $userId profile $profileId", e)
            throw e
        }
    }

    fun lastSyncedAtMillis(
        context: Context,
        profileId: String = UserProfiles.active().id,
    ): Long {
        val prefs = context.getSharedPreferences("cloud_sync_meta", Context.MODE_PRIVATE)
        val scoped = prefs.getLong(lastSyncedKey(profileId), 0L)
        if (scoped > 0L) return scoped
        // One-time: migrate unsuffixed legacy timestamp to the default profile.
        if (profileId == UserProfiles.DEFAULT_ID) {
            val legacy = prefs.getLong("last_synced_at", 0L)
            if (legacy > 0L) {
                prefs.edit()
                    .putLong(lastSyncedKey(profileId), legacy)
                    .remove("last_synced_at")
                    .apply()
                return legacy
            }
        }
        return 0L
    }

    private fun lastSyncedKey(profileId: String) = "last_synced_at_$profileId"

    private fun markLastSynced(context: Context, profileId: String) {
        context.getSharedPreferences("cloud_sync_meta", Context.MODE_PRIVATE)
            .edit()
            .putLong(lastSyncedKey(profileId), System.currentTimeMillis())
            .apply()
    }

    suspend fun flushPending(
        context: Context,
        profileId: String = UserProfiles.active().id,
        client: SupabaseClient? = null,
        onProgress: (CloudSyncProgress) -> Unit = {},
    ) {
        val appContext = context.applicationContext
        val supabase = client ?: SupabaseProvider.clientFor(appContext, profileId)
        val userId = supabase.auth.currentSessionOrNull()?.user?.id ?: return
        val maxIterations = 10
        var iteration = 0
        while (iteration < maxIterations) {
            iteration++
            val pending = CloudMutationStore.pendingForUser(appContext, profileId, userId)
            if (pending.isEmpty()) return
            val remoteByKey = fetchRemote(supabase).associateBy { it.queueKey }
            val uploadable = pending.filter { mutation ->
                val remote = remoteByKey[mutation.queueKey]
                remote == null || pendingStateWins(mutation, remote)
            }
            val successfullyUploaded = if (uploadable.isNotEmpty()) {
                upsert(supabase, uploadable, onProgress)
            } else {
                emptyList()
            }
            val acknowledged = successfullyUploaded +
                pending.filter { it !in uploadable }
            CloudMutationStore.acknowledge(appContext, profileId, acknowledged)
        }
        Log.w(
            TAG,
            "flushPending hit max iterations ($maxIterations); " +
                "mutations still pending for user $userId profile $profileId",
        )
    }

    private suspend fun activateAccount(
        context: Context,
        profileId: String,
        userId: String,
        client: SupabaseClient,
        onProgress: (CloudSyncProgress) -> Unit = {},
        mergeLocalOnLogin: Boolean = false,
    ) = accountSyncMutex.withLock {
        val previousUserId = CloudAccountStore.activeUserId(context, profileId)
        if (previousUserId == userId && !mergeLocalOnLogin) {
            syncNowLocked(context, profileId, onProgress)
            return@withLock
        }

        onProgress(CloudSyncProgress(CloudSyncProgress.Stage.CHECKING_CLOUD))
        val remote = fetchRemote(client)
        val legacyOwnerId = CloudAccountStore.legacyOwnerId(context, profileId)
        val canMergeLocal = shouldMergeLocal(
            previousUserId = previousUserId,
            legacyOwnerId = legacyOwnerId,
            userId = userId,
            mergeLocalOnLogin = mergeLocalOnLogin,
        )

        isApplyingRemote = true
        try {
            if (canMergeLocal) {
                onProgress(CloudSyncProgress(CloudSyncProgress.Stage.PREPARING_LOCAL))
                val local = withContext(Dispatchers.IO) {
                    collectLocalState(context, profileId, userId)
                }
                onProgress(CloudSyncProgress(CloudSyncProgress.Stage.MERGING))
                val merged = mergeForFirstLogin(
                    remote = remote,
                    local = local,
                    mergedAtMillis = System.currentTimeMillis(),
                )
                if (local.isNotEmpty()) {
                    val localKeys = local.mapTo(hashSetOf()) { it.queueKey }
                    upsert(client, merged.filter { it.queueKey in localKeys }, onProgress)
                }
                val finalRemote = if (local.isEmpty()) remote else {
                    onProgress(CloudSyncProgress(CloudSyncProgress.Stage.CHECKING_CLOUD))
                    fetchRemote(client)
                }
                onProgress(
                    CloudSyncProgress(
                        CloudSyncProgress.Stage.APPLYING_CLOUD,
                        current = finalRemote.size,
                        total = finalRemote.size,
                    ),
                )
                withContext(Dispatchers.IO) {
                    applyRemoteInternal(context, profileId, finalRemote)
                }
                CloudAccountStore.claimLegacyData(context, profileId, userId)
            } else {
                val local = withContext(Dispatchers.IO) {
                    collectLocalState(context, profileId, userId)
                }
                if (local.isNotEmpty()) {
                    runCatching { client.auth.signOut() }
                    CloudRealtimeSync.stop()
                    throw CloudAccountDataConflictException()
                }

                onProgress(
                    CloudSyncProgress(
                        CloudSyncProgress.Stage.APPLYING_CLOUD,
                        current = remote.size,
                        total = remote.size,
                    ),
                )
                withContext(Dispatchers.IO) {
                    applyRemoteInternal(context, profileId, remote)
                }
            }
            onProgress(CloudSyncProgress(CloudSyncProgress.Stage.FINALIZING))
            persistActiveAccount(context, profileId, userId, client)
        } finally {
            isApplyingRemote = false
        }
    }

    internal suspend fun applyRealtimeState(
        context: Context,
        profileId: String,
        state: RemoteMediaState,
    ) = accountSyncMutex.withLock {
        if (UserProfiles.active().id != profileId) return@withLock
        val userId = currentUserId(profileId)
        val pending = userId?.let {
            CloudMutationStore.pendingForUser(context, profileId, it)
        }.orEmpty()
        if (!shouldApplyRealtimeState(userId, state, pending)) return@withLock

        withContext(Dispatchers.IO) {
            applyRemote(context.applicationContext, profileId, listOf(state))
        }
    }

    internal suspend fun deleteRealtimeState(
        context: Context,
        profileId: String,
        state: RemoteMediaState,
    ) = accountSyncMutex.withLock {
        if (UserProfiles.active().id != profileId) return@withLock
        val userId = currentUserId(profileId)
        if (userId == null || state.userId != userId) return@withLock

        withContext(Dispatchers.IO) {
            deleteRemoteState(context.applicationContext, profileId, state)
        }
    }

    private fun deleteRemoteState(context: Context, profileId: String, state: RemoteMediaState) {
        val provider = providerByName(state.provider) ?: run {
            Log.w(TAG, "Skipping delete for unavailable provider ${state.provider}")
            return
        }
        val db = AppDatabase.getInstanceForProvider(provider.name, context, profileId)
        try {
            db.runInTransaction {
                when (state.mediaType) {
                    "movie" -> db.movieDao().deleteById(state.mediaId)
                    "tv_show" -> db.tvShowDao().deleteById(state.mediaId)
                    "episode" -> db.episodeDao().deleteById(state.mediaId)
                }
            }
            when (state.mediaType) {
                "movie" -> UserDataCache.writeMovies(
                    context, provider, db.movieDao().getAll(), profileId,
                )
                "tv_show" -> UserDataCache.writeTvShows(
                    context, provider, db.tvShowDao().getAllForBackup(), profileId,
                )
                "episode" -> UserDataCache.writeEpisodes(
                    context, provider, db.episodeDao().getAllForBackup(), profileId,
                )
            }
        } finally {
            db.close()
        }
        UserDataNotifier.notifyChanged()
    }

    internal fun shouldApplyRealtimeState(
        currentUserId: String?,
        state: RemoteMediaState,
        pending: List<RemoteMediaState>,
    ): Boolean {
        if (currentUserId == null || state.userId != currentUserId) return false
        return pending.none { mutation ->
            mutation.queueKey == state.queueKey && pendingStateWins(mutation, state)
        }
    }

    /**
     * client_updated_at is the enqueue time, not necessarily when playback
     * happened. Compare actual user-state timestamps before using it as a
     * tie-breaker.
     */
    internal fun pendingStateWins(
        pending: RemoteMediaState,
        remote: RemoteMediaState,
    ): Boolean {
        val pendingStateTime = pending.userStateTimestamp()
        val remoteStateTime = remote.userStateTimestamp()
        return if (pendingStateTime != remoteStateTime) {
            pendingStateTime > remoteStateTime
        } else {
            pending.clientUpdatedAtMillis >= remote.clientUpdatedAtMillis
        }
    }

    private fun RemoteMediaState.userStateTimestamp(): Long = listOfNotNull(
        watchedAtMillis,
        lastEngagementAtMillis,
        favoritedAtMillis,
    ).maxOrNull() ?: clientUpdatedAtMillis

    internal fun shouldMergeLocal(
        previousUserId: String?,
        legacyOwnerId: String?,
        userId: String,
        mergeLocalOnLogin: Boolean,
    ): Boolean {
        val localDataBelongsToUser =
            legacyOwnerId == null || legacyOwnerId == userId
        val accountCanOwnCurrentLocalData =
            previousUserId == null || (mergeLocalOnLogin && previousUserId == userId)
        return localDataBelongsToUser && accountCanOwnCurrentLocalData
    }

    private suspend fun forceMergeLocalAccount(
        context: Context,
        profileId: String,
        userId: String,
        client: SupabaseClient,
        onProgress: (CloudSyncProgress) -> Unit,
    ) {
        onProgress(CloudSyncProgress(CloudSyncProgress.Stage.PREPARING_LOCAL))
        val local = withContext(Dispatchers.IO) {
            collectLocalState(context, profileId, userId)
        }
        onProgress(CloudSyncProgress(CloudSyncProgress.Stage.MERGING))
        val remote = fetchRemote(client)
        val merged = mergeForFirstLogin(
            remote = remote,
            local = local,
            mergedAtMillis = System.currentTimeMillis(),
        )
        if (local.isNotEmpty()) {
            val localKeys = local.mapTo(hashSetOf()) { it.queueKey }
            upsert(client, merged.filter { it.queueKey in localKeys }, onProgress)
        }
        val finalRemote = if (local.isEmpty()) remote else {
            onProgress(CloudSyncProgress(CloudSyncProgress.Stage.CHECKING_CLOUD))
            fetchRemote(client)
        }
        onProgress(
            CloudSyncProgress(
                CloudSyncProgress.Stage.APPLYING_CLOUD,
                current = finalRemote.size,
                total = finalRemote.size,
            ),
        )
        withContext(Dispatchers.IO) {
            applyRemoteInternal(context, profileId, finalRemote)
        }
        CloudAccountStore.claimLegacyData(context, profileId, userId)
    }

    private suspend fun replaceLocalWithCloud(
        context: Context,
        profileId: String,
        userId: String,
        client: SupabaseClient,
        onProgress: (CloudSyncProgress) -> Unit,
    ) {
        withContext(Dispatchers.IO) {
            clearLocalUserState(context, profileId)
        }
        onProgress(CloudSyncProgress(CloudSyncProgress.Stage.CHECKING_CLOUD))
        val remote = fetchRemote(client)
        onProgress(
            CloudSyncProgress(
                CloudSyncProgress.Stage.APPLYING_CLOUD,
                current = remote.size,
                total = remote.size,
            ),
        )
        withContext(Dispatchers.IO) {
            applyRemoteInternal(context, profileId, remote)
        }
        CloudAccountStore.claimLegacyData(context, profileId, userId)
    }

    internal fun mergeForFirstLogin(
        remote: List<RemoteMediaState>,
        local: List<RemoteMediaState>,
        mergedAtMillis: Long,
    ): List<RemoteMediaState> {
        val merged = remote.associateByTo(linkedMapOf()) { it.queueKey }
        local.forEach { localState ->
            val remoteState = merged[localState.queueKey]
            merged[localState.queueKey] = if (remoteState == null) {
                localState.copy(
                    clientUpdatedAtMillis = maxOf(
                        localState.clientUpdatedAtMillis,
                        mergedAtMillis,
                    ),
                )
            } else {
                mergeState(remoteState, localState, mergedAtMillis)
            }
        }
        return merged.values.toList()
    }

    private fun mergeState(
        remote: RemoteMediaState,
        local: RemoteMediaState,
        mergedAtMillis: Long,
    ): RemoteMediaState {
        val newest = if (local.clientUpdatedAtMillis >= remote.clientUpdatedAtMillis) {
            local
        } else {
            remote
        }
        val oldest = if (newest === local) remote else local
        val latestHistory = when {
            newest.isWatched && newest.lastEngagementAtMillis == null -> null
            local.lastEngagementAtMillis == null -> remote.takeIf {
                it.lastEngagementAtMillis != null
            }
            remote.lastEngagementAtMillis == null -> local
            local.lastEngagementAtMillis >= remote.lastEngagementAtMillis -> local
            else -> remote
        }
        return newest.copy(
            parentShowId = newest.parentShowId ?: oldest.parentShowId,
            parentShowTitle = newest.parentShowTitle ?: oldest.parentShowTitle,
            parentShowPoster = newest.parentShowPoster ?: oldest.parentShowPoster,
            parentShowBanner = newest.parentShowBanner ?: oldest.parentShowBanner,
            seasonId = newest.seasonId ?: oldest.seasonId,
            seasonNumber = newest.seasonNumber ?: oldest.seasonNumber,
            seasonTitle = newest.seasonTitle ?: oldest.seasonTitle,
            seasonPoster = newest.seasonPoster ?: oldest.seasonPoster,
            episodeNumber = newest.episodeNumber ?: oldest.episodeNumber,
            title = newest.title.ifBlank { oldest.title },
            poster = newest.poster ?: oldest.poster,
            banner = newest.banner ?: oldest.banner,
            isFavorite = remote.isFavorite || local.isFavorite,
            favoritedAtMillis = maxNullable(
                remote.favoritedAtMillis,
                local.favoritedAtMillis,
            ),
            isWatched = newest.isWatched,
            watchedAtMillis = newest.watchedAtMillis,
            lastEngagementAtMillis = latestHistory?.lastEngagementAtMillis,
            playbackPositionMillis = latestHistory?.playbackPositionMillis,
            durationMillis = latestHistory?.durationMillis,
            isWatching = local.isWatching ?: remote.isWatching ?: newest.isWatching,
            clientUpdatedAtMillis = maxOf(
                remote.clientUpdatedAtMillis,
                local.clientUpdatedAtMillis,
                mergedAtMillis,
            ),
        )
    }

    private fun maxNullable(first: Long?, second: Long?): Long? = when {
        first == null -> second
        second == null -> first
        else -> maxOf(first, second)
    }

    private suspend fun fetchRemote(client: SupabaseClient): List<RemoteMediaState> =
        collectPages(FETCH_PAGE_SIZE) { from, to ->
            client.from(TABLE).select {
                order("provider", Order.ASCENDING)
                order("media_type", Order.ASCENDING)
                order("media_id", Order.ASCENDING)
                range(from, to)
            }.decodeList()
        }

    internal suspend fun <T> collectPages(
        pageSize: Long,
        fetchPage: suspend (from: Long, to: Long) -> List<T>,
    ): List<T> {
        require(pageSize > 0)
        val items = mutableListOf<T>()
        var from = 0L
        do {
            val page = fetchPage(from, from + pageSize - 1)
            items += page
            from += page.size
        } while (page.size == pageSize.toInt())
        return items
    }

    private suspend fun upsert(
        client: SupabaseClient,
        states: List<RemoteMediaState>,
        onProgress: (CloudSyncProgress) -> Unit = {},
    ): List<RemoteMediaState> {
        val uploadedStates = mutableListOf<RemoteMediaState>()
        var uploaded = 0
        onProgress(
            CloudSyncProgress(
                CloudSyncProgress.Stage.UPLOADING,
                current = uploaded,
                total = states.size,
            ),
        )
        states.chunked(250).forEach { chunk ->
            client.from(TABLE).upsert(chunk) {
                onConflict = "user_id,provider,media_type,media_id"
            }
            uploadedStates += chunk
            uploaded += chunk.size
            onProgress(
                CloudSyncProgress(
                    CloudSyncProgress.Stage.UPLOADING,
                    current = uploaded,
                    total = states.size,
                ),
            )
        }
        return uploadedStates
    }

    private fun collectLocalState(
        context: Context,
        profileId: String,
        userId: String,
    ): List<RemoteMediaState> {
        val states = mutableListOf<RemoteMediaState>()
        existingProviders(context, profileId).forEach { provider ->
            val db = AppDatabase.getInstanceForProvider(provider.name, context, profileId)
            try {
                db.movieDao().getAll()
                    .filter { movie ->
                        movie.isFavorite || movie.isWatched || movie.watchedDate != null ||
                            movie.watchHistory != null
                    }
                    .forEach { movie ->
                        states += RemoteMediaState.fromMovie(
                            userId,
                            provider.name,
                            movie,
                            movie.stateTimestamp(),
                        )
                    }
                db.tvShowDao().getAllForBackup()
                    .filter { show ->
                        show.isFavorite || !show.isWatching
                    }
                    .forEach { show ->
                        states += RemoteMediaState.fromTvShow(
                            userId,
                            provider.name,
                            show,
                            show.stateTimestamp(),
                        )
                    }
                db.episodeDao().getAllForBackup()
                    .filter { episode ->
                        episode.isWatched || episode.watchedDate != null || episode.watchHistory != null
                    }
                    .forEach { episode ->
                        states += RemoteMediaState.fromEpisode(
                            userId,
                            provider.name,
                            episode,
                            episode.stateTimestamp(),
                        )
                    }
            } finally {
                db.close()
            }
        }
        return states
    }

    private fun applyRemote(
        context: Context,
        profileId: String,
        states: List<RemoteMediaState>,
    ) {
        isApplyingRemote = true
        try {
            applyRemoteInternal(context, profileId, states)
        } finally {
            isApplyingRemote = false
        }
    }

    private fun applyRemoteInternal(
        context: Context,
        profileId: String,
        states: List<RemoteMediaState>,
    ) {
        states.groupBy { it.provider }.forEach { (providerName, providerStates) ->
            val provider = providerByName(providerName) ?: run {
                Log.w(TAG, "Skipping state for unavailable provider $providerName")
                return@forEach
            }
            val db = AppDatabase.getInstanceForProvider(provider.name, context, profileId)
            try {
                val statesToApply = providerStates.filter { state ->
                    shouldApplyRemoteState(db, state)
                }
                if (statesToApply.isEmpty()) return@forEach

                db.runInTransaction {
                    statesToApply.filter { it.mediaType == "movie" }.forEach { state ->
                        val movie = db.movieDao().getById(state.mediaId)
                            ?: Movie(
                                id = state.mediaId,
                                title = state.title,
                                poster = state.poster,
                                banner = state.banner,
                            )
                        movie.isFavorite = state.isFavorite
                        movie.favoritedAtMillis = state.favoritedAtMillis
                        movie.isWatched = state.isWatched
                        movie.watchedDate = state.watchedAtMillis.toCalendar()
                        movie.watchHistory = state.toWatchHistory()
                        db.movieDao().insert(movie)
                    }

                    statesToApply.filter { it.mediaType == "tv_show" }.forEach { state ->
                        val show = db.tvShowDao().getById(state.mediaId)
                            ?: TvShow(
                                id = state.mediaId,
                                title = state.title,
                                poster = state.poster,
                                banner = state.banner,
                            )
                        show.isFavorite = state.isFavorite
                        show.favoritedAtMillis = state.favoritedAtMillis
                        show.isWatching = state.isWatching ?: true
                        db.tvShowDao().insert(show)
                    }

                    statesToApply.filter { it.mediaType == "episode" }.forEach { state ->
                        val show = state.parentShowId?.let { showId ->
                            db.tvShowDao().getById(showId) ?: TvShow(
                                id = showId,
                                title = state.parentShowTitle.orEmpty(),
                                poster = state.parentShowPoster,
                                banner = state.parentShowBanner,
                            ).also(db.tvShowDao()::insert)
                        }
                        val season = state.seasonId?.let { seasonId ->
                            db.seasonDao().getById(seasonId) ?: Season(
                                id = seasonId,
                                number = state.seasonNumber ?: 0,
                                title = state.seasonTitle,
                                poster = state.seasonPoster,
                                tvShow = show,
                            ).also(db.seasonDao()::insert)
                        }
                        val episode = db.episodeDao().getById(state.mediaId)
                            ?: Episode(
                                id = state.mediaId,
                                number = state.episodeNumber ?: 0,
                                title = state.title,
                                poster = state.poster,
                                tvShow = show,
                                season = season,
                            )
                        episode.isWatched = state.isWatched
                        episode.watchedDate = state.watchedAtMillis.toCalendar()
                        episode.watchHistory = state.toWatchHistory()
                        db.episodeDao().insert(episode)
                    }
                }

                runCatching {
                    UserDataCache.writeMovies(
                        context, provider, db.movieDao().getAll(), profileId,
                    )
                    UserDataCache.writeTvShows(
                        context, provider, db.tvShowDao().getAllForBackup(), profileId,
                    )
                    UserDataCache.writeEpisodes(
                        context, provider, db.episodeDao().getAllForBackup(), profileId,
                    )
                }.onFailure { e ->
                    Log.w(TAG, "Failed to write cache for provider ${provider.name}", e)
                }
            } finally {
                db.close()
            }
        }
        UserDataNotifier.notifyChanged()
    }

    private fun shouldApplyRemoteState(
        database: AppDatabase,
        state: RemoteMediaState,
    ): Boolean {
        return when (state.mediaType) {
            "movie" -> database.movieDao().getById(state.mediaId)?.let { movie ->
                if (movie.cloudStateTimestamp() > state.clientUpdatedAtMillis) return false
                !movie.matchesRemoteState(state)
            } ?: true

            "tv_show" -> database.tvShowDao().getById(state.mediaId)?.let { show ->
                if (show.cloudStateTimestamp() > state.clientUpdatedAtMillis) return false
                !show.matchesRemoteState(state)
            } ?: true

            "episode" -> database.episodeDao().getById(state.mediaId)?.let { episode ->
                if (episode.cloudStateTimestamp() > state.clientUpdatedAtMillis) return false
                !episode.matchesRemoteState(state)
            } ?: true

            else -> false
        }
    }

    private fun Movie.matchesRemoteState(state: RemoteMediaState): Boolean =
        isFavorite == state.isFavorite &&
            favoritedAtMillis == state.favoritedAtMillis &&
            isWatched == state.isWatched &&
            watchedDate?.timeInMillis == state.watchedAtMillis &&
            watchHistory?.lastEngagementTimeUtcMillis == state.lastEngagementAtMillis &&
            watchHistory?.lastPlaybackPositionMillis == state.playbackPositionMillis &&
            watchHistory?.durationMillis == state.durationMillis

    private fun TvShow.matchesRemoteState(state: RemoteMediaState): Boolean =
        isFavorite == state.isFavorite &&
            favoritedAtMillis == state.favoritedAtMillis &&
            isWatching == (state.isWatching ?: true)

    private fun Episode.matchesRemoteState(state: RemoteMediaState): Boolean =
        isWatched == state.isWatched &&
            watchedDate?.timeInMillis == state.watchedAtMillis &&
            watchHistory?.lastEngagementTimeUtcMillis == state.lastEngagementAtMillis &&
            watchHistory?.lastPlaybackPositionMillis == state.playbackPositionMillis &&
            watchHistory?.durationMillis == state.durationMillis

    private fun Movie.cloudStateTimestamp(): Long = listOfNotNull(
        favoritedAtMillis,
        watchedDate?.timeInMillis,
        watchHistory?.lastEngagementTimeUtcMillis,
    ).maxOrNull() ?: Long.MIN_VALUE

    private fun TvShow.cloudStateTimestamp(): Long = listOfNotNull(
        favoritedAtMillis,
    ).maxOrNull() ?: Long.MIN_VALUE

    private fun Episode.cloudStateTimestamp(): Long = listOfNotNull(
        watchedDate?.timeInMillis,
        watchHistory?.lastEngagementTimeUtcMillis,
    ).maxOrNull() ?: Long.MIN_VALUE

    private fun clearLocalUserState(context: Context, profileId: String) {
        existingProviders(context, profileId).forEach { provider ->
            val db = AppDatabase.getInstanceForProvider(provider.name, context, profileId)
            try {
                db.runInTransaction {
                    db.movieDao().clearUserState()
                    db.tvShowDao().clearUserState()
                    db.episodeDao().clearUserState()
                }
            } finally {
                db.close()
            }
        }
        UserDataCache.clearProfile(context, profileId)
        UserDataNotifier.notifyChanged()
    }

    private fun existingProviders(context: Context, profileId: String): List<Provider> =
        allProviders()
            .distinctBy { it.name }
            .filter { provider ->
                val name = AppDatabase.resolveDatabaseName(context, provider.name, profileId)
                context.getDatabasePath(name).exists()
            }

    private fun allProviders(): List<Provider> = (Provider.providers.keys +
        listOf("it", "en", "es", "de", "fr").map(::TmdbProvider)).toList()

    private fun providerByName(name: String): Provider? =
        allProviders().firstOrNull { it.name == name }

    private fun requireConfigured() {
        check(SupabaseProvider.isConfigured) {
            "Configure Supabase in Settings > Account & sync before signing in"
        }
    }

    private fun ensureAccountNotLinkedElsewhere(
        context: Context,
        profileId: String,
        userId: String,
    ) {
        val existing = CloudAccountStore.profileIdForUser(context, userId) ?: return
        if (existing == profileId) return
        val name = UserProfiles.list().find { it.id == existing }?.name ?: existing
        throw CloudAccountAlreadyLinkedException(existing, name)
    }

    private fun persistActiveAccount(
        context: Context,
        profileId: String,
        userId: String,
        client: SupabaseClient,
    ) {
        val email = client.auth.currentSessionOrNull()?.user?.email
        CloudAccountStore.setActiveAccount(context, profileId, userId, email)
    }

    private fun Movie.stateTimestamp(): Long = listOfNotNull(
        favoritedAtMillis,
        watchedDate?.timeInMillis,
        watchHistory?.lastEngagementTimeUtcMillis,
    ).maxOrNull() ?: System.currentTimeMillis()

    private fun TvShow.stateTimestamp(): Long = listOfNotNull(
        favoritedAtMillis,
    ).maxOrNull() ?: System.currentTimeMillis()

    private fun Episode.stateTimestamp(): Long = listOfNotNull(
        watchedDate?.timeInMillis,
        watchHistory?.lastEngagementTimeUtcMillis,
    ).maxOrNull() ?: System.currentTimeMillis()

    private fun RemoteMediaState.toWatchHistory(): WatchItem.WatchHistory? =
        lastEngagementAtMillis?.let { engagedAt ->
            WatchItem.WatchHistory(
                lastEngagementTimeUtcMillis = engagedAt,
                lastPlaybackPositionMillis = playbackPositionMillis ?: 0L,
                durationMillis = durationMillis ?: 0L,
            )
        }

    private fun Long?.toCalendar(): Calendar? = this?.let { millis ->
        Calendar.getInstance().apply { timeInMillis = millis }
    }
}
