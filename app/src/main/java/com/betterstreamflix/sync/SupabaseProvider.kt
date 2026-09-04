package com.betterstreamflix.sync

import android.content.Context
import android.net.Uri
import com.betterstreamflix.StreamFlixApp
import com.betterstreamflix.utils.FileLogger
import com.betterstreamflix.utils.UserProfiles
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.SettingsSessionManager
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object SupabaseProvider {
    private const val PREFS = "supabase_connection"
    private const val URL_KEY = "url"
    private const val PUBLIC_KEY = "public_key"
    private const val SESSION_KEY = "streamflix_supabase_session"
    private val clientsMutex = Mutex()

    private data class ProfileClient(
        val fingerprint: String,
        val client: SupabaseClient,
    )

    private val clients = mutableMapOf<String, ProfileClient>()

    @Volatile
    private var configFingerprint: String? = null

    val isConfigured: Boolean
        get() = readConfig(StreamFlixApp.instance)?.let { it.first.isNotEmpty() } == true

    /** Active-profile client; prefer [clientFor] / [clientOrNull] in profile-scoped paths. */
    val client: SupabaseClient
        get() = clientOrNull(UserProfiles.active().id)
            ?: error("Supabase has not been initialized for the active profile")

    fun activeClientOrNull(): SupabaseClient? = clientOrNull(UserProfiles.active().id)

    fun clientOrNull(profileId: String): SupabaseClient? = clients[profileId]?.client

    fun configured(context: Context): Boolean = readConfig(context) != null

    fun getUrl(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(URL_KEY, "").orEmpty()

    fun getPublicKey(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(PUBLIC_KEY, "").orEmpty()

    suspend fun saveConfig(context: Context, url: String, publicKey: String) {
        val normalizedUrl = normalizeUrl(url)
            ?: throw IllegalArgumentException("Enter a valid HTTPS Supabase URL")
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(URL_KEY, normalizedUrl)
            .putString(PUBLIC_KEY, publicKey.trim())
            .apply()
        invalidateAllClients()
    }

    suspend fun clientFor(context: Context, profileId: String): SupabaseClient {
        initialize(context, profileId)
        return clients[profileId]?.client
            ?: error("Supabase has not been initialized for profile $profileId")
    }

    suspend fun removeProfile(profileId: String) {
        clientsMutex.withLock {
            clients.remove(profileId)?.let { existing ->
                runCatching { existing.client.close() }
            }
        }
    }

    /**
     * Initialize the Supabase client for [profileId] (defaults to the active local profile).
     * URL/key remain global; only the Auth session storage is profile-scoped.
     */
    suspend fun initialize(
        context: Context,
        profileId: String = UserProfiles.active().id,
    ) {
        FileLogger.i("SupabaseProvider", "initialize() called for profile=$profileId")
        val config = readConfig(context)
        if (config == null) {
            FileLogger.i("SupabaseProvider", "initialize: no config found, skipping (not configured)")
            return
        }
        FileLogger.i("SupabaseProvider", "initialize: config found, url=${config.first}")
        val fingerprint = config.first + "\u0000" + config.second
        clients[profileId]?.takeIf { it.fingerprint == fingerprint }?.let {
            FileLogger.i("SupabaseProvider", "initialize: client already exists for profile=$profileId")
            return
        }
        FileLogger.i("SupabaseProvider", "initialize: creating new SupabaseClient for profile=$profileId")
        clientsMutex.withLock {
            clients[profileId]?.takeIf { it.fingerprint == fingerprint }?.let { return@withLock }
            if (configFingerprint != null && configFingerprint != fingerprint) {
                closeAllClientsLocked()
            }
            try {
                createSupabaseClient(
                    supabaseUrl = config.first,
                    supabaseKey = config.second,
                ) {
                    install(Auth) {
                        sessionManager = SettingsSessionManager(
                            key = sessionKey(profileId, fingerprint),
                        )
                    }
                    install(Postgrest)
                    install(Realtime)
                }.also { created ->
                    clients[profileId]?.client?.let { old ->
                        runCatching { old.close() }
                    }
                    clients[profileId] = ProfileClient(fingerprint, created)
                    configFingerprint = fingerprint
                    FileLogger.i(
                        "SupabaseProvider",
                        "initialize: ✓ SupabaseClient created for profile=$profileId",
                    )
                }
            } catch (e: Exception) {
                FileLogger.e("SupabaseProvider", "initialize: ✗ FAILED to create client", e)
                throw e
            }
        }
    }

    suspend fun clearConfig(context: Context) {
        invalidateAllClients()
        configFingerprint = null
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    private suspend fun invalidateAllClients() {
        runCatching { CloudRealtimeSync.stop() }
        clientsMutex.withLock {
            closeAllClientsLocked()
        }
        configFingerprint = null
    }

    private suspend fun closeAllClientsLocked() {
        clients.values.forEach { existing ->
            runCatching { existing.client.close() }
        }
        clients.clear()
    }

    /**
     * Default profile keeps the legacy unsuffixed session key so existing users
     * are not signed out during the profiles migration.
     */
    internal fun sessionKey(profileId: String, fingerprint: String): String {
        val legacy = "$SESSION_KEY-${fingerprint.hashCode()}"
        return if (profileId == UserProfiles.DEFAULT_ID) legacy else "$legacy-$profileId"
    }

    private fun readConfig(context: Context): Pair<String, String>? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val url = normalizeUrl(prefs.getString(URL_KEY, null)) ?: return null
        val key = prefs.getString(PUBLIC_KEY, null)?.trim().orEmpty()
        if (key.isEmpty()) return null
        return url to key
    }

    private fun normalizeUrl(raw: String?): String? {
        val parsed = raw?.trim()?.let(Uri::parse) ?: return null
        if (parsed.scheme != "https" || parsed.host.isNullOrBlank()) return null
        return raw.trim().trimEnd('/')
    }
}
