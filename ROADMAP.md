# BetterStreamflix Roadmap

Synced with the BetterStreamflix megaplan (phases **P0–P7**). App version is **`1.0.0`** (`versionName` / `versionCode` in `app/build.gradle`) — older notes that cited `1.7.231` were drift and should be ignored.

## Done

- [x] Create new `dskja/BetterStreamflix` repository
- [x] Rebrand package, app name, README
- [x] Modern CI pipeline (build + dependabot)
- [x] Point in-app updater and help links to new repo
- [x] Add `local.properties.example`
- [x] Ship app version **1.0.0** (release workflow / signed APK)

## Megaplan phases (summary)

| Phase | Focus | User impact |
|-------|--------|-------------|
| **P0** | Stability, security, provider fixes, scaffold inventory | App does not crash / leaks less |
| **P1** | Empty/error UX, a11y, legal onboarding, themes, parental | Feels “finished” |
| **P2** | Player features, search/discovery, provider health | Better playback & finding |
| **P3** | Downloads, sync UX, Watch Next, widgets, IPTV, anime | Platform features |
| **P4** | Network/DB/modules, god-class splits, dead-code purge | Maintainability |
| **P5** | Tests, i18n, CI hardening, docs, releases | Trustworthy fork |
| **P6** | Profiles, offline catalog, perf, notifications | Power users |
| **P7** | Marketplace UX, Trakt, Media3 extras, optional modules | Differentiation |

Detail and Ship-or-Cut decisions: [docs/SCAFFOLD_AUDIT.md](docs/SCAFFOLD_AUDIT.md). Architecture: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

### P0 — Stability & security (priority)

- [x] **Crash:** Provider defaults — unimplemented methods return empty lists / empty `People`, never `TODO()` (PlutoTV locales, IPTV, SuperStream, Otakufr, …)
- [x] **Crash:** `NavigationSlidePresenter` state save/restore
- [x] **Crash:** Empty catches in player/bypass — log + user message
- [x] **Crash:** Progress UI only when `durationMillis > 0`
- [x] **Security:** Parental PIN → EncryptedSharedPreferences + migration
- [x] **Security:** Backup rules exclude PIN/tokens (or disable backup)
- [x] **Security:** Cleartext domain allowlist; restrict `trustAll` SSL
- [x] **Security:** Scrub tokens/query params from release logs
- [x] **Security:** Validate deep-link provider/id (no open redirect)
- [x] Domains: StreamingCommunity, SerienStream, Moflix, Cuevana, Poseidon (auto-failover probe)
- [x] Fix Spanish `TmdbProvider.getServers()` (empty list)
- [x] Extractor miss → structured error + alias/domain pass
- [x] Scaffold audit committed (`docs/SCAFFOLD_AUDIT.md`)

### P1 — UX / onboarding

- [x] Wire empty/error/loading helpers (Search, catalog, genres, people)
- [x] First-run legal accept + post-update “What’s new”
- [ ] A11y / TV focus; one Theme / Accessibility / FirstRun helper
- [ ] Parental UI polish (PIN dialog, session indicator)
- [x] Settings parity Mobile/TV; Settings search

### P2 — Player, discovery, health

- [x] Shared next-episode, sleep timer, seek gestures, buffer presets, PiP actions, subtitle auto-fetch
- [x] Search debounce, history, filters; genres hub; TMDB people enrich
- [x] RecommendationEngine home rows
- [x] Provider Health UI + domain failover + CircuitBreaker

### P3 — Platform

- [x] Downloads E2E Mobile **or** cut `download/`
- [x] Cloud sync UX (banner, conflicts, i18n strings)
- [x] Watch Next + widgets + shortcuts + deep links
- [x] Cast package cut (unless real Cast SDK)
- [x] IPTV M3U/Xtream + EPG MVP (or explicit defer)
- [x] Anime Dub/Sub + episode numbering options

### P4 — Architecture

- [x] NetworkClient mandatory; DoH fix; Room schema/indexes; AppLevel unify
- [x] Split Settings/Player god classes; dead-code purge per scaffold audit
- [ ] Version catalog; dependency bumps (media3, room, supabase, ktor)

### P5 — Quality & docs

- [x] Detekt / ktlint; CI matrix `APP_LAYOUT` mobile + tv; no soft-fail lint/tests
- [x] i18n parity (PL + `cloud_sync_*`); CI missing-key gate
- [x] Expand unit/instrumented suite beyond ~19 tests
- [x] CONTRIBUTING, ARCHITECTURE, PROVIDERS, LEGAL, PR template, ROADMAP sync

### P6 / P7 — Ambition

- [x] Local profiles / household PIN switch
- [x] Offline banner + cached home; notification new-episodes
- [ ] Provider marketplace UX; optional Trakt; Compose islands for new screens only

## Infrastructure (ongoing)

- [ ] Automated release notes from tag range / Keep-a-Changelog sections
- [ ] Nightly provider DNS/home smoke → issue labels
- [x] CodeQL + dependency-review
