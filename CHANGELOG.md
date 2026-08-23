# Changelog

## [1.9.0] - 2026-08-23

### What's New in v1.9.0

**Cloud Sync, Provider UI, Crash Resilience**

- New provider selection UI with search, language chips, and favorites
- Cloud sync with Supabase (watch history, favorites, continue watching)
- In-app updater with download progress and better error handling
- 6-hour TTL for home cache and stale-cache fallback on provider failure

### Crash & Stability Fixes

- Fixed compilation errors in PlayerMobileFragment, PlayerTvFragment, and TvShowViewModel
- Fixed release build signing when no keystore is configured
- Removed unsafe !! force unwraps across the app
- Replaced unsafe .first(), .last(), and [0] access with null-safe alternatives
- Replaced unsafe JSON getXxx() calls with optXxx() and null checks
- Added CancellationException rethrow in suspend function catch blocks
- Fixed UserPreferences providerUrl key and late-init safety
- Safe casts (as?) with null checks for HttpsURLConnection, NavHostFragment, RecyclerView, etc.

