# Changelog

All notable changes to BetterStreamflix are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.2.0] - 2026-09-09

### Added
- PR CI workflow (duplicate-strings check, unit tests, `assembleDebug`)
- Shared player core (`PlayerBuilderFactory`, `PlaybackFailover`, `SerienStreamBypassHelper`)
- Local `CrashReporter` with Settings entry to view the last crash log
- Provider quarantine list + smoke harness (`ProviderHealth`, `ProviderSmoke`)
- Home `getHome` timeout with stale-while-revalidate cache
- Official branding assets under `branding/`

### Changed
- App version set to **1.2.0** (`versionCode` 10200)
- New BetterStreamflix app icon / TV banner (black · white · red)
- Adaptive launcher background color `#000000`
- SerienStream bypass Continue only after challenge cookies
- TV/mobile playback: decoder fallback + software-decoder retry

### Fixed
- Android TV / OEM playback crash-to-home paths (see #41)
- Home screen hanging forever on a slow provider

[1.2.0]: https://github.com/dskja/BetterStreamflix/releases/tag/v1.2.0
