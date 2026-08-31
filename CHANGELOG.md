# Changelog

## Unreleased (Megaplan)

### Security & Stability
- Parental control PINs stored via EncryptedSharedPreferences with one-time migration
- Backup/data-extraction rules exclude secure and app preference files
- Provider `TODO`/`NotImplemented` paths return safe empty results instead of crashing
- Watch-progress math guarded against `durationMillis == 0`
- NavigationSlidePresenter state save/restore no longer throws
- `NetworkClient.trustAll` falls back to system trust in release builds
- Verbose token/URL logging gated behind debug

### Features & UX
- First-run legal disclaimer and post-update what's-new dialogs
- Search empty state and localized generic error messages
- Deep links (`betterstreamflix://`) handled via DeepLinkHandler
- Android TV Watch Next upsert on watch progress
- Continue Watching home-screen widget registered
- Anime `AudioVariant` (SUB/DUB) on servers (AniWorld)
- DownloadFeature entry API for offline downloads stack
- Local UserProfiles helper for household/kids profiles
- Provider health recording during global search
- TV theme preference unified to `SELECTED_THEME`

### Provider & Extractor Fixes
- Spanish TMDB `getServers()` no longer returns an empty list when no server has an explicit `[LAT]`/`[CAST]` tag; falls back to the untagged results, then to the global aggregators if no Spanish-specific provider found a match
- `Extractor.extract()` retries other host/name-matched extractors before failing, and raises a structured `ExtractionFailedException` (link + attempted extractor names) instead of a generic exception
- Fixed stale/mismatched domain defaults: Cuevana default now consistently `cuevana.gs` (Settings Mobile/TV previously hardcoded the dead `cuevana3.la`), SerienStream TV Settings default now matches the Mobile/provider default
- `StreamingCommunityProvider.baseUrl` now reflects the live (user-overridden) domain instead of a static hardcoded value
- AniWorld actor pages are now enriched with TMDB biography/photo when TMDB is enabled, matching SerienStream (previously always blank)
- Documented unregistered/WIP provider files (AfterDark, AnimeBum, AnyMovie, HiAnime, Otakufr, StreamingIta, SuperStream, UnJourUnFilm) in the provider registry

### Cleanup & Quality
- Removed unused Cast and main-source testing scaffolds
- Docs: SCAFFOLD_AUDIT, ARCHITECTURE, PROVIDERS, LEGAL, CONTRIBUTING
- Unit tests for DeepLinkHandler and ProviderHealthMonitor
- CI: unit tests hard-fail; critical i18n parity script
- Locale backfill for cloud_sync and critical megaplan strings

## v1.0.0

### Bug Fixes
- Fixed release build crash caused by ViewPager2 reflection (NoSuchFieldException: mRecyclerView)
- Replaced unsafe reflection with getChildAt approach
- Added ProGuard keep rules for AndroidX ViewPager2, RecyclerView, provider services, TMDb3 classes, extractors, and utilities
- Fixed compilation error in MovieMobileFragment and TvShowMobileFragment (submitList callback)

### Changes
- Redesigned provider selection page with gradient header, updated card layouts, search bar, and language chips
- Improved TMDB person search to prioritize exact name matches
- Episode images now use original quality from TMDB
- Added providers subtitle string (EN/DE/IT)
- Consolidated release workflow to produce a single signed APK
- Reset version numbering to 1.0.0

### Known Issues
- Actor profile images and biography info not yet displaying
- Episode and season images still showing generic series covers in some cases
