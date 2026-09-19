package com.dskja.betterstreamflix.platform.plugins

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * Loads plugin catalog manifests from assets (`plugins/catalog.json`) or app files.
 * Remote APK classloading is intentionally not implemented yet — catalog only.
 */
object PluginCatalog {
    private const val TAG = "PluginCatalog"
    private const val ASSET_PATH = "plugins/catalog.json"

    data class Entry(
        val id: String,
        val name: String,
        val version: String,
        val description: String,
        val source: PluginManifest.Source,
        val downloadUrl: String = "",
        val sha256: String = "",
        val apiVersion: Int = 1,
    )

    fun loadFromAssets(context: Context): List<Entry> = runCatching {
        context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
            .let { parse(it) }
    }.getOrElse {
        Log.d(TAG, "No asset catalog: ${it.message}")
        emptyList()
    }

    fun loadFromJson(raw: String): List<Entry> = parse(raw)

    fun parse(raw: String): List<Entry> {
        val root = JSONObject(raw)
        val list = root.optJSONArray("plugins") ?: JSONArray()
        return buildList {
            for (i in 0 until list.length()) {
                val o = list.optJSONObject(i) ?: continue
                val id = o.optString("id")
                if (id.isBlank()) continue
                val source = when (o.optString("source").lowercase()) {
                    "local" -> PluginManifest.Source.LOCAL
                    "remote" -> PluginManifest.Source.REMOTE
                    else -> PluginManifest.Source.BUILTIN
                }
                val versionValue = o.opt("version")
                val version = when (versionValue) {
                    is Number -> versionValue.toString()
                    is String -> versionValue.ifBlank { "1" }
                    else -> "1"
                }
                add(
                    Entry(
                        id = id,
                        name = o.optString("name", id),
                        version = version,
                        description = o.optString("description"),
                        source = source,
                        downloadUrl = o.optString("downloadUrl"),
                        sha256 = o.optString("sha256"),
                        apiVersion = o.optInt("apiVersion", 1),
                    ),
                )
            }
        }
    }

    /** Register catalog LOCAL entries as disabled-by-default stubs in the registry. */
    fun registerLocalStubs(context: Context) {
        loadFromAssets(context).filter { it.source == PluginManifest.Source.LOCAL }.forEach { entry ->
            if (PluginRegistry.get(entry.id) != null) return@forEach
            PluginRegistry.register(
                object : SourcePlugin {
                    override val manifest = PluginManifest(
                        id = entry.id,
                        name = entry.name,
                        version = entry.version,
                        language = "en",
                        description = entry.description,
                        source = PluginManifest.Source.LOCAL,
                        capabilities = PluginManifest.Capabilities(),
                    )
                    override fun createProvider(): com.dskja.betterstreamflix.providers.Provider {
                        error("Local plugin ${entry.id} has no provider implementation yet")
                    }
                    override fun isEnabled(): Boolean = false
                },
            )
        }
    }
}
