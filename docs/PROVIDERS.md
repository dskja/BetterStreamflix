# Adding providers and extractors

BetterStreamflix aggregates third-party catalogs through **Provider** implementations and resolves stream hosts through **Extractor** implementations. Unregistered classes are never shown in the UI.

## Add a provider

1. Create `app/src/main/java/com/betterstreamflix/providers/YourProvider.kt` implementing `Provider` (or `IptvProvider` for live channels).

2. Implement the interface methods. Required surface:

   - `baseUrl`, `name`, `logo`, `language`
   - `getHome()`, `search()`, `getMovies()`, `getTvShows()`
   - `getMovie()`, `getTvShow()`, `getEpisodesBySeason()`
   - `getGenre()`, `getPeople()`
   - `getServers()`, `getVideo()`

3. **Register** the object in [`Provider.kt`](../app/src/main/java/com/betterstreamflix/providers/Provider.kt) companion `providers` map with `ProviderSupport`:

```kotlin
YourProvider to ProviderSupport(movies = true, tvShows = true),
```

Set `movies` / `tvShows` to match what the source actually supports so UI tabs stay honest.

4. Optional: implement `ProviderPortalUrl` / `ProviderConfigUrl` if the site needs portal/base URL failover.

5. Prefer `NetworkClient` for HTTP. Rethrow `CancellationException`; do not swallow coroutine cancellation.

### Safe empty defaults (mandatory)

Never leave unimplemented methods as `TODO("Not yet implemented")` — that crashes when the UI calls them.

Use safe empties instead:

```kotlin
override suspend fun getMovies(page: Int): List<Movie> = emptyList()

override suspend fun getPeople(id: String, page: Int): People {
    return People(id = id, name = "", filmography = emptyList())
}

override suspend fun getEpisodesBySeason(seasonId: String): List<Episode> = emptyList()

override suspend fun getServers(id: String, videoType: Video.Type): List<Video.Server> =
    emptyList()
```

Return empty lists / empty `People` / empty server lists so the UI can show “no results” / “not supported” instead of crashing.

### Enrich `getPeople()` with TMDB (optional, recommended for scrapers)

If your source's own actor page has no photo/biography, use `TmdbUtils.enrichPersonByName(name, language)` to fill it in from TMDB (no-op when `ENABLE_TMDB` is off or nothing matches):

```kotlin
val tmdbPerson = TmdbUtils.enrichPersonByName(peopleName, language = language)
People(
    id = id,
    name = peopleName,
    image = tmdbPerson?.image,
    biography = tmdbPerson?.biography,
    placeOfBirth = tmdbPerson?.placeOfBirth,
    birthday = tmdbPerson?.birthday?.format("yyyy-MM-dd"),
    deathday = tmdbPerson?.deathday?.format("yyyy-MM-dd"),
    filmography = /* ... */,
)
```

See `AniWorldProvider.getPeople()` / `SerienStreamProvider.getPeople()` for a working example.

## Add an extractor

1. Create a class under `app/src/main/java/com/betterstreamflix/extractors/` extending `Extractor`.

2. Implement:

```kotlin
class YourHostExtractor : Extractor() {
    override val name = "YourHost"
    override val mainUrl = "https://yourhost.example"
    override val aliasUrls = listOf("https://alias.example") // optional
    // optional: override val rotatingDomain = listOf(Regex("yourhost\\..+"))

    override suspend fun extract(link: String): Video {
        // Resolve playable source (+ subtitles if available)
        return Video(source = "...", subtitles = emptyList())
    }
}
```

3. **Register** an instance in the `extractors` list inside [`Extractor.kt`](../app/src/main/java/com/betterstreamflix/extractors/Extractor.kt) companion object.

Matching order: `mainUrl` → `aliasUrls` → domain-stripped match → `rotatingDomain` → server name contains extractor name.

If the matched extractor's `extract()` throws, `Extractor.extract()` retries other registered extractors that share the same host or a server-name hint before giving up. If nothing matches (or every candidate fails), it throws `Extractor.ExtractionFailedException` with the link and the list of extractor names that were attempted, so logs/UI can distinguish "no extractor registered" from "extractor(s) matched but the host changed/broke".

## Checklist before PR

- [ ] Provider/extractor registered  
- [ ] No `TODO()` on interface methods  
- [ ] Unsupported paths return empty defaults  
- [ ] `CancellationException` rethrown  
- [ ] Smoke: home / search / detail / play on at least one layout (`APP_LAYOUT=mobile` or `tv`)  
- [ ] Follow [LEGAL.md](LEGAL.md) — educational aggregator only  

## Related

- [ARCHITECTURE.md](ARCHITECTURE.md)
- [CONTRIBUTING.md](../CONTRIBUTING.md)
