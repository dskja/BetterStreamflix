# Changelog

All notable changes to BetterStreamflix are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Re-enabled five previously unregistered providers whose sites are live again: StreamingIta (it), AnyMovie (en), HiAnime (en), 1Jour1Film (fr), AfterDark (fr)
- Experimental mobile design rebuilt as "Lumina": cinematic dark shell (near-black `#0B0B10` canvas, champagne-gold × violet accents, Fraunces display type) covering the entire mobile surface — home, details, seasons/episodes, genres, people, downloads, player, dialogs and loading states
- Poster-derived ambient glow behind home hero and detail posters (Palette API), floating glass nav pill, shimmer skeleton loading, hero scroll parallax and press-scale card feedback (still off-by-default behind the experimental toggle)

### Changed
- StreamingCommunity default domain `streamingunity.cc` → `streamingunity.win`; stale stored domains auto-migrate
- Cuevana 3 default domain `cuevana.gs` → `cuevana3.gs`; stale stored domains auto-migrate
- Wiflix default `flemmix.team` → `neufneuf.space`
- CB01 default `cb01official.uno` → `cb01uno.homes`
- CineCalidad default `cinecalidad.ec` → `cinecalidad.am`
- SeriesFlix default `seriesflixhd.lol` → `seriesflixhd.team`
- Series Turcas default `tbg.seriesturcastv.to` → `kaj.seriesturcastv.to`
- Anime Online Ninja `ww3.` → `ver.animeonline.ninja`
- AfterDark `afterdark.best` → `afterdark.rest`; portal now reads the topsitestreaming.club index (new `url:` field)
- Artwork repair matches all `animeonline.ninja` hosts, not only `ww3.`

### Fixed
- Frembed URL discovery: dead portal `audin213.com` (spam redirect) replaced by `frembed.casa`; portal redirects now resolve the live domain directly before DOM parsing
- Settings screens showed outdated default domains for StreamingCommunity (`cuevana3.la` typo included) and Cuevana 3

## [1.1.0] - 2026-09-09

Complete BetterStreamflix maintainer changelog since the upstream sync with
[streamflix-reborn2/streamflix](https://github.com/streamflix-reborn2/streamflix)
(**v1.7.231**). The **v1.0.0** release notes below stay unchanged; this section
covers everything done on top of that upstream baseline.

### Added
- Offline downloads (Media3 HLS/MP4): queue, Downloads tab (mobile + TV), server + quality options dialog, season batch, Wi‑Fi-only default, concurrency / soft storage limit, notifications, connectivity auto-pause, offline playback via `__offline__` server (`#86`)
- Buy Me a Coffee link in Settings / About: https://buymeacoffee.com/betterstreamflix
- PR CI workflow (`pr-ci.yml`): duplicate-string check, unit tests, `assembleDebug`
- Release workflow builds three APKs like upstream: `BetterStreamflix.apk`, `BetterStreamflix-only-Mobile.apk`, `BetterStreamflix-only-TV.apk`
- Shared player core: `PlayerBuilderFactory`, `PlaybackFailover`, `SerienStreamBypassHelper`
- Local `CrashReporter` + Settings “View last crash log”
- Provider quarantine list + smoke harness (`ProviderHealth`, `ProviderSmoke`)
- Home `getHome` timeout with stale-while-revalidate (`HomeCacheStore`)
- Official branding under `branding/` + new adaptive launcher / TV banner icons
- GuardaFlix in-app login / register for full streams
- Configurable provider URL in Settings for almost all providers
- Live TV player UX (LIVE badge, no seek/skip-intro, no VOD resume for live)
- TMDb missing-API-key hint
- Manage Recently Watched / Continue Watching on Home (`#18`)
- Optional cross-provider favorites and continue watching (`#10`)
- Local cache for OpenSubtitles / SubDL subtitle files (`#14`)
- Subtitle timing offset control (`#22`)
- Unit tests for playback failover, SerienStream bypass helper, provider health

### Changed
- App version set to **1.1.0** (`versionCode` 10100)
- Public rebrand to **BetterStreamflix** (maintainer **dskja**): app name, deep links `betterstreamflix://`, in-app updater repo, Telegram `@BetterStreamflix`, About / README credits (`#77`)
- Mass-rename package / namespace to `com.dskja.betterstreamflix` (`StreamFlixApp` → `BetterStreamflixApp`), BS / brand icons (`#78`)
- SerienStream default domain: dead `s.to` → `serienstream.to` ([serien.domains](https://serien.domains))
- SerienStream bypass Continue gated on clearance cookies (or resolved hoster URL); TV captcha QR usable from phones (`#32`)
- TV/mobile playback: decoder fallback + software-decoder retry; safer mid-play failover on TV
- Release APK layout split via `APP_LAYOUT` (universal / mobile / tv), matching upstream

### Fixed
- Fire Stick 4K (1st gen) splash → crash: soft-fail Conscrypt, skip Cronet on Fire/API ≤ 25, skip WebView cache wipe on low-RAM sticks, `extractNativeLibs` + `useLegacyPackaging` (`#84`, earlier `#37`)
- Quiet-audio / ambience ducking mid-playback (`#38`); TV auto-pause from focus/lifecycle glitches (`#26`)
- Provider “No servers found” / “All servers failed” restorations (`#81`, `#85`), including:
  HDFilme, MEGAKino, Frembed, GuardaSerie, GuardaFlix, Anikoto, MKissa,
  FlixLatam / SoloLatino / LaCartoons / Pelisflix / Pelisplusto / CineHax / Zaluknij / Vavoo,
  TvPorInternet / Libre Futbol / CableVision / Sports Events (M3U `|` titles + headers),
  Pluto TV / IPTV channel-like detail, Animefenix home when `/directorio` is CF-blocked
- Additional provider / playback fixes after upstream sync: Vavoo MediaHub resolve (`#4`), MEGAKino domain + manual URL (`#6`), TMDb DoH / IPv4 (`#8`), TMDb ENG anime / audio language (`#16`, `#20`), AnimeFLV → animeflv.one (`#24`), Doramasflix App Router (`#28`), FrenchStream hash portals (`#30`), AnimeAV1 black screen on TV boxes (`#34`), leftover merge markers (`#40`)
- Duplicate string resources blocking release (EN/ES/IT/DE) (`#79`, `#82`)
- Release compile errors (imports, `SubtitleOffset`, AnimeFLV, FrenchAnime/FrenchManga, GuardaFlix) (`#80`, `#83`)
- Downloads live progress while tab visible; server/quality options always shown; UI polish
- Home hanging forever on a slow provider
- Android TV / OEM playback crash-to-home paths (see `#41`)
- Remove items from Recently Watched (`#12`); season number parsing from TMDB-style ids

### Upstream
- Synced from **streamflix-reborn2/streamflix** at **v1.7.231** (merge of upstream `main` before BetterStreamflix v1.0.0)
- Inherited upstream 1.7.231 work includes CB01 Uprot/HMAC updates, Cinecalidad UA, AnimeSaturn scrape fix, sports IPTV provider fixes, extractor aliases (Voe, VidMoLy, VidHide, StreamWish, Hxfile, LuluVdo, Goodstream, VidxGo), conditional NDK/`native-lib.cpp` build, Supabase docs URL fix

## [1.0.0] - 2026-09-08

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

[1.1.0]: https://github.com/dskja/BetterStreamflix/releases/tag/v1.1.0
[1.0.0]: https://github.com/dskja/BetterStreamflix/releases/tag/v1.0.0
