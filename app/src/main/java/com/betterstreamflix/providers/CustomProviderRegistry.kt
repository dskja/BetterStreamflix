package com.betterstreamflix.providers

import com.betterstreamflix.utils.UserPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lightweight in-memory and persistent registry for user-defined custom providers.
 *
 * Custom provider configurations are stored as a JSON array in [UserPreferences]
 * under the [UserPreferences.Key.CUSTOM_PROVIDERS] key. The registry is kept
 * separate from the built-in [Provider.providers] map so that tests and the app
 * can still boot cleanly when user preferences are not yet initialized.
 */
object CustomProviderRegistry {

    private val providers = mutableMapOf<String, CustomProvider>()

    /**
     * Returns all currently saved custom providers, loading from preferences if needed.
     */
    fun getAll(): List<CustomProvider> {
        ensureLoaded()
        return providers.values.toList()
    }

    /**
     * Finds a custom provider by its human-readable name.
     */
    fun findByName(name: String): CustomProvider? {
        ensureLoaded()
        return providers[name]
    }

    /**
     * Adds a new custom provider and persists the registry.
     */
    fun add(provider: CustomProvider): Boolean {
        ensureLoaded()
        if (providers.containsKey(provider.name)) return false

        providers[provider.name] = provider
        persist()
        return true
    }

    /**
     * Removes a custom provider by name and persists the registry.
     */
    fun remove(name: String): Boolean {
        ensureLoaded()
        val removed = providers.remove(name) != null
        if (removed) persist()
        return removed
    }

    /**
     * Clears the in-memory cache so the next read re-loads from disk.
     */
    fun invalidate() {
        providers.clear()
    }

    /**
     * Replaces the entire registry with the given providers.
     */
    fun replaceAll(newProviders: List<CustomProvider>) {
        providers.clear()
        newProviders.forEach { providers[it.name] = it }
        persist()
    }

    private fun ensureLoaded() {
        if (providers.isNotEmpty() || !isPrefsAvailable()) return

        val rawJson = UserPreferences.Key.CUSTOM_PROVIDERS.getString()
        if (!rawJson.isNullOrBlank()) {
            runCatching {
                val jsonArray = JSONArray(rawJson)
                providers.clear()
                for (i in 0 until jsonArray.length()) {
                    CustomProvider.fromConfigJson(jsonArray.getJSONObject(i))?.let {
                        providers[it.name] = it
                    }
                }
            }
        }

        // Backward compatibility: old versions stored a Set of JSON strings.
        if (providers.isEmpty()) {
            val rawSet = UserPreferences.Key.CUSTOM_PROVIDERS.getStringSet() ?: emptySet()
            rawSet.forEach { jsonString ->
                CustomProvider.fromConfigJson(JSONObject(jsonString))?.let {
                    providers[it.name] = it
                }
            }
        }
    }

    private fun persist() {
        if (!isPrefsAvailable()) return

        val jsonArray = JSONArray()
        providers.values.forEach { jsonArray.put(it.toConfigJson()) }

        // Store as a single JSON string to keep the data model consistent and compact.
        UserPreferences.Key.CUSTOM_PROVIDERS.setString(jsonArray.toString())
    }

    private fun isPrefsAvailable(): Boolean {
        return runCatching { UserPreferences.Key.CUSTOM_PROVIDERS.getString() }.isSuccess
    }
}
