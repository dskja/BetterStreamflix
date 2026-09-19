package com.dskja.betterstreamflix.platform.plugins

import android.content.Context
import android.util.Log
import com.dskja.betterstreamflix.adapters.AppAdapter
import com.dskja.betterstreamflix.models.Category
import com.dskja.betterstreamflix.models.Movie
import com.dskja.betterstreamflix.models.TvShow
import com.dskja.betterstreamflix.models.Video
import com.dskja.betterstreamflix.providers.Provider
import com.dskja.betterstreamflix.utils.ProviderChangeNotifier
import com.dskja.betterstreamflix.utils.UserPreferences
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Central plugin lifecycle: bootstrap, reload (without dropping LOCAL APKs),
 * enable/disable, uninstall, install from staged APK, extension dispatch.
 */
object PluginManager {
    private const val TAG = "PluginManager"

    data class PluginStatus(
        val id: String,
        val name: String,
        val version: String,
        val source: PluginManifest.Source,
        val description: String,
        val enabled: Boolean,
        val loaded: Boolean,
        val active: Boolean,
        val capabilities: PluginManifest.Capabilities,
        val lastError: String = "",
        val apkPresent: Boolean = false,
        val extensions: List<String> = emptyList(),
        val author: String = "",
        val settingsSummary: String = "",
    )

    private val lastErrors = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val homeHooks = CopyOnWriteArrayList<HomeContributionPlugin>()
    private val searchHooks = CopyOnWriteArrayList<SearchContributionPlugin>()
    private val metadataHooks = CopyOnWriteArrayList<MetadataEnrichmentPlugin>()
    private val playbackHooks = CopyOnWriteArrayList<PlaybackHookPlugin>()
    private val extractorHooks = CopyOnWriteArrayList<ExtractorContributionPlugin>()
    private val subtitleHooks = CopyOnWriteArrayList<SubtitleContributionPlugin>()
    private val settingsHooks = CopyOnWriteArrayList<SettingsContributionPlugin>()

    @Volatile
    private var appContext: Context? = null

    fun start(context: Context) {
        appContext = context.applicationContext
        PluginRegistry.bootstrapBuiltins()
        DemoAddonPlugin.register()
        PluginCatalog.registerLocalStubs(context.applicationContext)
        val loads = PluginApkLoader.loadInstalled(context.applicationContext)
        loads.filter { !it.success }.forEach { lastErrors[it.pluginId] = it.message }
        loads.filter { it.success }.forEach { lastErrors.remove(it.pluginId) }
        refreshExtensionHooks()
        PluginEvents.record("system", "start", "plugins=${PluginRegistry.size}")
        Log.i(
            TAG,
            "Started plugins=${PluginRegistry.size} home=${homeHooks.size} " +
                "search=${searchHooks.size} playback=${playbackHooks.size}",
        )
    }

    /** Soft reload: keep builtins + LOCAL APKs; never wipe the registry first. */
    fun reload(context: Context = requireContext()): Int {
        PluginRegistry.ensureBuiltins()
        DemoAddonPlugin.register()
        PluginCatalog.registerLocalStubs(context)
        val loads = PluginApkLoader.loadInstalled(context)
        loads.forEach { result ->
            if (result.success) lastErrors.remove(result.pluginId)
            else lastErrors[result.pluginId] = result.message
        }
        refreshExtensionHooks()
        ProviderChangeNotifier.notifyProviderChanged()
        PluginEvents.record("system", "reload", "loaded=${loads.count { it.success }}")
        return loads.count { it.success }
    }

    fun setEnabled(pluginId: String, enabled: Boolean) {
        UserPreferences.setPluginDisabled(pluginId, disabled = !enabled)
        refreshExtensionHooks()
        ProviderChangeNotifier.notifyProviderChanged()
        PluginEvents.record(pluginId, if (enabled) "enabled" else "disabled")
        val current = UserPreferences.currentProvider
        if (current != null && !PluginRegistry.isProviderVisible(current)) {
            UserPreferences.currentProvider = PluginRegistry.enabledProviders().firstOrNull()
                ?: Provider.providers.keys.firstOrNull()
        }
    }

    fun isEnabled(pluginId: String): Boolean = !UserPreferences.isPluginDisabled(pluginId)

    fun uninstall(context: Context, pluginId: String): Boolean {
        val apk = PluginApkLoader.apkFileFor(context, pluginId)
        val deleted = if (apk.isFile) apk.delete() else true
        val plugin = PluginRegistry.get(pluginId)
        if (plugin != null && plugin.manifest.source == PluginManifest.Source.LOCAL) {
            runCatching {
                val name = plugin.createProvider().name
                Provider.unregisterDynamic(name)
            }
            runCatching { (plugin as? BetterStreamflixPlugin)?.onDetach() }
            PluginRegistry.unregister(pluginId)
        }
        val files = PluginCatalog.loadFromFiles(context).filterNot { it.id == pluginId }
        PluginCatalog.writeFilesCatalog(context, files)
        lastErrors.remove(pluginId)
        refreshExtensionHooks()
        ProviderChangeNotifier.notifyProviderChanged()
        PluginEvents.record(pluginId, "uninstalled")
        return deleted
    }

    fun installLocalApk(
        context: Context,
        sourceApk: File,
        entry: PluginCatalog.Entry,
    ): PluginApkLoader.LoadResult {
        val result = PluginApkLoader.installAndLoad(context, sourceApk, entry)
        if (result.success) {
            lastErrors.remove(entry.id)
            val merged = PluginCatalog.loadMerged(context).associateBy { it.id }.toMutableMap()
            merged[entry.id] = entry
            val filesOnly = merged.values.filter {
                it.source == PluginManifest.Source.LOCAL || it.source == PluginManifest.Source.REMOTE
            }
            PluginCatalog.writeFilesCatalog(context, filesOnly.toList())
            refreshExtensionHooks()
            ProviderChangeNotifier.notifyProviderChanged()
        } else {
            lastErrors[entry.id] = result.message
        }
        return result
    }

    fun statuses(context: Context = requireContext()): List<PluginStatus> {
        val catalog = PluginCatalog.loadMerged(context).associateBy { it.id }
        val registry = PluginRegistry.all()
        val ids = linkedSetOf<String>().apply {
            addAll(registry.map { it.manifest.id })
            addAll(catalog.keys)
        }
        return ids.map { id ->
            val plugin = PluginRegistry.get(id)
            val entry = catalog[id]
            val manifest = plugin?.manifest
            val apk = PluginApkLoader.apkFileFor(context, id)
            val caps = manifest?.capabilities ?: entry?.capabilities ?: PluginManifest.Capabilities()
            val settingsSummary = (plugin as? SettingsContributionPlugin)
                ?.takeIf { !UserPreferences.isPluginDisabled(id) }
                ?.settingsSummary()
                .orEmpty()
            PluginStatus(
                id = id,
                name = manifest?.name ?: entry?.name ?: id,
                version = manifest?.version ?: entry?.version ?: "?",
                source = manifest?.source ?: entry?.source ?: PluginManifest.Source.BUILTIN,
                description = manifest?.description ?: entry?.description.orEmpty(),
                enabled = isEnabled(id),
                loaded = plugin != null,
                active = plugin?.isEnabled() == true,
                capabilities = caps,
                lastError = lastErrors[id].orEmpty(),
                apkPresent = apk.isFile,
                extensions = collectExtensions(plugin, caps),
                author = manifest?.author ?: entry?.author.orEmpty(),
                settingsSummary = settingsSummary,
            )
        }.sortedWith(
            compareBy<PluginStatus> { it.source.ordinal }
                .thenBy { it.name.lowercase() },
        )
    }

    suspend fun collectHomeCategories(provider: Provider): List<Category> {
        if (homeHooks.isEmpty()) return emptyList()
        return homeHooks.flatMap { hook ->
            if (UserPreferences.isPluginDisabled(hook.pluginId)) return@flatMap emptyList()
            if (!contributes(hook, home = true)) return@flatMap emptyList()
            runCatching { hook.homeCategories(provider) }.getOrElse {
                Log.w(TAG, "Home hook ${hook.pluginId}: ${it.message}")
                emptyList()
            }
        }
    }

    suspend fun collectSearchResults(
        provider: Provider,
        query: String,
        page: Int = 1,
    ): List<AppAdapter.Item> {
        if (query.isBlank() || searchHooks.isEmpty()) return emptyList()
        return searchHooks.flatMap { hook ->
            if (UserPreferences.isPluginDisabled(hook.pluginId)) return@flatMap emptyList()
            if (!contributes(hook, search = true)) return@flatMap emptyList()
            runCatching { hook.search(provider, query, page) }.getOrElse {
                Log.w(TAG, "Search hook ${hook.pluginId}: ${it.message}")
                emptyList()
            }
        }
    }

    suspend fun enrichMovie(provider: Provider, movie: Movie): Movie {
        var current = movie
        metadataHooks.forEach { hook ->
            if (UserPreferences.isPluginDisabled(hook.pluginId)) return@forEach
            if (!contributes(hook, metadata = true)) return@forEach
            current = runCatching { hook.enrichMovie(provider, current) }.getOrDefault(current)
        }
        return current
    }

    suspend fun enrichTvShow(provider: Provider, tvShow: TvShow): TvShow {
        var current = tvShow
        metadataHooks.forEach { hook ->
            if (UserPreferences.isPluginDisabled(hook.pluginId)) return@forEach
            if (!contributes(hook, metadata = true)) return@forEach
            current = runCatching { hook.enrichTvShow(provider, current) }.getOrDefault(current)
        }
        return current
    }

    fun dispatchPlaybackStarted(videoType: Video.Type, serverName: String?) {
        playbackHooks.forEach { hook ->
            if (UserPreferences.isPluginDisabled(hook.pluginId)) return@forEach
            if (!contributes(hook, playback = true)) return@forEach
            runCatching { hook.onPlaybackStarted(videoType, serverName) }
            PluginEvents.record(hook.pluginId, "playback_started", serverName.orEmpty())
        }
    }

    fun dispatchPlaybackEnded(videoType: Video.Type) {
        playbackHooks.forEach { hook ->
            if (UserPreferences.isPluginDisabled(hook.pluginId)) return@forEach
            if (!contributes(hook, playback = true)) return@forEach
            runCatching { hook.onPlaybackEnded(videoType) }
            PluginEvents.record(hook.pluginId, "playback_ended")
        }
    }

    fun dispatchServerResolved(server: Video.Server) {
        playbackHooks.forEach { hook ->
            if (UserPreferences.isPluginDisabled(hook.pluginId)) return@forEach
            if (!contributes(hook, playback = true)) return@forEach
            runCatching { hook.onServerResolved(server) }
        }
    }

    fun dispatchPlaybackProgress(videoType: Video.Type, positionMs: Long, durationMs: Long) {
        if (durationMs <= 0L) return
        playbackHooks.forEach { hook ->
            if (UserPreferences.isPluginDisabled(hook.pluginId)) return@forEach
            if (!contributes(hook, playback = true)) return@forEach
            runCatching { hook.onPlaybackProgress(videoType, positionMs, durationMs) }
        }
    }

    fun extractorClaims(): List<String> =
        extractorHooks
            .filter { !UserPreferences.isPluginDisabled(it.pluginId) }
            .filter { contributes(it, extractors = true) }
            .flatMap { runCatching { it.extractorNames() }.getOrDefault(emptyList()) }

    fun subtitleClaims(): List<String> =
        subtitleHooks
            .filter { !UserPreferences.isPluginDisabled(it.pluginId) }
            .filter { contributes(it, subtitles = true) }
            .flatMap { hook ->
                runCatching {
                    hook.subtitleLanguages().map { "${hook.subtitleSourceLabel()}:$it" }
                }.getOrDefault(emptyList())
            }

    fun settingsActions(): List<Pair<String, PluginSettingsAction>> =
        settingsHooks
            .filter { !UserPreferences.isPluginDisabled(it.pluginId) }
            .filter { contributes(it, settings = true) }
            .flatMap { hook ->
                runCatching { hook.settingsActions().map { hook.pluginId to it } }
                    .getOrDefault(emptyList())
            }

    fun diagnostics(): String = buildString {
        appendLine("api=${PluginApi.VERSION} plugins=${PluginRegistry.size}")
        appendLine(
            "hooks home=${homeHooks.size} search=${searchHooks.size} " +
                "meta=${metadataHooks.size} playback=${playbackHooks.size} " +
                "extract=${extractorHooks.size} subs=${subtitleHooks.size}",
        )
        PluginEvents.recent(12).forEach { ev ->
            appendLine("${ev.kind} · ${ev.pluginId} ${ev.detail}".trim())
        }
    }

    private fun refreshExtensionHooks() {
        homeHooks.clear()
        searchHooks.clear()
        metadataHooks.clear()
        playbackHooks.clear()
        extractorHooks.clear()
        subtitleHooks.clear()
        settingsHooks.clear()
        PluginRegistry.all().forEach { plugin ->
            if (plugin is BetterStreamflixPlugin) {
                runCatching { plugin.onAttach(DefaultPluginHost) }
            }
            if (plugin is HomeContributionPlugin && contributes(plugin, home = true)) {
                homeHooks.add(plugin)
            }
            if (plugin is SearchContributionPlugin && contributes(plugin, search = true)) {
                searchHooks.add(plugin)
            }
            if (plugin is MetadataEnrichmentPlugin && contributes(plugin, metadata = true)) {
                metadataHooks.add(plugin)
            }
            if (plugin is PlaybackHookPlugin && contributes(plugin, playback = true)) {
                playbackHooks.add(plugin)
            }
            if (plugin is ExtractorContributionPlugin && contributes(plugin, extractors = true)) {
                extractorHooks.add(plugin)
            }
            if (plugin is SubtitleContributionPlugin && contributes(plugin, subtitles = true)) {
                subtitleHooks.add(plugin)
            }
            if (plugin is SettingsContributionPlugin && contributes(plugin, settings = true)) {
                settingsHooks.add(plugin)
            }
        }
    }

    private fun contributes(
        plugin: PluginExtension,
        home: Boolean = false,
        search: Boolean = false,
        metadata: Boolean = false,
        playback: Boolean = false,
        extractors: Boolean = false,
        subtitles: Boolean = false,
        settings: Boolean = false,
    ): Boolean {
        if (plugin is LoadedPluginFacade) {
            return when {
                home -> plugin.contributesHome()
                search -> plugin.contributesSearch()
                metadata -> plugin.contributesMetadata()
                playback -> plugin.contributesPlayback()
                extractors -> plugin.contributesExtractors()
                subtitles -> plugin.contributesSubtitles()
                settings -> plugin.contributesSettings()
                else -> true
            }
        }
        // Built-ins and direct implementors: the `is XxxPlugin` check at the call site is enough.
        // Still honor capability flags when present so stubs don't falsely advertise hooks.
        val caps = (plugin as? SourcePlugin)?.manifest?.capabilities ?: return true
        return when {
            home -> caps.homeContribution || plugin is HomeContributionPlugin
            search -> caps.searchContribution || plugin is SearchContributionPlugin
            metadata -> caps.metadataEnrichment || plugin is MetadataEnrichmentPlugin
            playback -> caps.playbackHooks || plugin is PlaybackHookPlugin
            extractors -> caps.extractors || plugin is ExtractorContributionPlugin
            subtitles -> caps.subtitles || plugin is SubtitleContributionPlugin
            settings -> caps.settings || plugin is SettingsContributionPlugin
            else -> true
        }
    }

    private fun collectExtensions(
        plugin: SourcePlugin?,
        caps: PluginManifest.Capabilities,
    ): List<String> {
        if (plugin is LoadedPluginFacade) {
            return buildList {
                if (plugin.contributesHome()) add("home")
                if (plugin.contributesSearch()) add("search")
                if (plugin.contributesMetadata()) add("metadata")
                if (plugin.contributesPlayback()) add("playback")
                if (plugin.contributesExtractors()) add("extractor")
                if (plugin.contributesSubtitles()) add("subtitles")
                if (plugin.contributesSettings()) add("settings")
                if (caps.live) add("live")
                if (caps.selfHosted) add("self-hosted")
                if (caps.movies || caps.tvShows) add("source")
            }.distinct()
        }
        return buildList {
            if (plugin is HomeContributionPlugin || caps.homeContribution) add("home")
            if (plugin is SearchContributionPlugin || caps.searchContribution) add("search")
            if (plugin is MetadataEnrichmentPlugin || caps.metadataEnrichment) add("metadata")
            if (plugin is PlaybackHookPlugin || caps.playbackHooks) add("playback")
            if (plugin is ExtractorContributionPlugin || caps.extractors) add("extractor")
            if (plugin is SubtitleContributionPlugin || caps.subtitles) add("subtitles")
            if (plugin is SettingsContributionPlugin || caps.settings) add("settings")
            if (caps.live) add("live")
            if (caps.selfHosted) add("self-hosted")
            if (caps.movies || caps.tvShows) add("source")
        }.distinct()
    }

    private fun requireContext(): Context =
        appContext ?: error("PluginManager not started")
}
