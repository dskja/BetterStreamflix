package com.dskja.betterstreamflix.platform.plugins

import android.content.Context
import android.util.Log
import com.dskja.betterstreamflix.providers.Provider
import com.dskja.betterstreamflix.utils.UserPreferences
import dalvik.system.DexClassLoader
import java.io.File

/**
 * Loads verified LOCAL plugin APKs via [DexClassLoader] into [PluginRegistry].
 * Remote auto-install is intentionally unsupported — user/catalog-driven only.
 */
object PluginApkLoader {
    private const val TAG = "PluginApkLoader"
    private const val PLUGINS_DIR = "plugins"
    private const val OPT_DIR = "plugins_opt"
    private const val APKS_DIR = "apks"

    data class LoadResult(
        val pluginId: String,
        val name: String,
        val success: Boolean,
        val message: String,
    )

    fun pluginsDir(context: Context): File =
        File(context.filesDir, PLUGINS_DIR).also { it.mkdirs() }

    fun apksDir(context: Context): File =
        File(pluginsDir(context), APKS_DIR).also { it.mkdirs() }

    fun optDir(context: Context): File =
        File(context.codeCacheDir ?: context.cacheDir, OPT_DIR).also { it.mkdirs() }

    fun apkFileFor(context: Context, pluginId: String): File {
        val safe = pluginId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return File(apksDir(context), "$safe.apk")
    }

    /**
     * Copy a user-selected APK into app-private storage, verify against [entry], then load.
     */
    fun installAndLoad(
        context: Context,
        sourceApk: File,
        entry: PluginCatalog.Entry,
    ): LoadResult {
        if (entry.source == PluginManifest.Source.REMOTE) {
            return LoadResult(entry.id, entry.name, false, "Remote APK auto-install is disabled")
        }
        val target = apkFileFor(context, entry.id)
        return runCatching {
            sourceApk.copyTo(target, overwrite = true)
            loadVerified(context, target, entry)
        }.getOrElse {
            LoadResult(entry.id, entry.name, false, it.message ?: "install failed")
        }
    }

    /** Scan installed APKs that match catalog LOCAL entries and register them. */
    fun loadInstalled(context: Context): List<LoadResult> {
        val catalog = PluginCatalog.loadMerged(context)
            .filter { it.source == PluginManifest.Source.LOCAL }
        if (catalog.isEmpty()) return emptyList()
        return catalog.mapNotNull { entry ->
            val apk = resolveApkPath(context, entry) ?: return@mapNotNull null
            if (!apk.isFile) return@mapNotNull null
            loadVerified(context, apk, entry)
        }
    }

    fun loadVerified(
        context: Context,
        apkFile: File,
        entry: PluginCatalog.Entry,
    ): LoadResult {
        val verify = PluginVerifier.verify(
            apkFile = apkFile,
            expectedSha256 = entry.sha256,
            apiVersion = entry.apiVersion,
            entryClass = entry.entryClass,
        )
        if (verify is PluginVerifier.Result.Rejected) {
            Log.w(TAG, "Reject ${entry.id}: ${verify.reason}")
            return LoadResult(entry.id, entry.name, false, verify.reason)
        }

        return runCatching {
            val loader = DexClassLoader(
                apkFile.absolutePath,
                optDir(context).absolutePath,
                /* librarySearchPath = */ null,
                context.classLoader,
            )
            val clazz = Class.forName(entry.entryClass, true, loader)
            val instance = instantiatePlugin(clazz)
                ?: error("entryClass must implement SourcePlugin with a no-arg constructor")
            val wrapped = wrapLoaded(entry, instance)
            PluginRegistry.register(wrapped)
            Provider.registerDynamic(
                wrapped.createProvider(),
                Provider.Companion.ProviderSupport(
                    movies = wrapped.manifest.capabilities.movies,
                    tvShows = wrapped.manifest.capabilities.tvShows,
                ),
            )
            Log.i(TAG, "Loaded plugin ${entry.id} (${entry.name})")
            LoadResult(entry.id, entry.name, true, "ok")
        }.getOrElse {
            Log.w(TAG, "Load failed ${entry.id}: ${it.message}")
            LoadResult(entry.id, entry.name, false, it.message ?: "load failed")
        }
    }

    private fun resolveApkPath(context: Context, entry: PluginCatalog.Entry): File? {
        val staged = apkFileFor(context, entry.id)
        if (staged.isFile) return staged
        val download = entry.downloadUrl.trim()
        if (download.startsWith("/") || download.startsWith("file:")) {
            val path = download.removePrefix("file://")
            val file = File(path)
            if (file.isFile) return file
        }
        if (download.isNotBlank() && !download.startsWith("http", ignoreCase = true)) {
            val relative = File(pluginsDir(context), download)
            if (relative.isFile) return relative
        }
        return null
    }

    private fun instantiatePlugin(clazz: Class<*>): SourcePlugin? {
        if (!SourcePlugin::class.java.isAssignableFrom(clazz)) return null
        val ctor = clazz.getDeclaredConstructor().also { it.isAccessible = true }
        return ctor.newInstance() as SourcePlugin
    }

    private fun wrapLoaded(entry: PluginCatalog.Entry, loaded: SourcePlugin): SourcePlugin {
        return object : SourcePlugin {
            override val manifest = loaded.manifest.copy(
                id = entry.id.ifBlank { loaded.manifest.id },
                name = entry.name.ifBlank { loaded.manifest.name },
                version = entry.version.ifBlank { loaded.manifest.version },
                description = entry.description.ifBlank { loaded.manifest.description },
                source = PluginManifest.Source.LOCAL,
            )

            override fun createProvider(): Provider = loaded.createProvider()

            override fun isEnabled(): Boolean =
                !UserPreferences.isPluginDisabled(manifest.id) && loaded.isEnabled()
        }
    }
}
