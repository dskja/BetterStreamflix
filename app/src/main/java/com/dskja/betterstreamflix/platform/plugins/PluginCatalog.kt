package com.dskja.betterstreamflix.platform.plugins

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Loads plugin catalog manifests from assets (`plugins/catalog.json`) and app-private files.
 * LOCAL APKs are verified (SHA-256 + apiVersion) then loaded via [PluginApkLoader].
 * Remote APK auto-download/install remains intentionally unsupported.
 */
object PluginCatalog {
    private const val TAG = "PluginCatalog"
    private const val ASSET_PATH = "plugins/catalog.json"
    private const val FILES_DIR = "plugins"
    private const val FILES_NAME = "catalog.json"

    data class Entry(
        val id: String,
        val name: String,
        val version: String,
        val description: String,
        val source: PluginManifest.Source,
        val downloadUrl: String = "",
        val sha256: String = "",
        val apiVersion: Int = 1,
        val entryClass: String = "",
        val capabilities: PluginManifest.Capabilities? = null,
        val author: String = "",
    )

    fun catalogFile(context: Context): File =
        File(File(context.filesDir, FILES_DIR).also { it.mkdirs() }, FILES_NAME)

    fun loadFromAssets(context: Context): List<Entry> = runCatching {
        context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
            .let { parse(it) }
    }.getOrElse {
        Log.d(TAG, "No asset catalog: ${it.message}")
        emptyList()
    }

    fun loadFromFiles(context: Context): List<Entry> = runCatching {
        val file = catalogFile(context)
        if (!file.exists()) return emptyList()
        parse(file.readText())
    }.getOrElse {
        Log.d(TAG, "No file catalog: ${it.message}")
        emptyList()
    }

    /** Merge assets + files (files override same id). */
    fun loadMerged(context: Context): List<Entry> {
        val map = linkedMapOf<String, Entry>()
        loadFromAssets(context).forEach { map[it.id] = it }
        loadFromFiles(context).forEach { map[it.id] = it }
        return map.values.toList()
    }

    fun loadFromJson(raw: String): List<Entry> = parse(raw)

    fun writeFilesCatalog(context: Context, entries: List<Entry>) {
        val root = JSONObject()
            .put("name", "BetterStreamflix local catalog")
            .put("manifestVersion", 1)
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(
                JSONObject()
                    .put("id", e.id)
                    .put("name", e.name)
                    .put("version", e.version)
                    .put("description", e.description)
                    .put(
                        "source",
                        when (e.source) {
                            PluginManifest.Source.LOCAL -> "local"
                            PluginManifest.Source.REMOTE -> "remote"
                            PluginManifest.Source.BUILTIN -> "builtin"
                        },
                    )
                    .put("downloadUrl", e.downloadUrl)
                    .put("sha256", e.sha256)
                    .put("apiVersion", e.apiVersion)
                    .put("entryClass", e.entryClass)
                    .put("author", e.author)
                    .also { obj ->
                        val caps = e.capabilities?.labels().orEmpty()
                        if (caps.isNotEmpty()) {
                            obj.put("capabilities", JSONArray(caps))
                        }
                    },
            )
        }
        root.put("plugins", arr)
        catalogFile(context).writeText(root.toString(2))
    }

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
                val capLabels = buildList {
                    val arr = o.optJSONArray("capabilities")
                    if (arr != null) {
                        for (j in 0 until arr.length()) {
                            val label = arr.optString(j)
                            if (label.isNotBlank()) add(label)
                        }
                    }
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
                        entryClass = o.optString("entryClass"),
                        capabilities = if (capLabels.isEmpty()) null
                        else PluginManifest.capabilitiesFromLabels(capLabels),
                        author = o.optString("author"),
                    ),
                )
            }
        }
    }

    fun summaryLine(entry: Entry, disabled: Boolean): String {
        val src = entry.source.name.lowercase()
        val loaded = PluginRegistry.get(entry.id)?.let { plugin ->
            if (plugin.isEnabled()) "active" else "registered"
        } ?: "listed"
        val state = if (disabled) "hidden" else loaded
        return "${entry.name} · v${entry.version} · $src · $state"
    }

    /** Register catalog LOCAL/REMOTE entries as disabled-by-default stubs in the registry. */
    fun registerLocalStubs(context: Context) {
        loadMerged(context)
            .filter {
                it.source == PluginManifest.Source.LOCAL ||
                    it.source == PluginManifest.Source.REMOTE
            }
            .forEach { entry ->
                // Do not overwrite a successfully loaded APK plugin.
                val existing = PluginRegistry.get(entry.id)
                if (existing != null && existing.manifest.source == PluginManifest.Source.LOCAL) {
                    val canCreate = runCatching { existing.createProvider(); true }.getOrDefault(false)
                    if (canCreate) return@forEach
                }
                if (existing != null && existing.manifest.source == PluginManifest.Source.BUILTIN) {
                    return@forEach
                }
                PluginRegistry.register(
                    object : SourcePlugin {
                        override val manifest = PluginManifest(
                            id = entry.id,
                            name = entry.name,
                            version = entry.version,
                            language = "en",
                            author = entry.author.ifBlank { "Community" },
                            description = entry.description,
                            source = entry.source,
                            capabilities = entry.capabilities ?: PluginManifest.Capabilities(
                                movies = false,
                                tvShows = false,
                            ),
                            minApiVersion = entry.apiVersion,
                        )
                        override fun createProvider(): com.dskja.betterstreamflix.providers.Provider {
                            error("Plugin ${entry.id} has no provider implementation yet")
                        }
                        override fun isEnabled(): Boolean =
                            !com.dskja.betterstreamflix.utils.UserPreferences.isPluginDisabled(entry.id) &&
                                false // stubs never activate providers
                    },
                )
            }
    }

    fun reload(context: Context) {
        // Keep builtins; re-register stubs from catalogs; load verified local APKs.
        registerLocalStubs(context)
        PluginApkLoader.loadInstalled(context)
    }
}
