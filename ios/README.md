# BetterStreamflix iOS (Liquid Glass beta)

Unsigned SwiftUI client for German catalogs (SerienStream, AniWorld).

## Scope (beta v1)

- Shell + Liquid Glass UI (iOS 26+)
- Home / Search / Detail / Player
- Provider framework with SerienStream + AniWorld

## Requirements

- Xcode 26+ (Liquid Glass APIs)
- [XcodeGen](https://github.com/yonaskolb/XcodeGen)

```bash
brew install xcodegen
cd ios
xcodegen generate
open BetterStreamflix.xcodeproj
```

## Unsigned CI IPA

GitHub Actions workflow `.github/workflows/ios-build.yml` archives with
`CODE_SIGNING_ALLOWED=NO` and uploads:

- `BetterStreamflix-unsigned.ipa`
- `BetterStreamflix.xcarchive`

Re-sign / sideload locally with your own tooling (Sideloadly, TrollStore, etc.).
No Apple signing secrets are stored in this repo.
