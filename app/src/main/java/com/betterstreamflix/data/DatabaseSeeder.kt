package com.betterstreamflix.data

import android.content.Context
import androidx.core.content.edit

/**
 * Database seeder — seeds initial data on first install or after
 * database reset.
 */
object DatabaseSeeder {

    /**
     * Seed initial data if database is empty.
     */
    fun seedIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences("db_seeding", Context.MODE_PRIVATE)
        if (prefs.getBoolean("seeded", false)) return

        seedDefaultProviders(context)
        seedDefaultCollections(context)

        prefs.edit { putBoolean("seeded", true) }
    }

    /**
     * Seed default provider configurations.
     */
    private fun seedDefaultProviders(context: Context) {
        val providers = listOf(
            ProviderSeed("animeonline", "AnimeOnline", "https://animeonline.ninja", true, 1),
            ProviderSeed("local", "Local Files", "", false, 0),
        )

        val prefs = context.getSharedPreferences("seeded_providers", Context.MODE_PRIVATE)
        prefs.edit {
            providers.forEach { p ->
                putString("${p.name}_display", p.displayName)
                putString("${p.name}_url", p.baseUrl)
                putBoolean("${p.name}_enabled", p.isEnabled)
                putInt("${p.name}_priority", p.priority)
            }
        }
    }

    /**
     * Seed default content collections.
     */
    private fun seedDefaultCollections(context: Context) {
        val collections = listOf(
            "Watchlist" to "Content to watch later",
            "Favorites" to "Your favorite content",
            "Continue Watching" to "Resume where you left off",
        )

        val prefs = context.getSharedPreferences("seeded_collections", Context.MODE_PRIVATE)
        prefs.edit {
            collections.forEach { (name, desc) ->
                putString("collection_${name}_desc", desc)
                putLong("collection_${name}_created", System.currentTimeMillis())
            }
        }
    }

    /**
     * Reset seeding state (for testing).
     */
    fun resetSeeding(context: Context) {
        context.getSharedPreferences("db_seeding", Context.MODE_PRIVATE).edit { clear() }
        context.getSharedPreferences("seeded_providers", Context.MODE_PRIVATE).edit { clear() }
        context.getSharedPreferences("seeded_collections", Context.MODE_PRIVATE).edit { clear() }
    }

    private data class ProviderSeed(
        val name: String,
        val displayName: String,
        val baseUrl: String,
        val isEnabled: Boolean,
        val priority: Int,
    )
}
