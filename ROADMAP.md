# BetterStreamflix Roadmap

Short- and mid-term goals for making this the best maintained fork.

## Done
- [x] Create new `dskja/BetterStreamflix` repository
- [x] Rebrand package, app name, README
- [x] Modern CI pipeline (build + dependabot)
- [x] Point in-app updater and help links to new repo
- [x] Add `local.properties.example`
- [x] Bump version to 1.7.231

## Next releases
- [ ] Refresh / fix dead provider domains (StreamingCommunity, SerienStream, Moflix, Cuevana, Poseidon)
- [ ] Fix Spanish `TmdbProvider.getServers()` (currently returns empty list)
- [ ] Add more robust extractor error handling and fallbacks
- [ ] Update core dependencies (media3, room, supabase, ktor) to latest stable
- [ ] Automated release pipeline for mobile & TV APKs
- [ ] Add a "What changed" dialog after in-app update

## Quality of life
- [ ] Unified settings for provider domain failover
- [ ] Clearer error messages when a video source fails
- [ ] Optional in-app changelog on first start after update
- [ ] Better parental-control UI

## Infrastructure
- [ ] Enabling test builds for both `mobile` and `tv` APP_LAYOUT values
- [ ] Detekt / ktlint for code style
- [ ] Issue templates and PR templates
