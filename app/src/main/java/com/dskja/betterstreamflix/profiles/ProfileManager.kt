package com.dskja.betterstreamflix.profiles

import android.content.Context
import com.dskja.betterstreamflix.database.AppDatabase
import com.dskja.betterstreamflix.ui.UserDataNotifier
import com.dskja.betterstreamflix.utils.ProviderChangeNotifier
import com.dskja.betterstreamflix.utils.UserDataCache
import java.security.MessageDigest
import java.util.UUID

object ProfileManager {

    const val DEFAULT_PROFILE_ID = ProfileStore.DEFAULT_PROFILE_ID

    val avatarKeys = listOf(
        "crimson",
        "ember",
        "aurora",
        "slate",
        "forest",
        "ocean",
        "gold",
        "rose",
    )

    private lateinit var appContext: Context

    val activeProfileId: String
        get() = if (::appContext.isInitialized) {
            ProfileStore.getActiveId(appContext) ?: DEFAULT_PROFILE_ID
        } else {
            DEFAULT_PROFILE_ID
        }

    fun init(context: Context) {
        appContext = context.applicationContext
        ProfileStore.ensureDefaultExists(appContext)
    }

    fun activeProfile(): UserProfile? =
        profiles().find { it.id == activeProfileId }

    fun profiles(): List<UserProfile> {
        if (!::appContext.isInitialized) return emptyList()
        return ProfileStore.loadAll(appContext)
    }

    fun create(
        name: String,
        isKids: Boolean = false,
        avatarKey: String = avatarKeys.first(),
    ): UserProfile {
        require(::appContext.isInitialized) { "ProfileManager.init() must be called first" }
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty()) { "Profile name cannot be empty" }
        require(avatarKey in avatarKeys) { "Unknown avatar key: $avatarKey" }

        val now = System.currentTimeMillis()
        val profile = UserProfile(
            id = UUID.randomUUID().toString().replace("-", "").take(12),
            displayName = trimmedName,
            avatarKey = avatarKey,
            isKids = isKids,
            maxAgeRating = if (isKids) 12 else null,
            createdAtMillis = now,
            updatedAtMillis = now,
        )
        val updated = profiles() + profile
        ProfileStore.saveAll(appContext, updated)
        return profile
    }

    fun rename(id: String, newName: String): Boolean {
        require(::appContext.isInitialized) { "ProfileManager.init() must be called first" }
        val trimmedName = newName.trim()
        if (trimmedName.isEmpty()) return false

        val current = profiles()
        val index = current.indexOfFirst { it.id == id }
        if (index < 0) return false

        val now = System.currentTimeMillis()
        val updated = current.toMutableList()
        updated[index] = updated[index].copy(
            displayName = trimmedName,
            updatedAtMillis = now,
        )
        ProfileStore.saveAll(appContext, updated)
        return true
    }

    fun delete(id: String): Boolean {
        require(::appContext.isInitialized) { "ProfileManager.init() must be called first" }
        val current = profiles()
        if (current.size <= 1) return false
        if (id == DEFAULT_PROFILE_ID && current.size == 1) return false
        if (current.none { it.id == id }) return false

        val updated = current.filterNot { it.id == id }
        ProfileStore.saveAll(appContext, updated)

        if (activeProfileId == id) {
            switchTo(appContext, DEFAULT_PROFILE_ID)
        }
        return true
    }

    fun switchTo(context: Context, id: String): Boolean {
        require(::appContext.isInitialized) { "ProfileManager.init() must be called first" }
        val profile = profiles().find { it.id == id } ?: return false
        if (activeProfileId == id) return true

        ProfileStore.setActiveId(context.applicationContext, profile.id)
        AppDatabase.resetInstance()
        UserDataCache.clearMemory()
        UserDataNotifier.notifyChanged()
        ProviderChangeNotifier.notifyProviderChanged()
        return true
    }

    fun setPin(profileId: String, pin: String): Boolean {
        require(::appContext.isInitialized) { "ProfileManager.init() must be called first" }
        if (pin.isEmpty()) return false
        return updateProfile(profileId) { it.copy(pinHash = hashPin(pin, profileId)) }
    }

    fun clearPin(profileId: String): Boolean =
        updateProfile(profileId) { it.copy(pinHash = null) }

    fun verifyPin(profileId: String, pin: String): Boolean {
        val profile = profiles().find { it.id == profileId } ?: return false
        val stored = profile.pinHash ?: return false
        return stored == hashPin(pin, profileId)
    }

    fun scopedPrefKey(base: String): String = scopedPrefKeyFor(base, activeProfileId)

    internal fun scopedPrefKeyFor(base: String, profileId: String): String =
        if (profileId == DEFAULT_PROFILE_ID) base else "${base}_p_$profileId"

    internal fun hashPin(pin: String, profileId: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest("$pin$profileId".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun updateProfile(profileId: String, transform: (UserProfile) -> UserProfile): Boolean {
        require(::appContext.isInitialized) { "ProfileManager.init() must be called first" }
        val current = profiles()
        val index = current.indexOfFirst { it.id == profileId }
        if (index < 0) return false

        val now = System.currentTimeMillis()
        val updated = current.toMutableList()
        updated[index] = transform(updated[index]).copy(updatedAtMillis = now)
        ProfileStore.saveAll(appContext, updated)
        return true
    }
}
