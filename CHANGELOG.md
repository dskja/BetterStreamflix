# Changelog

## v1.0.0

### Bug Fixes
- Fixed release build crash caused by ViewPager2 reflection (NoSuchFieldException: mRecyclerView)
- Replaced unsafe reflection with getChildAt approach
- Added ProGuard keep rules for AndroidX ViewPager2, RecyclerView, provider services, TMDb3 classes, extractors, and utilities
- Fixed compilation error in MovieMobileFragment and TvShowMobileFragment (submitList callback)

### Changes
- Redesigned provider selection page with gradient header, updated card layouts, search bar, and language chips
- Improved TMDB person search to prioritize exact name matches
- Episode images now use original quality from TMDB
- Added providers subtitle string (EN/DE/IT)
- Consolidated release workflow to produce a single signed APK
- Reset version numbering to 1.0.0

### Known Issues
- Actor profile images and biography info not yet displaying
- Episode and season images still showing generic series covers in some cases

