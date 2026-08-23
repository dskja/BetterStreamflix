# Changelog

All notable changes to BetterStreamflix will be documented here.

## [Unreleased] - 2026-08-23

### Megaplan Tasks #301-400 — Architecture, UI, Data, Network, Images, Downloads, Cast, Widgets, Testing, Deployment

#### Tasks #301-310: Architecture & Code Quality
- **EventBus** — In-process event bus using SharedFlow with typed AppEvent sealed class
- **DependencyContainer** — Service locator with singleton and factory registration
- **FeatureFlags** — Runtime feature toggles with overrides and persistence
- **StateMachine** — Generic state machine with validated transitions and fluent builder
- **OperationResult** — Unified result type (Success/Failure/Loading) with mapping and chaining
- **BaseViewModel** — ViewModel base with StateFlow, coroutine binding, debounced updates
- **BaseRepository** — Repository interface with local/remote sources and CachedRepository wrapper
- **Mapper** — Bidirectional/one-way mapper interfaces with SafeMapper and CompositeMapper
- **EffectHandler** — One-shot side effects via SharedFlow for navigation/toasts/snackbars
- **UseCase** — UseCase base classes with input validation and safe coroutine execution
- **ConfigurationManager** — Centralized config with defaults, overrides, and persistence

#### Tasks #311-320: UI/UX Polish & Animations
- **AnimationHelper** — Fade, slide, scale, shake, pulse animations with consistent durations
- **RecyclerViewAnimationHelper** — Custom item animations and scroll effects for RecyclerViews
- **MaterialColorHelper** — Material Design 3 color schemes, ripple backgrounds, contrast calculation
- **TransitionHelper** — Shared element and scene transitions with combined presets
- **TypographyHelper** — Material Design typography scale application for TextViews
- **SpacingHelper** — Consistent dp/px spacing values, padding, and margin helpers
- **TooltipHelper** — Rich tooltips with title, message, icon, and position control
- **ShimmerHelper** — Shimmer loading placeholders (fixed duplicate AnimationHelper class)

#### Tasks #321-330: Data Layer & Database
- **DatabaseMigrations** — Centralized Room migrations (v1→v6) for all new tables
- **SearchHistoryDao** — Room DAO for search history with Flow queries and partial matching
- **PlaybackPositionDao** — Room DAO for playback positions with upsert and recent queries
- **DatabaseSeeder** — Initial data seeding for providers and collections on first install
- **PaginationState** — Reactive pagination with page tracking, loading states, has-more detection
- **DataSyncCoordinator** — Local/remote sync coordination with conflict resolution
- **EntityConverter** — Converts between Room entities and domain models
- **CachePolicyManager** — Per-type cache policies with TTL, max entries, and disk size limits
- **DatabaseHealthMonitor** — Database file/WAL size monitoring with health warnings

#### Tasks #331-340: Network Layer Enhancement
- **HeaderInterceptor** — Standard headers (User-Agent, Accept, etc.) with custom header support
- **RetryInterceptor** — Exponential backoff retry with jitter for failed requests
- **LoggingInterceptor** — Debug request/response logging with timing and optional body logging
- **CacheControlInterceptor** — Cache control headers for offline support and conditional requests
- **NetworkRequestQueue** — Concurrent request limiter with priority tracking and duration stats
- **HttpClientBuilder** — Preset OkHttp configurations (default, fast, patient) with interceptor support
- **NetworkCapabilityChecker** — Network type detection, bandwidth estimation, unmetered check
- **TimeoutInterceptor** — Dynamic timeout adjustment based on request type (streaming/API/default)
- **TlsManager** — Certificate pinning, TLS version recommendations, Android version-aware support
- **NetworkErrorHandler** — Error classification (DNS/timeout/SSL/etc.) with user messages and retryability

#### Tasks #341-350: Image Loading & Caching
- **ImageCacheManager** — Two-tier cache (memory LRU + disk) with size-based eviction and trimming
- **ImageProcessor** — Resize, crop-to-ratio, circular, rounded, color overlay, placeholder generation
- **ImageLoader** — Coroutine-based image loading with caching, placeholders, and transformations
- **ImagePrefetchManager** — Prefetch queue with deduplication and immediate prefetch mode
- **ImagePlaceholderGenerator** — Deterministic colored placeholders with initials and gradients
- **ImageMemoryCache** — Standalone LRU memory cache with hit/miss tracking
- **ImageDiskCache** — SHA-256 keyed disk cache with LRU eviction and WEBP compression
- **ImageTransitionHelper** — Crossfade, fade-in, fade-out transitions for ImageViews
- **ImageStorageHelper** — App-private storage for posters, backdrops, thumbnails with WEBP compression

#### Tasks #351-360: Download Manager
- **DownloadQueueManager** — Priority-based queue with pause/resume and concurrent download limits
- **DownloadStorageChecker** — Available storage checking with low/critical storage detection
- **DownloadResumeManager** — Resume point persistence with ETag/Last-Modified and 7-day expiry
- **DownloadPolicyManager** — WiFi-only downloads, battery level checks, charging detection
- **DownloadFileManager** — Download file path management, temp-to-final finalization, orphan cleanup
- **DownloadProgressTracker** — Reactive progress tracking with speed/ETA/percent calculations
- **DownloadHistoryManager** — Completed download history with JSON persistence and stats
- **DownloadScheduler** — Intelligent scheduling based on network, charging, and battery level
- **DownloadIntegrityChecker** — SHA-256/MD5 checksums, file size validation, video magic byte checking

#### Tasks #361-370: Cast & External Display
- **CastManager** — Unified casting interface (Chromecast/DLNA/AirPlay/Miracast) with device management
- **CastDeviceDiscoverer** — Network device discovery with local IP detection and same-network validation
- **CastSessionManager** — Cast session lifecycle with playback position, volume, and state tracking
- **CastMediaController** — Media control API (play/pause/seek/volume/mute/next/previous/stop)
- **ExternalDisplayManager** — External display detection, display modes, optimal resolution, screen-on
- **CastRouteSelector** — Media route selection with registration, availability filtering, and management
- **CastPreferences** — Auto-connect, last device, cast quality, and notification preferences
- **CastVolumeController** — Volume management with smooth ramping, mute/unmute, and percentage display
- **CastStateListener** — Callback-based cast state change notification system
- **CastErrorHandler** — Cast error classification with user messages, retryability, and suggested actions

#### Tasks #371-380: Widgets & Quick Actions
- **ContinueWatchingWidget** — AppWidgetProvider for recently watched content on home screen
- **FavoritesWidget** — AppWidgetProvider for favorite content on home screen
- **QuickActionsHelper** — Launcher long-press shortcuts (Continue Watching, Search, Favorites, Downloads)
- **WidgetConfigurationHelper** — Per-widget config (content type, refresh interval, max items, poster)
- **WidgetUpdateScheduler** — Widget update scheduling with active widget detection
- **WidgetDataProvider** — Data provider for widget display with RemoteViews population
- **WidgetStateManager** — Widget state tracking with staleness detection and needs-update flags
- **WidgetIntentHelper** — Widget intent creation/parsing for content, search, favorites, downloads, settings
- **WidgetCompatibilityChecker** — Widget feature compatibility detection by Android API level
- **WidgetRegistry** — Widget registration, discovery, and active widget ID tracking

#### Tasks #381-390: Testing & QA
- **TestFixtures** — Test data factories for content items, search results, playback states
- **MockDataProvider** — Mock data for trending, favorites, continue watching, and search results
- **TestAssertions** — Custom assertions (notEmpty, size, notBlank, inRange, sameElements, eventually)
- **TestUtils** — Coroutine/Flow testing, random data generation, execution time measurement
- **QaConfiguration** — QA mode with mock data, slow network, error injection rate, log level
- **PerformanceBenchmark** — Benchmarking with iteration tracking, min/max/average times
- **ErrorInjector** — Error injection with per-operation and global rates, and count tracking
- **TestScenarioRunner** — Test scenario execution with step results, pass rate, and formatted output
- **TestCoverageTracker** — Code path coverage tracking per module with uncovered path listing
- **TestReportGenerator** — Test reports in text and JSON formats with full result aggregation

#### Tasks #391-400: Deployment & CI/CD
- **VersionManager** — Version info, semantic version parsing, comparison, and update checking
- **BuildConfiguration** — Build type detection (debug/release/beta) and BuildConfig field access
- **ReleaseNotesGenerator** — Release notes in text and markdown from changelog entries
- **CiCdConfiguration** — CI environment detection, branch/commit/build extraction, tag detection
- **UpdateChecker** — App update checking with version comparison and download URL generation
- **FeatureGate** — Feature gating by build type, min version, and rollout percentage
- **BuildVariantManager** — Build variant config with minification, debuggability, APK/AAB naming
- **DeploymentValidator** — Pre-deployment validation with error/warning reporting
- **SigningConfiguration** — APK/AAB signing from env vars with V1/V2/V3 signing and validation
- **ReleasePipeline** — Pipeline step management with progress tracking and status reports

#### Fixed
- Removed duplicate `AnimationHelper` class from `ShimmerHelper.kt` (conflicted with standalone file)
- Changed Room annotation processor from `kapt` to `ksp` for KSP compatibility

#### New Packages
- `accessibility/` — Accessibility helpers (TalkBack, font scale, reduced motion, TV focus)
- `analytics/` — Analytics and diagnostics (event tracking, device info, crash handling, bug reports)
- `architecture/` — Architecture foundations (EventBus, DI, state machines, base classes)
- `cast/` — Cast and external display management
- `deployment/` — Deployment and CI/CD utilities
- `imageloading/` — Image loading, caching, and processing
- `testing/` — Testing utilities, fixtures, and QA configuration
- `widgets/` — Home screen widgets and quick actions

---

## [1.9.0] - 2026-08-23

### Mega Update — Cloud Sync Audit, Provider Redesign, Release Signing, Database Integrity, Cache Reliability, Updater Overhaul

#### Build & Release
- **Fixed missing `import android.util.Log` in `InAppUpdater.kt`** — this was the root cause of the GitHub Actions build failure. New `Log.e()` call in the `catch` block of `downloadApk()` used the short form but the import was missing (existing code used fully-qualified `android.util.Log.e()`).
- **Added release APK signing** — `signingConfigs.release` block in `build.gradle` reads keystore path and credentials from environment variables (`SIGNING_KEYSTORE_PATH`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`).
- **Updated GitHub Actions workflow** to build both debug AND release APKs.
- **Added keystore decoding step** in workflow — decodes Base64 secret to `.jks` file and sets env vars for Gradle signing.
- **Fixed workflow `secrets` context error** — `secrets` cannot be used in `if:` conditionals; moved to `env:` block with shell-level check.
- **Added GitHub Release creation step** — automatically creates a release tagged `v1.9.0` with proper title "BetterStreamflix v1.9.0", changelog from `CHANGELOG.md`, and both APKs attached.
- **Release deletes existing tag first** if present, preventing duplicate or stale releases.

#### Provider Page Redesign
- **Complete UI overhaul of provider selection screen** for both Mobile and TV layouts.
- **Added search bar** — filter providers by name in real-time with `TextWatcher`.
- **Added language filter chips** — horizontal scrollable chips to filter providers by language (e.g., EN, DE, IT, ES, FR, etc.).
- **Added favorite star toggle** — click the star icon on any provider card to pin it as a favorite; favorites persist across sessions via `UserPreferences`.
- **Premium card design** — provider cards now show logo in a rounded container, provider name, language label, and clickable favorite star.
- **New drawables**: `bg_provider_card.xml`, `bg_provider_card_tv.xml` (focused/default states for TV), `bg_provider_logo_container.xml`, `bg_provider_search_bar.xml`, `bg_provider_chip.xml` (selected/default states), `bg_provider_chip_tv.xml` (focused/selected/default states).
- **New layouts**: `item_provider_chip.xml`, `item_provider_chip_tv.xml` for language filter chips.
- **Redesigned layouts**: `fragment_providers_mobile.xml` (GridLayoutManager), `fragment_providers_tv.xml` (LinearLayoutManager horizontal), `item_provider_mobile.xml`, `item_provider_tv.xml`.
- **Refactored `ProvidersViewModel`** — added `searchQuery` and `languageFilter` state, prevents double-load bug, applies search + language + favorites filters, toggles favorites with persistence.
- **Rewrote `ProvidersMobileFragment`** — search bar with `TextWatcher`, language chip `RecyclerView`, providers `RecyclerView`, lifecycle-aware state collection, proper cleanup on destroy.
- **Rewrote `ProvidersTvFragment`** — same as mobile but with TV-optimized navigation, `LinearLayoutManager` horizontal for chips and providers, safe `requestFocus` handling.
- **Refactored `ProviderViewHolder`** — supports both mobile and TV layouts, clickable favorite star with icon toggle, logo loading with Glide (supports SVG via `androidsvg-aar`), provider selection callback.
- **Added `Provider.copy()` method** — creates modified copies with updated `itemType` and other properties for adapter use.

#### Cloud Sync Audit (12 Fixes)
- **Added `CloudSyncManager.deleteRemoteState()`** — deletes media items from local database based on `RemoteMediaState` (movie/tv_show/episode), updates `UserDataCache`, and notifies listeners.
- **Added `CloudSyncManager.deleteRealtimeState()`** — mutex-locked entry point for realtime delete events, checks user ID match before proceeding.
- **`CloudRealtimeSync` now handles `PostgresAction.Delete`** — previously only Insert/Update were handled; Delete events now trigger `deleteRealtimeState()` instead of `applyRealtimeState()`.
- **Added realtime reconnect logic with exponential backoff** — on disconnect, retries 5 times with 2s/4s/8s/16s/32s backoff. Checks `activeUserId` before each retry to avoid reconnecting for logged-out users.
- **`CloudSyncManager.upsert()` now returns uploaded states** — returns `List<RemoteMediaState>` of successfully uploaded items so `flushPending()` can acknowledge only what was actually sent.
- **`CloudSyncManager.flushPending()` acknowledges only uploaded + stale mutations** — previously acknowledged all pending regardless of upload success. Now: `successfullyUploaded + pending.filter { it !in uploadable }`.
- **Added max iteration limit (10) to `flushPending()`** — prevents infinite loop if mutations keep getting re-queued. Logs warning when limit is hit.
- **`CloudSyncManager.signOut()` now handles failures gracefully** — `flushPending` failures are logged, `auth.signOut()` wrapped in `runCatching`, mutation store cleared for user, periodic sync cancelled.
- **`CloudSyncManager.syncNow()` wrapped in try-catch** — errors are logged with user ID before rethrowing, aiding diagnosis.
- **`CloudSyncManager.activateAccount/signIn/signUp` now schedule periodic sync** — `CloudSyncScheduler.schedulePeriodic()` called after realtime sync starts.
- **`CloudSyncManager.applyRemote()` cache writes wrapped in `runCatching`** — failures logged with provider name instead of crashing the entire sync.
- **`CloudSyncScheduler` — added `schedulePeriodic()` and `cancelPeriodic()`** — 15-minute periodic `WorkManager` sync with network constraint. Uses `ExistingPeriodicWorkPolicy.KEEP`.
- **`CloudSyncScheduler.enqueue()` changed from `KEEP` to `REPLACE`** — ensures fresh one-time sync isn't blocked by stale queued work.
- **`CloudMutationStore` — added `clearForUser()`** — removes all mutations for a specific user ID (or all if null). Used during sign-out.
- **`CloudMutationStore` — changed `apply()` to `commit()`** — synchronous SharedPreferences write ensures mutations are persisted before process termination.
- **`CloudSyncWorker` — changed `catch (Throwable)` to `catch (Exception)`** with `Log.e()` — avoids catching `CancellationException` which should propagate, adds error logging for retry diagnosis.

#### Critical Database Fixes
- **Fixed merge direction bug in `TvShowDao.save()` and `MovieDao.save()`** — root cause of user state (isWatching, isFavorite, isWatched) being silently overwritten by stale DB values on every save. Changed `tvShow.merge(existing)` → `existing.merge(tvShow)` so new user state is applied to the existing DB record, not the other way around.
- **Added `MovieDao.deleteById(id)`** — direct SQL delete for cloud sync delete propagation.
- **Added `TvShowDao.deleteById(id)`** — direct SQL delete for cloud sync delete propagation.
- **Added `EpisodeDao.deleteById(id)`** — direct SQL delete for cloud sync delete propagation.
- **Added `EpisodeDao.clearWatchHistory(id)`** — direct SQL method to NULL `lastEngagementTimeUtcMillis`, `lastPlaybackPositionMillis`, `durationMillis` for a single episode, bypassing `@Update` which doesn't reliably clear `@Embedded` null fields.
- **Added `EpisodeDao.clearWatchHistoryForTvShow(tvShowId)`** — bulk version that clears watch history for all episodes of a TV show at once.
- **Fixed `UserDataCache.removeEpisodeFromContinueWatching()`** — now calls `clearWatchHistory(id)` on the DB before removing from cache. Previously only removed from JSON cache, causing episodes to reappear on next `loadUserDataCache` rebuild from DB.
- **Fixed `UserDataCache.removeMovieFromContinueWatching()`** — replaced silent `runCatching` with proper try-catch and error logging.
- **Added `UserDataCache.clearAllContinueWatching()`** — clears all continue watching state from both cache and DB (episodes, movies, TV shows) in one call.

#### Continue Watching UI Fixes
- **Simplified Clear button handlers** in `ShowOptionsMobileDialog` and `ShowOptionsTvDialog` — removed redundant `save()` + `syncEpisodeToCache()` calls that were fighting each other. Now just `setWatching(false)` + `removeEpisodeFromContinueWatching()` (which handles both DB + cache atomically).

#### In-App Updater Overhaul
- **Fixed missing `import android.util.Log`** — root cause of GitHub Actions build failure.
- **Fixed temp file location** — APK downloads now go to `context.cacheDir` instead of system temp, ensuring `FileProvider` can access them for installation.
- **Added download progress callback** — `downloadApk()` now accepts `onProgress: (Float) -> Unit` so UI can show a progress bar.
- **Added proper error handling** — download failures are logged, temp file is cleaned up, and exception is rethrown for caller to handle.
- **Added 30s connect/read timeouts** to prevent hanging on slow connections.
- **Cleaned up redundant fully-qualified `android.util.Log.e()`** to use short form `Log.e()` now that import is present.

#### Home Cache Improvements
- **Added 6-hour TTL to `HomeCacheStore`** — cached home data expires after 6 hours, ensuring users see fresh content without manual cache clearing. Both memory and disk cache respect the TTL.
- **Memory cache now stores timestamp alongside data** — `ConcurrentHashMap<String, Pair<List<CachedCategory>, Long>>` for TTL checks.
- **Disk cache checks file age** via `file.lastModified()` against TTL.
- **Serve stale cache on provider failure** — when `getHome()` fails but cached data exists, the app now serves the stale cache with a warning log instead of showing an error screen. Previously only worked for AnimeOnlineNinja clearance issues.
- **Added `deferCachedHomeForClearance` logic** — skips serving cached home for AnimeOnlineNinja when no clearance cookie is present, ensuring fresh fetch for auth-gated content.

#### Thread Safety
- **Fixed `PlayerViewModel` thread-safety** — marked `lastVideoType` and `lastId` as `@Volatile` to ensure visibility across coroutine threads when `reloadServersAfterBypass()` is called.

#### Error Handling
- **Replaced silent `runCatching` with logged try-catch** in `UserDataCache` DB operations — failures now produce `Log.e()` output so issues can be diagnosed instead of silently swallowed.

#### Crash Audit & Null Safety (v1.9.0 re-release)

##### Unsafe Casts Fixed
- Replaced unsafe `as` casts with safe `as?` casts and null checks in NavHostFragment, RecyclerView, AppAdapter, ECPublicKey, and Response objects
- Fixed TmdbClient: safe cast for `HttpsURLConnection` to handle non-HTTPS URLs

##### Forced Unwraps (`!!`) Removed
- Removed all unsafe `!!` force unwraps causing NullPointerException crashes across the app

##### Unsafe Collection Access Fixed
- Replaced `.first()` with `firstOrNull()` + null checks in extractors (FrembedExtractor, PrimeSrcExtractor, VidplayExtractor, VidzeeExtractor, FilemoonExtractor, VeevExtractor), providers (VavooProvider), and ViewModels (TvShowViewModel, HomeViewModel)
- Replaced `.last()` with `lastOrNull()` + null checks in extractors and helpers
- Replaced unsafe `[0]` index access with `getOrNull(0)` in FilemoonExtractor (key_parts), VidsrcToExtractor (encrypt/decrypt keys), VeevExtractor (etext), RabbitstreamExtractor (rawKeys)
- Replaced unsafe split-and-index patterns (`split("x")[1].split("y")[0]`) with `getOrNull()` chains in MStreamDayExtractor (3 locations), VidGuardExtractor (sigDecode)
- Fixed ContentStatistics: safe `toIntOrNull()` for date parsing to prevent `NumberFormatException`
- Fixed ContentDeduplicator: safe `firstOrNull()` with descriptive exception

##### JSON Parsing Safety
- Wrapped `JSONObject(body)` in `runCatching` in AnimeOnlineNinjaProvider, FilmyOnlineCcProvider
- Replaced unsafe `getJSONObject(0)` with `optJSONObject(0)` + null checks in FilemoonExtractor, VeevExtractor, VideasyExtractor, VidzeeExtractor, VidLinkExtractor, OnRegardeOuExtractor, TmdbClient
- Replaced unsafe `getJSONArray()` with `optJSONArray()` + null checks in OnRegardeOuExtractor

##### CancellationException Handling
- Added `CancellationException` rethrow to suspend function catch blocks in UseCase (both safeExecute methods), DataSyncCoordinator, PaginationState, MoflixExtractor, MyFileStorageExtractor, PDrainExtractor, StreamixExtractor, StreamUpExtractor, VidaraExtractor, VidGuardExtractor, BackupRestoreManager

##### UserPreferences Safety
- Fixed `providerUrl` to use dedicated key instead of `PROVIDER_CACHE` (was overwriting entire provider cache)
- Added `::prefs.isInitialized` checks to all `Key` enum getter/setter methods
- Added `::providerCache.isInitialized` checks for safe access

## [1.8.3] - 2026-08-22

### SerienStream Selector Fixes (Verified Against Live Site)
- Fixed empty Featured/Hero category — selector `a.home-hero-cta` changed to `a.home-hero-overlay` (site restructured, CTA is now a `<span>` inside `<a>`)
- Replaced removed "Derzeit beliebte Serien" carousel with new tabbed trending sections
- Added "Gerade im Trend" category from `#section-1 .card-mini-tile`
- Added "Wöchentliche Favoriten" category from `#section-2 .card-mini-tile`
- Fixed cast/director/producer ID extraction — `pathSegments()` now filters `schauspieler`, `regisseur`, `produzent`, `land`, `jahr` in addition to `serie`
- Added crash guard in `getEpisodesBySeason()` for malformed season IDs (IndexOutOfBoundsException)
- Added discover block title fallback chain: `span.h6` → `span.fw-semibold` → `a` text
- Removed stale to-dos from README (Continue Watching + SerienStream Fix already resolved in v1.8.2)

## [1.8.2] - 2026-08-22

### Continue Watching Bug Fix
- Fixed "Aus Weiterschauen entfernen" (Remove from Continue Watching) doing nothing on long-press
- Root cause: `TvShowDao.save()` merge direction overwrote new `isWatching` value with old DB value
- Added direct SQL `setWatching()` method to `TvShowDao` bypassing broken merge logic
- Replaced all `tvShowDao().save()` calls in `ShowOptionsMobileDialog` and `ShowOptionsTvDialog` with `setWatching()`
- Fixes apply to all 3 actions: clear program, mark watched/unwatched, mark all previous watched

### SerienStream Massive Update
- Per-category try-catch in `getHome()` — one broken selector no longer kills all categories
- Fallback selectors for popular carousel when primary selector returns empty
- Fallback hero image to `img[src]` when srcset parsing fails
- Search title fallback chain: `h6.show-title` → `h6` → `.show-title`
- Episode link extraction fallback: `onclick` → `a[href]`
- Episode title fallback chain: German title → English title → 2nd column text → "Episode N"
- `getServers()` now catches page-load errors gracefully with logging instead of crashing
- Warning logs when 0 servers found or 0 search results — helps diagnose selector issues faster
- All empty results filtered out to prevent blank items in UI
- Comprehensive error logging throughout all provider methods

### Release Automation
- Release descriptions now auto-generated from `CHANGELOG.md` matching version tag
- Uses `body_path` in `softprops/action-gh-release@v2` for proper multiline release notes

## [1.8.1] - 2026-08-22

### SerienStream URL Fix
- Fixed 404 errors on all SerienStream links — site changed URL structure
- Updated Retrofit endpoints from `serie/{show}/{season}` to `serie/{show}/staffel-{season}`
- Updated episode endpoints from `serie/{show}/{season}/{episode}` to `serie/{show}/staffel-{season}/episode-{episode}`
- Season and episode IDs are now correctly extracted from new URL format

## [1.8.0] - 2026-08-22

### Security
- Replaced all trust-all SSL implementations with system default X509TrustManager
- Fixed insecure OkHttp clients in SerienStream, AniWorld, and TMDb providers

### Bug Fixes (15 from Audit)
- Fixed all unsafe `!!` force unwraps causing NullPointerException crashes
- Fixed unsafe `toInt()` calls in TmdbProvider causing NumberFormatException
- Fixed unsafe destructuring in `getEpisodesBySeason` preventing IndexOutOfBoundsException
- Fixed null pointer in PlayerMobileFragment during video display
- Fixed null pointer in PlayerViewModel during server loading
- Fixed null pointer in InAppUpdater version comparison
- Fixed null pointer in TmdbProvider during people details fetch
- Fixed null pointer in SettingsMobileFragment preference binding
- Fixed Supabase client unsafe initialization

### SerienStream Fix
- Updated SerienStream default URL to `http://186.2.175.5/`
- Stabilized SerienStream integration with proper HTTP scheme handling
- Provider now correctly uses configurable domain from UserPreferences

### AniWorld Fix
- Made AniWorld base URL configurable via UserPreferences
- Added AniWorld domain settings UI (mobile + TV)
- AniWorld provider now uses system default trust manager for SSL validation
- Provider domain changes trigger cache clearing

### Login & Registrierungs Fix
- Added robust email validation for login/registration
- Added password strength validation (minimum length, complexity)
- Improved error messages for Supabase RestException responses
- User-friendly error handling for all auth failure scenarios

### Token Fix
- Fixed race condition in Supabase authentication session initialization
- Ensured `awaitInitialization()` is called before session usage
- Token injection now properly handles null query scenarios

### API Fix
- Added 30s connect and read timeouts to TMDb OkHttpClient to prevent hanging
- Fixed all unsafe `toInt()` calls in TmdbProvider with `toIntOrNull()` fallbacks
- Improved error handling for invalid API responses

### Qualitäts Fix
- Saved video quality preference (`qualityHeight`) is now applied at player initialization
- ExoPlayer `setMaxVideoSize` constraint applied for adaptive streams (HLS/DASH)
- Quality selection in player settings now properly clears max size when "Auto" is selected
- Quality preference persists across playback sessions and applies on both Mobile and TV

### Einstellungen Erweiterung
- Added AniWorld domain configuration in Settings (Mobile + TV)
- Added default video quality selector in Playback Settings (Auto/360p/480p/720p/1080p/1440p/2160p)
- AniWorld settings category visibility is context-aware (shown only when AniWorld provider is active)
- Quality preference syncs between main Settings and in-player quality selector

## [1.7.231] - 2026-08-22

### Fork / Rebrand
- Forked from Streamflix Reborn as `BetterStreamflix`
- Renamed package to `com.betterstreamflix`
- Updated app name, README and project metadata

### CI / Automation
- Added GitHub Actions `build.yml` for automated debug builds on push/PR
- Added `dependabot.yml` for automated dependency and action updates
- Updated `release.yml` to include `SUBDL_API_KEY` and modern checkout/java actions

### Fixes / Updates
- In-app update checker now points to `dskja/BetterStreamflix`
- Help / Telegram links in settings now point to new BetterStreamflix community
- Added `local.properties.example` with required build keys

## [Unreleased]

- Settings UI for "Clear All Continue Watching"
- Per-show "Clear All Episodes" using clearWatchHistoryForTvShow
- Cloud-sync enhancements for watch state
- Additional provider improvements
