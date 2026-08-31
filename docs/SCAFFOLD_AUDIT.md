# Scaffold Ship-or-Cut Audit

Inventory of orphan / scaffold packages in BetterStreamflix that have little or no production wiring from fragments/activities. Decisions follow the megaplan: **Ship** (wire as a feature), **Merge** (fold into existing utils/fragments and delete duplicates), or **Cut** (remove dead code).

| Package | Decision | Phase | Notes |
|---------|----------|-------|-------|
| `download/` (+ DownloadDao) | Ship | P3 | Stack nearly complete; wire UI + WorkManager + Room + offline playback (Mobile first). Cut-PR if scope explodes. |
| `widgets/` | Ship | P3 | Register Manifest + `appwidget-provider` XML + RemoteViews (Continue Watching / Favorites). |
| `WatchNextUtils` / `tv/` | Ship / **Cut** | P3 | `WatchNextUtils` shipped on progress save; unwired `tv/` scaffold package **cut (done)**. |
| `player/` (advanced helpers) | Merge | P2→P4 | Consolidate NextEpisodeOverlay, SleepTimer, Buffer, PiP into player fragments/utils; delete duplicate stubs. |
| `search/` | Merge | P2 | History / Filter / Debounce into `SearchViewModel`; fuzzy/recommender optional. |
| `cast/` | **Cut (done)** | P3 | Removed — no Cast SDK; use external player |
| `analytics/` | Ship | P5 | Wire diagnostic export (FileLogger / ShareHelper). Firebase optional later — not default. |
| `resilience/` (+ ProviderHealth) | Ship | P2 | Wire Health + CircuitBreaker into provider `getHome` / `search` / `getVideo` paths. |
| `network/` | Merge | P4 | Fold into `utils.NetworkClient`; fix DoH builder (`HttpClientBuilder.buildWithDoH`). |
| `security/` | Merge | P0→P1 | Merge PIN/Lock into UserPreferences / EncryptedSharedPreferences path. |
| `polish/` | **Cut (done)** | P4 | Removed — unwired scaffold; use `utils/` / settings helpers. |
| `i18n/` | Ship | P1→P5 | Wire LocaleHelper; i18n parity + CI gate in P5. |
| `accessibility/` | Ship | P1 | Caption/font/reduced-motion into Settings; one AccessibilityHelper. |
| `imageloading/` | Cut | P4 | Glide is production path; revisit only if P6 perf needs a dedicated layer. |
| `performance/` | Cut | P4 / P6 | Cut stubs now; optional deep-dive in P6 (prefetch, trim, pools). |
| `deployment/` (UpdateChecker stub) | **Cut (done)** | P4 | Removed — keep real `InAppUpdater` in `utils/`. |
| `testing/` | **Cut (done)** | P4 | Removed from main source set |
| `architecture/` (FeatureFlags) | **Cut (done)** | P4 | Removed — no production imports. |
| `content/` (backup duplicates) | Cut | P3→P4 | Keep `backup/BackupRestoreManager`; remove duplicate content-backup paths. |
| `AppLevelDatabase` | Merge | P4 | Unify with provider-DB strategy; real migrations (no destructive fallback). |
| `notifications/` | Ship | P3 / P6 | Wire with Downloads (P3) and New-Episode for favorites (P6). |
| `metadata/` (RecommendationEngine) | Ship | P2 | Wire personalized Home rows from watch history. |
| `settings/` helpers | Merge | P1 | Wire Settings-Search; cut unused helpers. |

## Rules

1. No third parallel API for the same concern — Ship, Merge, or Cut.
2. Production code lives in `fragments/`, `utils/`, `sync/`, `ui/` today; scaffold packages must not remain half-wired.
3. Update this table when a package is wired or deleted (date + PR link in Notes).

## Related

- Megaplan Phase 0.5 (Scaffold Ship-or-Cut)
- [ARCHITECTURE.md](ARCHITECTURE.md)
- [ROADMAP.md](../ROADMAP.md)
