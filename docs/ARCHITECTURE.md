# BetterStreamflix Architecture

Overview of how the Android TV / mobile aggregator is structured. Prefer extending these patterns over introducing parallel stacks (see [SCAFFOLD_AUDIT.md](SCAFFOLD_AUDIT.md)).

## Product shape

User picks a **provider** → browses catalog (movies / TV / anime / IPTV) → **extractor** resolves a playable URL → **Media3 / ExoPlayer** plays it. Optional **Supabase** sync keeps watch progress and favorites across devices.

## Modules (Gradle)

| Module | Role |
|--------|------|
| `:app` | UI, ViewModels, providers, extractors, Room, sync, preferences |
| `:navigation` | Custom navigation / slide presenter (TV-oriented) |
| `:retrofit-jsoup-converter` | HTML scraping converter for Retrofit |
| `supabase/` | SQL migrations and sync schema (not a runtime Android module) |

Longer-term megaplan (P4) extracts `:core:models`, `:core:network`, `:core:database`, `:providers`, `:extractors`, `:feature:sync`, `:feature:player` — not required for current contributions.

## Dual UI

Build layout is selected via `APP_LAYOUT` in `local.properties`:

- empty / unset — universal (both layouts as configured)
- `mobile` — AppCompat mobile UI
- `tv` — Leanback TV UI

Shared ViewModels and domain code live under `fragments/`, `providers/`, `extractors/`, `utils/`, `sync/`. Layout-specific code uses `*Mobile*` / `*Tv*` activities, fragments, and resources.

## MVVM

- **View**: Fragments + View Binding (no Compose rewrite in early phases)
- **ViewModel**: Per-screen ViewModels (`SearchViewModel`, player VMs, home, etc.)
- **Model**: `models/` + Room entities/DAOs + provider DTOs
- UI helpers: `ui/` (`UiState`, empty/error/shimmer helpers)

## Providers & extractors

```text
Provider (catalog + servers)
    → Video.Server list
        → Extractor.extract(link)
            → Video (source URL + subtitles)
                → Player fragments (Media3)
```

- Registry: `Provider.companion.providers` in [`Provider.kt`](../app/src/main/java/com/betterstreamflix/providers/Provider.kt)
- Extractor list: `Extractor.companion` in [`Extractor.kt`](../app/src/main/java/com/betterstreamflix/extractors/Extractor.kt)
- How to add one: [PROVIDERS.md](PROVIDERS.md)

## Persistence (Room)

- **Per-provider databases** for favorites / continue watching scoped to a source
- **App-level DB** (`AppLevelDatabase`) for cross-cutting state — megaplan P4 unifies strategy and real migrations
- Home cache (~6h refresh) with stale fallback when a provider is down

## Sync

- `sync/` + Supabase migrations under `supabase/`
- Cloud account settings UI; mutation queue for offline-friendly updates
- Local prefs: `UserPreferences`, themes, parental PIN (encrypt in P0)

## Network

Canonical client: `utils.NetworkClient` (+ DNS / DoH helpers). Scaffold `network/` package is scheduled to **merge** into that path (P4). Prefer NetworkClient over ad-hoc `OkHttpClient.Builder()` in new code.

## Player

Production players are the inline fragments:

- `fragments/player/PlayerMobileFragment`
- `fragments/player/PlayerTvFragment`

Advanced helpers under `player/` are **merge** candidates (next episode, sleep timer, buffer presets, PiP) — not a second player stack.

## Entry points

| Area | Anchor |
|------|--------|
| Application | `StreamFlixApp.kt` |
| Activities | `activities/main/` (Mobile / TV) |
| Themes | `ThemeManager`, `res/values/themes.xml` |
| Settings | `res/xml/settings_mobile.xml`, `settings_tv.xml` |
| Updates | `utils.InAppUpdater` |

## Design principles (megaplan)

1. One canonical implementation per concern  
2. Mobile and TV parity where it makes sense  
3. Ship-or-Cut for scaffold packages  
4. Security default-deny (cleartext, backup, logging, PIN)  
5. No Compose full migration as a blocker for fixes  
