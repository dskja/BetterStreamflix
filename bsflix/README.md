# BetterStreamflix (Flutter)

Pulse redesign of BetterStreamflix — **Flutter / Dart** rewrite of the core app.

## Scope (v3 core)

- Home with Peacock-style hero card
- Search
- Title detail
- Video player (sample stream)
- Provider picker (Pulse glass chips)

Live provider/extractor backends are intentionally stubbed with demo catalog data for the first Flutter cut.

## Run

```bash
cd bsflix
flutter pub get
flutter run
```

## Installable APK

```bash
cd bsflix
flutter build apk --debug
# → build/app/outputs/flutter-apk/app-debug.apk
```

CI workflow: **Build Flutter APK** uploads debug + release artifacts.

## Stack

- Flutter 3.35+ / Dart 3.9+
- Riverpod, go_router
- Google Fonts (Space Grotesk + Manrope)
- video_player + chewie
- Pulse theme (charcoal + amber→orange CTA)
