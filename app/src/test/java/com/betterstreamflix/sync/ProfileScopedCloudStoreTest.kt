package com.betterstreamflix.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.betterstreamflix.utils.UserProfiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CloudAccountStoreTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("cloud_account_state", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun migratesLegacyKeysOnlyToDefaultProfile() {
        context.getSharedPreferences("cloud_account_state", Context.MODE_PRIVATE)
            .edit()
            .putString("active_user_id", "user-a")
            .putString("legacy_owner_id", "user-a")
            .commit()

        assertEquals("user-a", CloudAccountStore.activeUserId(context, UserProfiles.DEFAULT_ID))
        assertEquals("user-a", CloudAccountStore.legacyOwnerId(context, UserProfiles.DEFAULT_ID))
        assertNull(CloudAccountStore.activeUserId(context, "other-profile"))
        assertNull(
            context.getSharedPreferences("cloud_account_state", Context.MODE_PRIVATE)
                .getString("active_user_id", null),
        )
    }

    @Test
    fun profileIdForUserRejectsDuplicateLinkingLookup() {
        CloudAccountStore.setActiveAccount(context, "profile-a", "user-1", "a@example.com")
        CloudAccountStore.setActiveAccount(context, "profile-b", "user-2", "b@example.com")

        assertEquals("profile-a", CloudAccountStore.profileIdForUser(context, "user-1"))
        assertEquals("profile-b", CloudAccountStore.profileIdForUser(context, "user-2"))
        assertNull(CloudAccountStore.profileIdForUser(context, "missing"))
    }

    @Test
    fun clearProfileLeavesOtherProfilesIntact() {
        CloudAccountStore.setActiveAccount(context, "profile-a", "user-1", "a@example.com")
        CloudAccountStore.setActiveAccount(context, "profile-b", "user-2", "b@example.com")
        CloudAccountStore.claimLegacyData(context, "profile-a", "user-1")

        CloudAccountStore.clearProfile(context, "profile-a")

        assertNull(CloudAccountStore.activeUserId(context, "profile-a"))
        assertEquals("user-2", CloudAccountStore.activeUserId(context, "profile-b"))
        assertEquals("b@example.com", CloudAccountStore.activeUserEmail(context, "profile-b"))
    }
}

@RunWith(RobolectricTestRunner::class)
class CloudMutationStoreTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("cloud_sync_queue", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun migratesGlobalQueueOnlyToDefault() {
        val state = sampleState("user-a", "movie-1", 10L)
        val json = kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
        val encoded = json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(RemoteMediaState.serializer()),
            listOf(state),
        )
        context.getSharedPreferences("cloud_sync_queue", Context.MODE_PRIVATE)
            .edit()
            .putString("pending_media_states", encoded)
            .commit()

        val pending = CloudMutationStore.pendingForUser(context, UserProfiles.DEFAULT_ID, "user-a")
        assertEquals(1, pending.size)
        assertEquals("movie-1", pending.first().mediaId)
        assertTrue(
            CloudMutationStore.pendingForUser(context, "other", "user-a").isEmpty(),
        )
    }

    @Test
    fun acknowledgeKeepsNewerQueuedVersion() {
        val profileId = "profile-a"
        val older = sampleState("user-a", "movie-1", 10L)
        val newer = sampleState("user-a", "movie-1", 20L)
        CloudMutationStore.enqueue(context, profileId, older)
        CloudMutationStore.enqueue(context, profileId, newer)

        CloudMutationStore.acknowledge(context, profileId, listOf(older))

        val pending = CloudMutationStore.pendingForUser(context, profileId, "user-a")
        assertEquals(1, pending.size)
        assertEquals(20L, pending.first().clientUpdatedAtMillis)
    }

    @Test
    fun queuesAreIsolatedPerProfile() {
        CloudMutationStore.enqueue(context, "a", sampleState("u", "m1", 1L))
        CloudMutationStore.enqueue(context, "b", sampleState("u", "m2", 2L))

        assertEquals(1, CloudMutationStore.pendingForUser(context, "a", "u").size)
        assertEquals("m1", CloudMutationStore.pendingForUser(context, "a", "u").first().mediaId)
        assertEquals("m2", CloudMutationStore.pendingForUser(context, "b", "u").first().mediaId)

        CloudMutationStore.clearProfile(context, "a")
        assertTrue(CloudMutationStore.pendingForUser(context, "a", "u").isEmpty())
        assertEquals(1, CloudMutationStore.pendingForUser(context, "b", "u").size)
    }

    private fun sampleState(userId: String, mediaId: String, updatedAt: Long) = RemoteMediaState(
        userId = userId,
        provider = "TestProvider",
        mediaType = "movie",
        mediaId = mediaId,
        title = "Title",
        poster = null,
        banner = null,
        parentShowId = null,
        parentShowTitle = null,
        parentShowPoster = null,
        parentShowBanner = null,
        seasonId = null,
        seasonNumber = null,
        seasonTitle = null,
        seasonPoster = null,
        episodeNumber = null,
        isFavorite = false,
        favoritedAtMillis = null,
        isWatched = false,
        watchedAtMillis = null,
        lastEngagementAtMillis = null,
        playbackPositionMillis = null,
        durationMillis = null,
        isWatching = null,
        clientUpdatedAtMillis = updatedAt,
    )
}

@RunWith(RobolectricTestRunner::class)
class SupabaseProviderSessionKeyTest {
    @Test
    fun defaultProfileKeepsLegacySessionKey() {
        val fingerprint = "https://example.supabase.co\u0000anon"
        val key = SupabaseProvider.sessionKey(UserProfiles.DEFAULT_ID, fingerprint)
        assertEquals(
            "streamflix_supabase_session-${fingerprint.hashCode()}",
            key,
        )
    }

    @Test
    fun otherProfilesUseSuffixedSessionKey() {
        val fingerprint = "https://example.supabase.co\u0000anon"
        val key = SupabaseProvider.sessionKey("kids-profile", fingerprint)
        assertEquals(
            "streamflix_supabase_session-${fingerprint.hashCode()}-kids-profile",
            key,
        )
    }
}

@RunWith(RobolectricTestRunner::class)
class AppDatabaseProfileNamingTest {
    @Test
    fun databaseNameIncludesProfileAndProvider() {
        assertEquals(
            "default_testprovider.db",
            com.betterstreamflix.database.AppDatabase.databaseNameFor("TestProvider", "default"),
        )
        assertEquals(
            "abc123_serienstream.db",
            com.betterstreamflix.database.AppDatabase.databaseNameFor("SerienStream", "abc123"),
        )
    }
}

@RunWith(RobolectricTestRunner::class)
class CloudSyncManagerProfileLogicTest {
    @Test
    fun shouldMergeLocalPreservesOwnershipRules() {
        assertTrue(
            CloudSyncManager.shouldMergeLocal(
                previousUserId = null,
                legacyOwnerId = null,
                userId = "u1",
                mergeLocalOnLogin = true,
            ),
        )
        assertTrue(
            !CloudSyncManager.shouldMergeLocal(
                previousUserId = "u2",
                legacyOwnerId = "u2",
                userId = "u1",
                mergeLocalOnLogin = true,
            ),
        )
    }

    @Test
    fun pendingStateWinsUsesUserStateTimestamps() {
        val pending = RemoteMediaState(
            userId = "u",
            provider = "p",
            mediaType = "movie",
            mediaId = "m",
            title = "t",
            poster = null,
            banner = null,
            parentShowId = null,
            parentShowTitle = null,
            parentShowPoster = null,
            parentShowBanner = null,
            seasonId = null,
            seasonNumber = null,
            seasonTitle = null,
            seasonPoster = null,
            episodeNumber = null,
            isFavorite = false,
            favoritedAtMillis = null,
            isWatched = true,
            watchedAtMillis = 200L,
            lastEngagementAtMillis = null,
            playbackPositionMillis = null,
            durationMillis = null,
            isWatching = null,
            clientUpdatedAtMillis = 1L,
        )
        val remote = pending.copy(watchedAtMillis = 100L, clientUpdatedAtMillis = 999L)
        assertTrue(CloudSyncManager.pendingStateWins(pending, remote))
    }
}
