# Contributing to BetterStreamflix

Thanks for helping maintain this fork. Please read [docs/LEGAL.md](docs/LEGAL.md) before submitting provider or extractor work.

## Branch conventions

- Fork the repo and branch from `main`.
- Prefer short, descriptive names:
  - `fix/...` — bugfixes
  - `feat/...` or `feature/...` — user-facing features
  - `refactor/...` — structure without behavior change
  - `chore/...` — CI, deps, cleanup
  - `docs/...` — documentation only
- Cloud / agent branches may use the `dskja…-de6b` template; keep names lowercase.
- Commit messages: concise, imperative (`fix: …`, `feat: …`, `docs: …`).

## Build setup

1. Install [Android Studio](https://developer.android.com/studio) (JDK 17).
2. Clone the repo and open it in Android Studio.
3. Copy `local.properties.example` → `local.properties` and fill values:

```properties
APP_LAYOUT=          # empty = universal; "mobile" or "tv" for layout-specific builds
TMDB_API_KEY=
SUBDL_API_KEY=
RABBITSTREAM_SOURCE_API=
```

4. Sync Gradle, then Run on an emulator/device.

### APP_LAYOUT

| Value | Result |
|-------|--------|
| *(empty)* | Default / universal configuration |
| `mobile` | Mobile (AppCompat) UI |
| `tv` | Android TV (Leanback) UI |

CI is moving toward a matrix that builds both `mobile` and `tv` (see megaplan P5). Test the layout you change.

### CLI

```bash
./gradlew assembleDebug
./gradlew lintDebug
./gradlew testDebugUnitTest
```

NDK/CMake may be required for native key helpers used in release builds.

## Testing

- Add or update unit tests under `app/src/test` for registry flags, parsers, prefs, merge logic, etc.
- Manually smoke: Home → Detail → Player, Search, Settings, provider switch — on the `APP_LAYOUT` you touched.
- Do not leave `TODO("Not yet implemented")` on provider methods; use empty defaults ([docs/PROVIDERS.md](docs/PROVIDERS.md)).
- Prefer fixing failures over `continue-on-error` / `|| true` in new CI steps.

## Pull request guidelines

1. One concern per PR when practical (hotfix vs feature vs dead-code cut).
2. Describe **what** and **why**; link issues.
3. Note Mobile / TV / both impact.
4. For providers/extractors: registration + smoke notes + domains touched.
5. For scaffold packages: follow [docs/SCAFFOLD_AUDIT.md](docs/SCAFFOLD_AUDIT.md) (Ship / Merge / Cut) — no third parallel API.
6. Use the PR template (`.github/PULL_REQUEST_TEMPLATE.md`).
7. Keep PRs reviewable; large god-class splits should preserve behavior.

## Docs map

| Doc | Purpose |
|-----|---------|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Modules, MVVM, dual UI, Room, sync |
| [docs/PROVIDERS.md](docs/PROVIDERS.md) | Add provider / extractor |
| [docs/SCAFFOLD_AUDIT.md](docs/SCAFFOLD_AUDIT.md) | Ship-or-Cut inventory |
| [docs/LEGAL.md](docs/LEGAL.md) | Educational-use disclaimer |
| [ROADMAP.md](ROADMAP.md) | Phased megaplan summary |

## Code of conduct (practical)

Be respectful to the original Streamflix authors and other forks. No harassment, spam, or illegal content requests in issues/PRs.
