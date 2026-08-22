# Changelog

All notable changes to BetterStreamflix will be documented here.

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
