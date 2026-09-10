# BetterStreamflix iOS — Liquid Glass Beta v3

Unsigned SwiftUI client (iOS 26+) with TMDb catalog + German scrape providers.

## Beta v3 scope

- **TMDb (DE)** primary catalog (trending, popular, discover shelves) using the same `TMDB_API_KEY` as Android
- Playback via **Videasy (Killjoy DE)** plus title-matched SerienStream / Filmpalast hosts
- **SerienStream** fixed to current site selectors (`term` search, `#season-nav`, `tr.episode-row`, `button.link-box` / challenge WebView)
- **AniWorld** hardened (beliebte + cover lists + alphabet search cache, hoster list)
- **Filmpalast** movies/series
- Library: continue watching + bookmarks
- Settings: TMDb key override
- Multi-source picker + header-aware AVPlayer

## Requirements

- Xcode 26+
- [XcodeGen](https://github.com/yonaskolb/XcodeGen)
- Optional: `TMDB_API_KEY` in repo-root `local.properties` (same as Android)

```bash
brew install xcodegen
./ios/ci/generate-secrets.sh
cd ios && xcodegen generate
open BetterStreamflix.xcodeproj
```

## Unsigned CI IPA

`.github/workflows/ios-build.yml` injects `secrets.TMDB_API_KEY`, archives with
`CODE_SIGNING_ALLOWED=NO`, and uploads:

- `BetterStreamflix-unsigned.ipa`
- `BetterStreamflix.xcarchive`

Re-sign / sideload locally. No Apple signing secrets are stored in this repo.
