# Changelog

All notable changes to BetterStreamflix are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Massive Integrations upgrade: shared `IntegrationStatus` + `IntegrationProbes`, live hub card summaries (“N of M connected”), per-service status rows, hardened Jellyfin/Plex/Debrid/Simkl/OpenSubtitles connection tests, OpenSubtitles login/JWT UI, Debrid credential visibility by provider, Cast queue moved to Player, Plex/Simkl help links, MPV install status, Settings onResume refresh after OAuth
- Massive providers + home upgrade: shared `HomeCatalogPipeline` (FEATURED synthesis, providerName stamp, absolute artwork URLs, empty-shelf drop, dedupe), home circuit breaker in `ProviderSmoke`, persistent catalog warning banner (Mobile/TV, tap to retry), `ProviderDefaults` stubs, healthier provider picker ranking, expanded quarantine/smoke lists
- Home cache now persists `providerName` so stale-while-revalidate shelves keep ownership across process death
- Massive IPTV live player: channel guide session with prev/next zapping, Go Live edge seek, pulsing LIVE badge, channel meta chrome, live-tuned ExoPlayer buffers + Media3 LiveConfiguration, IPTV-Org/Spain channel list overrides
- Massive TMDb upgrade: in-memory detail/IMDb/search cache, Find-by-IMDb enrichment, home shelves (Now Playing / Upcoming / On The Air / Airing Today / Top Rated), `tmdbId` on models, provider enable gate, Settings status + connection test + cache clear
- Massive Support system expansion: smart startup cooldown (5 days / max 8), durable appreciation thanks across process death, Impact goals + FAQ on Mobile/TV hubs, Issues/Releases links, Details Telegram + impact, Preview wires hero/impact/FAQ, Thanks → Discord
- Lumina experimental design upgrade: accent palettes (Crimson/Ember/Aurora/Slate), pure-black OLED surfaces, optional Material You tint, nav auto-hide / hero parallax / reduced-glass toggles, stronger home brand reveal + provider chip
- SerienStream account system: status, WebView sign-in, session validate, paste/copy cookies, full logout (prefs + CookieManager), startup cookie seed, Settings hub card (`screen_serienstream_auth`)
- Massive plugin/addon system: `PluginManager` lifecycle, host facade, home/search/metadata/playback/extractor/subtitle/settings extension hooks, `LoadedPluginFacade` for LOCAL APKs, Demo Addon v1.1, manage/enable/uninstall UI, soft reload, diagnostics event ring

### Fixed
- Jellyfin/Plex “Test connection” lived under Plugins and almost always reported OK; probes now live on their own screens and verify real identity endpoints
- Debrid `isAuthenticated()` now hits provider account APIs instead of only checking non-empty keys
- Settings → Miscellaneous hardened against BETTERSTREAMFLIX-K recurrence: dependency sanitizer on nested PreferenceScreens + regression tests that `screen_more` never keeps cross-screen `android:dependency`
- Home soft-fail no longer relies on Toast-only warnings — catalog issues stay visible until retry succeeds
- Home featured swiper crash after leave (BETTERSTREAMFLIX-1): clear ViewPager auto-advance on recycle
- Videasy decrypt empty/`JSONObject("")` crash (BETTERSTREAMFLIX-4)
- TMDb trending ClassCastException on incomplete MultiItem JSON (BETTERSTREAMFLIX-Q)
- AnyMovie SSL trust-anchor failures use NetworkClient.trustAll (BETTERSTREAMFLIX-R)
- Nekostream null Gson body no longer NPEs mid-candidate loop
- Settings crash opening “More”: Preference dependency `EXPERIMENTAL_NEW_APP_DESIGN` was resolved across nested PreferenceScreens (Sentry BETTERSTREAMFLIX-K)
- Settings: Support UI Preview lives next to Experimental Design under Appearance (same PreferenceScreen); TV Experimental Design also under Appearance; nested screen inflate failures fall back to Settings root
- TV bypass QR / deep-link: accept `betterstreamflix://resolve` and HTTP landing QR in the in-app scanner and MainMobileActivity
- Download resume no longer fatals when Android blocks background service starts (Sentry BETTERSTREAMFLIX-J / -M)
- Player ExoPlayer listeners no longer stack on every `displayVideo` (TV + mobile)
- Mobile player server-select `!!` NPE on blank SerienStream bypass fallthrough
- TV mid-playback hoster failover enabled (parity with mobile)
- Unified download error classification between enqueue UI and Media3 events
- SerienStream cookie seeding uses scheme-aware proxy origin; only official proxy IP forces HTTP
- Bypass “solved” detection matches real cookie names (not vague `token`/`auth` substrings)
- HomeViewModel rethrows `CancellationException` instead of soft-failing cancelled jobs
- Sentry drops JobCancellationException noise; `isSendDefaultPii` disabled
- Cast stream proxy closes upstream OkHttp responses on playlist/error paths

## [1.1.1] - 2026-09-19

### Added
- Full Platform integrations: Trakt (VIP app credentials gated), Jellyfin, Plex, Debrid, Simkl, OpenSubtitles v1, MPV handoff, plugin hooks
- Full Sentry Android SDK (init, user, nav breadcrumbs, feedback; full sample rates in debug, dialed-down traces/replay in production)
- Settings Integrations hub near the top of classic Settings; experimental Support-style Settings hub (Mobile + TV, gated by Experimental UI)
- Lumina experimental design unified with Support visual language (Manrope + crimson tokens; off by default)
- Nested PreferenceScreens for platform / content / playback / downloads / appearance / network / provider / cloud / backup
- SerienStream CUII bypass: official [serien.domains](https://serien.domains) proxy `http://186.2.175.5/` as default endpoint (HTTP), with `.to` / `.cx` fallbacks; WebView DoH bridge for hostname mirrors
- KinoGer live DOM scrape + Cloudflare hardening + poster/Featured artwork fixes
- Patreon support link: https://www.patreon.com/BetterStreamflix (Settings, About, README, GitHub FUNDING.yml)
- Configurable download storage location (internal / app-external / public Movies)
- Chromecast queue, subtitle toggle, keep-screen-awake option, richer cast metadata
- APK CI artifacts on every push to `main` / `dskja/**` (no GitHub Release unless tagged)
- Re-enabled five previously unregistered providers whose sites are live again: StreamingIta (it), AnyMovie (en), HiAnime (en), 1Jour1Film (fr), AfterDark (fr)
- Experimental mobile design rebuilt on Material 3: `Theme.Material3.Dark` shell with dynamic wallpaper color on Android 12+ (static M3 dark scheme below) covering the entire mobile surface — home, details, seasons/episodes, genres, people, downloads, player, dialogs and loading states
- Poster-derived ambient glow behind home hero and detail posters (Palette API), floating tonal nav pill, shimmer skeleton loading, hero scroll parallax, press-scale and ripple feedback on cards (still off-by-default behind the experimental toggle)
- Experimental motion layer: ViewPager2 page transformer + animated pill dots on the featured swiper, ambient glow follows the active slide, brand fade/parallax on scroll, auto-hiding bottom nav, fragment enter transitions, staggered grid/row fill-in, loading crossfades, ribbon pop-ins, haptic ticks on primary actions
- Experimental player polish: primary-tinted buffering spinner, glass brightness/volume panels, tabular-figure time labels, M3 error overlay with close action, content descriptions on all transport controls
- Experimental detail screens: floating tonal back chip, play-icon CTA, M3 meta chips, provider selected-check badge, icon+text empty states for favorites/downloads/search
- Experimental dialogs/settings: icon rows with ripple state layers in the options sheet, tonal spinner fields, primary-tinted progress in watchlist-import/bypass, tooltips + TalkBack page announcements on the swiper, all entry animations honour the system animator-duration scale
- Download notifications carry Pause/Resume-All actions; failed downloads get a per-item notification with a Retry action (`DownloadActionReceiver`)
- Orphaned download sidecar subtitle directories are pruned on startup when their DB item no longer exists
- Subtitle offset is persisted per video (`movie:<id>`/`episode:<id>`) and restored on every player build instead of being reset globally
- Media3 audio focus handling enabled (playback pauses on calls/transient focus loss); audio content type set to movie
- Offline subtitles: subtitle tracks offered by the resolved server are downloaded alongside the video into the download's sidecar directory and injected into offline playback (`SubtitleFetch` + `subtitleUrlsJson`/`subtitlePathsJson`)
- Smart Downloads (opt-in, Settings → Downloads): automatically enqueue the next episode after one finishes — including season-boundary rollover — and optionally delete watched episode downloads; honours Wi-Fi-only and storage guards
- Downloads tab: sort menu (newest/title/size, mobile + TV), "retry all failed", "delete watched", per-item long-press overflow with Share (FileProvider) and Retry, long-press season packs to pause/resume/delete the whole pack, watched badge on finished downloads
- `DownloadDatabase` migration 1→2 preserves existing downloads (adds `subtitleUrlsJson`, `smartEnqueued`); destructive fallback removed so the Media3 cache is never orphaned

### Changed
- App version set to **1.1.1** (`versionCode` 10101)
- Centralized HTTP 409 cache-clear + one-shot retry in `Http409CacheGuard` across all 16 content fragments; skips the wipe while offline
- `Accept-Language` header is now built from the app locale instead of a hardcoded `it-IT`; OkHttp gained explicit call/write timeouts, throttled cookie persistence and 429/5xx `Retry-After` backoff
- Cloud sync worker retries are capped at 5 attempts with explicit exponential backoff
- Continue-watching queries gained bounded variants (LIMIT 25) for home/cross-provider rails; backup export keeps the unbounded query
- SearchViewModel cancels in-flight searches when a new query starts, guards `loadMore` against double-fires and no longer reports cancellations as failures
- StreamingCommunity default domain `streamingunity.cc` → `streamingunity.win`; stale stored domains auto-migrate
- Cuevana 3 default domain `cuevana.gs` → `cuevana3.gs`; stale stored domains auto-migrate
- Download HTTP headers (Referer/User-Agent/cookies) are now keyed per URL/origin and injected at `DataSpec` level — fixes header bleed across parallel downloads on the shared data-source factory
- Download failures are classified (space/expired/cleartext/network) instead of always reporting a generic network error; speed sampling no longer leaks entries for finished downloads
- Wiflix default `flemmix.team` → `neufneuf.space`
- CB01 default `cb01official.uno` → `cb01uno.homes`
- CineCalidad default `cinecalidad.ec` → `cinecalidad.am`
- SeriesFlix default `seriesflixhd.lol` → `seriesflixhd.team`
- Series Turcas default `tbg.seriesturcastv.to` → `kaj.seriesturcastv.to`
- Anime Online Ninja `ww3.` → `ver.animeonline.ninja`
- AfterDark `afterdark.best` → `afterdark.rest`; portal now reads the topsitestreaming.club index (new `url:` field)
- Artwork repair matches all `animeonline.ninja` hosts, not only `ww3.`
- SerienStream default endpoint: `serienstream.to` → `186.2.175.5` proxy ([serien.domains](https://serien.domains))

### Fixed
- SerienStream/AniWorld watchlist import: correct ID parsing, WebView HTML scrape, pagination, login/challenge detection; CUII copyright-page failover to proxy
- Hide floating search on Downloads/Settings so the downloads gear is usable
- FilmPalast SSL fallback via NetworkClient; SerienStream domain failover across known mirrors (incl. `.cx`; deprecate dead `.sx`; bypass cookie seed uses live hosts only)
- Player no longer installs an empty media URI while loading a server (fake 0:00/0:00 playing state)
- Home soft-fails when catalog fetch fails so continue-watching/favorites still render
- AnimeFLV default domain → animeflv.vc; TioAnime → tioanime.top; SoloLatino browser headers refreshed
- Frembed URL discovery: dead portal `audin213.com` (spam redirect) replaced by `frembed.casa`; portal redirects now resolve the live domain directly before DOM parsing
- Settings screens showed outdated default domains for StreamingCommunity (`cuevana3.la` typo included) and Cuevana 3
- Backup import now refuses backups written by a newer app version instead of parsing them with stale field mappings
- DoH bootstrap/client diagnostics no longer log in release builds
- KinoGer wrong poster selector (KG logos) and Featured banner blank shell
- TV SerienStream bypass instructions document cookie/session path when QR / same Wi‑Fi is unavailable (#112, #117)

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
- SerienStream/AniWorld watchlist import: correct ID parsing, WebView HTML scrape, pagination, login/challenge detection
- Hide floating search on Downloads/Settings so the downloads gear is usable
- FilmPalast SSL fallback via NetworkClient; SerienStream domain failover across known mirrors
- Player no longer installs an empty media URI while loading a server (fake 0:00/0:00 playing state)
- Home soft-fails when catalog fetch fails so continue-watching/favorites still render
- AnimeFLV default domain → animeflv.vc; TioAnime → tioanime.top; SoloLatino browser headers refreshed
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

[1.1.1]: https://github.com/dskja/BetterStreamflix/releases/tag/v1.1.1
[1.1.0]: https://github.com/dskja/BetterStreamflix/releases/tag/v1.1.0
[1.0.0]: https://github.com/dskja/BetterStreamflix/releases/tag/v1.0.0
