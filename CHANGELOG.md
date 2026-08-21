# Changelog

All notable changes to BetterStreamflix will be documented here.

## [1.7.231] - 2026-08-22

### Fork / Rebrand
- Forked from Streamflix Reborn as `BetterStreamflix`
- Renamed package to `com.betterstreamflix`
- Updated app name, README and project metadata

### CI / Automation
- Added GitHub Actions `build.yml` for automated debug builds on push/PR
- Added `dependabot.yml` for automated dependency and action updates
- Updated `release.yml` to include `SUBDL_API_KEY` and modern checkout/java actions

### Fixes / Updates
- In-app update checker now points to `dskja/BetterStreamflix`
- Help / Telegram links in settings now point to new BetterStreamflix community
- Added `local.properties.example` with required build keys

## [Unreleased]

- Provider domain refresh
- More extractor fixes
- Cloud-sync improvements
