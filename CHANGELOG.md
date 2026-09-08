# Changelog

## Unreleased — Flutter rewrite (v3)

### Flutter core (`bsflix/`)
- New **Flutter / Dart** app with Pulse design (Space Grotesk + Manrope, amber→orange CTAs)
- Peacock-style hero card, floating pill nav, glass provider chips
- Core screens: Home, Search, Detail, Player, Providers
- Demo catalog repository (live providers/extractors next)
- CI workflow **Build Flutter APK** uploads installable debug + release artifacts

## v2.0.0 — Arc

### Arc interface
- Complete mobile and TV visual redesign with the Arc design system
- New compact navigation chrome, content hierarchy, hero metadata and responsive poster grids
- Unified search, filters, empty/error states, downloads, profiles and settings surfaces
- Redesigned media details, long-press actions, next-episode overlay and player controls
- Consistent TV focus rings, Android dialogs, widgets, QR scanner and bypass tools
- Space Grotesk display typography, Manrope body typography and vermilion accent palette

### Release engineering
- Release tags must match the app version (`v2.0.0`)
- Signed APKs are now mandatory; unsigned artifacts can no longer be published
- Mobile and TV release builds run lint, unit tests and localization parity checks
- Published APKs include SHA-256 checksum files

### Bug fixes
- Fix Android TV playback crash on devices with buggy hardware decoders (Xiaomi TV P1, #41)
- Always enable ExoPlayer decoder fallback; auto-switch to software decoder when all servers fail
- Fix nightly CI startup failure: inline workflow steps instead of reusable workflow reference
- Fix all CI builds: bump minSdk from 21 to 23 (required by Room 2.8.4)
- Fix okhttp3.internal.userAgent unresolved reference in player fragments
- Remove obsolete dexOptions block (deprecated since AGP 8.0)
- Remove duplicate Room declarations in build.gradle

### Providers
- Add KinogerProvider (kinoger.to, German, movies + TV shows)

### Dependencies
- Kotlin 2.2.10, KSP 2.3.11
- Room 2.8.4 (requires minSdk 23)
- Ktor 3.5.2, Jsoup 1.23.1, Conscrypt 2.6.3, Java-WebSocket 1.6.0
- ConstraintLayout 2.2.2
- GitHub Actions: codeql-action v4, gradle/actions v6, softprops/action-gh-release v3

## Unreleased (Megaplan)

### Massive update wave 2
- Search: debounced queries, recent-search chips (mobile), history persistence
- Home: "Recommended for you" row from favorites/history; stale-cache banner (mobile + TV)
- Provider domain auto-failover on home load failure (SerienStream, StreamingCommunity, Cuevana, Moflix, Poseidon, …)
- Provider health badges on TV provider list
- Downloads screen (Settings → Downloads) with offline queue list
- Profile picker on startup when multiple local profiles exist
- New-content notifications via WorkManager (opt-in in Settings)
- Settings search dialog; TV AniWorld domain parity with mobile
- TV player sleep timer (long-press settings)
- Player empty-catch blocks now log warnings instead of swallowing silently
- CodeQL workflow; expanded unit tests (SearchHistory, ProviderDomainManager)
- i18n backfill for new strings across AR/DE/ES/FR/IT/PL

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
