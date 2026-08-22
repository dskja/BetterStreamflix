# Changelog

All notable changes to BetterStreamflix will be documented here.

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
