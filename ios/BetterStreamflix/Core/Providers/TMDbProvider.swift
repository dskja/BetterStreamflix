import Foundation

struct TMDbProvider: CatalogProvider {
    let id = "tmdb-de"
    let name = "TMDb"
    let language = "de"
    let baseURL = URL(string: "https://www.themoviedb.org/")!
    var providesNativeStreams: Bool { false }

    private let apiLanguage = "de-DE"
    private let region = "DE"

    func home() async throws -> [CategoryRow] {
        guard AppSecrets.hasTMDbKey else {
            throw ProviderError.missingAPIKey("TMDb")
        }

        async let trending: TMDbClient.Page<TMDbClient.Multi> = TMDbClient.get(
            "trending/all/day",
            language: apiLanguage
        )
        async let popularMovies: TMDbClient.Page<TMDbClient.Multi> = TMDbClient.get(
            "movie/popular",
            language: apiLanguage
        )
        async let popularTV: TMDbClient.Page<TMDbClient.Multi> = TMDbClient.get(
            "tv/popular",
            language: apiLanguage
        )
        async let airing: TMDbClient.Page<TMDbClient.Multi> = TMDbClient.get(
            "tv/airing_today",
            language: apiLanguage
        )
        async let topRated: TMDbClient.Page<TMDbClient.Multi> = TMDbClient.get(
            "tv/top_rated",
            language: apiLanguage
        )
        async let netflix: TMDbClient.Page<TMDbClient.Multi> = TMDbClient.get(
            "discover/tv",
            query: ["with_watch_providers": "8", "watch_region": region, "sort_by": "popularity.desc"],
            language: apiLanguage
        )
        async let disney: TMDbClient.Page<TMDbClient.Multi> = TMDbClient.get(
            "discover/tv",
            query: ["with_watch_providers": "337", "watch_region": region, "sort_by": "popularity.desc"],
            language: apiLanguage
        )
        async let amazon: TMDbClient.Page<TMDbClient.Multi> = TMDbClient.get(
            "discover/movie",
            query: ["with_watch_providers": "119", "watch_region": region, "sort_by": "popularity.desc"],
            language: apiLanguage
        )

        let trendingItems = try await mapPage(trending)
        let featured = Array(trendingItems.prefix(8))
        let restTrending = Array(trendingItems.dropFirst(8))

        var rows: [CategoryRow] = []
        if !featured.isEmpty {
            rows.append(CategoryRow(id: "featured", title: "Jetzt angesagt", items: featured, isFeatured: true))
        }
        if !restTrending.isEmpty {
            rows.append(CategoryRow(id: "trending", title: "Trending heute", items: restTrending))
        }
        rows.append(CategoryRow(id: "movies", title: "Beliebte Filme", items: try await mapPage(popularMovies)))
        rows.append(CategoryRow(id: "tv", title: "Beliebte Serien", items: try await mapPage(popularTV)))
        rows.append(CategoryRow(id: "airing", title: "Heute im TV", items: try await mapPage(airing)))
        rows.append(CategoryRow(id: "top", title: "Top bewertet", items: try await mapPage(topRated)))
        rows.append(CategoryRow(id: "netflix", title: "Auf Netflix", items: try await mapPage(netflix)))
        rows.append(CategoryRow(id: "disney", title: "Auf Disney+", items: try await mapPage(disney)))
        rows.append(CategoryRow(id: "amazon", title: "Auf Prime Video", items: try await mapPage(amazon)))
        return rows.filter { !$0.items.isEmpty }
    }

    func search(query: String) async throws -> [MediaItem] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return [] }
        let page: TMDbClient.Page<TMDbClient.Multi> = try await TMDbClient.get(
            "search/multi",
            query: ["query": trimmed, "include_adult": "false"],
            language: apiLanguage
        )
        return page.results.compactMap { $0.asMediaItem() }
    }

    func detail(id: String, kind: MediaItem.Kind) async throws -> ShowDetail {
        let (mediaKind, numericID) = try parseID(id, fallbackKind: kind)
        switch mediaKind {
        case .movie:
            let movie: TMDbClient.MovieDetail = try await TMDbClient.get(
                "movie/\(numericID)",
                query: ["append_to_response": "credits,external_ids"],
                language: apiLanguage
            )
            return ShowDetail(
                id: "movie:\(movie.id)",
                title: movie.title,
                overview: movie.overview,
                posterURL: TMDbClient.imageURL(movie.posterPath),
                bannerURL: TMDbClient.imageURL(movie.backdropPath, size: "original"),
                year: movie.releaseDate.map { String($0.prefix(4)) },
                rating: movie.voteAverage,
                seasons: [],
                kind: .movie,
                imdbId: movie.imdbId,
                genres: movie.genres?.map(\.name) ?? [],
                cast: movie.credits?.cast?.prefix(12).compactMap(\.name).map { $0 } ?? []
            )
        case .tvShow:
            let show: TMDbClient.TVDetail = try await TMDbClient.get(
                "tv/\(numericID)",
                query: ["append_to_response": "credits,external_ids"],
                language: apiLanguage
            )
            let seasons = (show.seasons ?? [])
                .filter { $0.seasonNumber >= 0 && ($0.episodeCount ?? 1) > 0 }
                .map {
                    SeasonInfo(
                        id: "tv:\(show.id)/season/\($0.seasonNumber)",
                        number: $0.seasonNumber,
                        title: $0.name ?? "Staffel \($0.seasonNumber)",
                        posterURL: TMDbClient.imageURL($0.posterPath)
                    )
                }
            return ShowDetail(
                id: "tv:\(show.id)",
                title: show.name,
                overview: show.overview,
                posterURL: TMDbClient.imageURL(show.posterPath),
                bannerURL: TMDbClient.imageURL(show.backdropPath, size: "original"),
                year: show.firstAirDate.map { String($0.prefix(4)) },
                rating: show.voteAverage,
                seasons: seasons,
                kind: .tvShow,
                imdbId: show.externalIds?.imdbId,
                genres: show.genres?.map(\.name) ?? [],
                cast: show.credits?.cast?.prefix(12).compactMap(\.name).map { $0 } ?? []
            )
        }
    }

    func episodes(showId: String, seasonId: String) async throws -> [EpisodeInfo] {
        let (_, tvID) = try parseID(showId, fallbackKind: .tvShow)
        let seasonNumber: Int = {
            if let n = Int(seasonId.split(separator: "/").last.map(String.init) ?? "") {
                return n
            }
            return 1
        }()
        let season: TMDbClient.SeasonDetail = try await TMDbClient.get(
            "tv/\(tvID)/season/\(seasonNumber)",
            language: apiLanguage
        )
        return season.episodes.map {
            EpisodeInfo(
                id: "tv:\(tvID)/season/\(seasonNumber)/episode/\($0.episodeNumber)",
                number: $0.episodeNumber,
                title: $0.name ?? "Episode \($0.episodeNumber)",
                overview: $0.overview,
                thumbnailURL: TMDbClient.imageURL($0.stillPath)
            )
        }
    }

    func streams(showId: String, seasonId: String?, episodeId: String?, detail: ShowDetail?) async throws -> [StreamSource] {
        let resolvedDetail: ShowDetail
        if let detail {
            resolvedDetail = detail
        } else {
            resolvedDetail = try await self.detail(
                id: showId,
                kind: showId.hasPrefix("movie:") ? .movie : .tvShow
            )
        }
        let (kind, tmdbID) = try parseID(resolvedDetail.id, fallbackKind: resolvedDetail.kind)
        let year = resolvedDetail.year ?? ""
        var seasonNum: Int?
        var episodeNum: Int?
        if let episodeId {
            let parts = episodeId.split(separator: "/").map(String.init)
            if let eIdx = parts.firstIndex(of: "episode"), eIdx + 1 < parts.count {
                episodeNum = Int(parts[eIdx + 1])
            }
            if let sIdx = parts.firstIndex(of: "season"), sIdx + 1 < parts.count {
                seasonNum = Int(parts[sIdx + 1])
            }
        } else if let seasonId {
            seasonNum = Int(seasonId.split(separator: "/").last.map(String.init) ?? "")
        }

        let mediaType = kind == .movie ? "movie" : "tv"
        var sources: [StreamSource] = []

        // Host scrapes first — Videasy DE (`meine`) often returns upstream HTTP 500.
        if kind == .tvShow {
            if let match = try? await SerienStreamProvider().search(query: resolvedDetail.title).first,
               let ssStreams = try? await SerienStreamProvider().streams(
                showId: match.id,
                seasonId: seasonNum.map { "\(match.id)/staffel-\($0)" },
                episodeId: nil,
                detail: nil
               ) {
                sources.append(contentsOf: ssStreams.prefix(8).map { source in
                    StreamSource(
                        id: "ss-\(source.id)",
                        name: "SerienStream · \(source.name)",
                        url: source.url,
                        headers: source.headers,
                        resolveKind: source.resolveKind
                    )
                })
            }
        } else if let match = try? await FilmPalastProvider().search(query: resolvedDetail.title).first(where: { $0.kind == .movie }),
                  let fpStreams = try? await FilmPalastProvider().streams(
                    showId: match.id,
                    seasonId: nil,
                    episodeId: nil,
                    detail: nil
                  ) {
            sources.append(contentsOf: fpStreams.prefix(8).map { source in
                StreamSource(
                    id: "fp-\(source.id)",
                    name: "FP · \(source.name)",
                    url: source.url,
                    headers: source.headers,
                    resolveKind: source.resolveKind
                )
            })
        }

        sources.append(contentsOf: VideasyExtractor.streamSources(
            tmdbId: String(tmdbID),
            title: resolvedDetail.title,
            mediaType: mediaType,
            year: year,
            imdbId: resolvedDetail.imdbId,
            season: kind == .tvShow ? seasonNum : nil,
            episode: kind == .tvShow ? episodeNum : nil
        ))

        return sources
    }

    private func mapPage(_ page: TMDbClient.Page<TMDbClient.Multi>) -> [MediaItem] {
        page.results.compactMap { $0.asMediaItem() }
    }

    private func parseID(_ id: String, fallbackKind: MediaItem.Kind) throws -> (MediaItem.Kind, Int) {
        if id.contains(":") {
            let parts = id.split(separator: ":", maxSplits: 1).map(String.init)
            guard parts.count == 2, let num = Int(parts[1].split(separator: "/").first.map(String.init) ?? "") else {
                throw ProviderError.parseFailed("Bad TMDb id \(id)")
            }
            let kind: MediaItem.Kind = parts[0] == "movie" ? .movie : .tvShow
            return (kind, num)
        }
        guard let num = Int(id) else { throw ProviderError.parseFailed("Bad TMDb id \(id)") }
        return (fallbackKind, num)
    }
}
