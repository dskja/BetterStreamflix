# Changelog

All notable changes to BetterStreamflix will be documented here.

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

- Additional provider improvements
- More extractor fixes
- Cloud-sync enhancements
