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

        async let trendingResult = softPage("trending/all/day")
        async let popularMoviesResult = softPage("movie/popular")
        async let popularTVResult = softPage("tv/popular")
        async let airingResult = softPage("tv/airing_today")
        async let topRatedResult = softPage("tv/top_rated")
        async let netflixResult = softPage(
            "discover/tv",
            query: ["with_watch_providers": "8", "watch_region": region, "sort_by": "popularity.desc"]
        )
        async let disneyResult = softPage(
            "discover/tv",
            query: ["with_watch_providers": "337", "watch_region": region, "sort_by": "popularity.desc"]
        )
        async let amazonResult = softPage(
            "discover/movie",
            query: ["with_watch_providers": "119", "watch_region": region, "sort_by": "popularity.desc"]
        )

        let trendingItems = await trendingResult
        let featured = Array(trendingItems.prefix(8))
        let restTrending = Array(trendingItems.dropFirst(8))

        var rows: [CategoryRow] = []
        if !featured.isEmpty {
            rows.append(CategoryRow(id: "featured", title: "Jetzt angesagt", items: featured, isFeatured: true))
        }
        if !restTrending.isEmpty {
            rows.append(CategoryRow(id: "trending", title: "Trending heute", items: restTrending))
        }
        let popularMovies = await popularMoviesResult
        if !popularMovies.isEmpty {
            rows.append(CategoryRow(id: "movies", title: "Beliebte Filme", items: popularMovies))
        }
        let popularTV = await popularTVResult
        if !popularTV.isEmpty {
            rows.append(CategoryRow(id: "tv", title: "Beliebte Serien", items: popularTV))
        }
        let airing = await airingResult
        if !airing.isEmpty {
            rows.append(CategoryRow(id: "airing", title: "Heute im TV", items: airing))
        }
        let topRated = await topRatedResult
        if !topRated.isEmpty {
            rows.append(CategoryRow(id: "top", title: "Top bewertet", items: topRated))
        }
        let netflix = await netflixResult
        if !netflix.isEmpty {
            rows.append(CategoryRow(id: "netflix", title: "Auf Netflix", items: netflix))
        }
        let disney = await disneyResult
        if !disney.isEmpty {
            rows.append(CategoryRow(id: "disney", title: "Auf Disney+", items: disney))
        }
        let amazon = await amazonResult
        if !amazon.isEmpty {
            rows.append(CategoryRow(id: "amazon", title: "Auf Prime Video", items: amazon))
        }
        if rows.isEmpty {
            throw ProviderError.parseFailed("TMDb unreachable")
        }
        return rows
    }

    private func softPage(_ path: String, query: [String: String] = [:]) async -> [MediaItem] {
        do {
            let page: TMDbClient.Page<TMDbClient.Multi> = try await TMDbClient.get(
                path,
                query: query,
                language: apiLanguage
            )
            return mapPage(page)
        } catch {
            return []
        }
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
                bannerURL: TMDbClient.imageURL(movie.backdropPath, size: "w1280"),
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
                bannerURL: TMDbClient.imageURL(show.backdropPath, size: "w1280"),
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

        // Host scrapes first — must hit *episode* pages for SerienStream (season pages have 0 hosts).
        if kind == .tvShow {
            let ss = SerienStreamProvider()
            if let match = bestMatch(
                try? await ss.search(query: resolvedDetail.title),
                target: resolvedDetail.title,
                kind: .tvShow
            ) {
                let seasonPath = "\(match.id)/staffel-\(seasonNum ?? 1)"
                let episodePath: String
                if let episodeNum {
                    if let episodes = try? await ss.episodes(showId: match.id, seasonId: seasonPath),
                       let ep = episodes.first(where: { $0.number == episodeNum }) {
                        episodePath = ep.id
                    } else {
                        episodePath = "\(seasonPath)/episode-\(episodeNum)"
                    }
                } else {
                    episodePath = seasonPath
                }
                if let ssStreams = try? await ss.streams(
                    showId: match.id,
                    seasonId: seasonPath,
                    episodeId: episodePath,
                    detail: nil
                ), !ssStreams.isEmpty {
                    sources.append(contentsOf: ssStreams.prefix(10).map { source in
                        StreamSource(
                            id: "ss-\(source.id)",
                            name: "S.to · \(source.name)",
                            url: source.url,
                            headers: source.headers,
                            resolveKind: source.resolveKind
                        )
                    })
                }
            }
        } else {
            let fp = FilmPalastProvider()
            if let match = bestMatch(
                try? await fp.search(query: resolvedDetail.title),
                target: resolvedDetail.title,
                kind: .movie
            ),
               let fpStreams = try? await fp.streams(
                showId: match.id,
                seasonId: nil,
                episodeId: nil,
                detail: nil
               ), !fpStreams.isEmpty {
                sources.append(contentsOf: fpStreams.prefix(10).map { source in
                    StreamSource(
                        id: "fp-\(source.id)",
                        name: "FP · \(source.name)",
                        url: source.url,
                        headers: source.headers,
                        resolveKind: source.resolveKind
                    )
                })
            }
        }

        // Videasy last — DE meine is often 500; keep EN mirrors as soft fallbacks.
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

    private func bestMatch(_ items: [MediaItem]?, target: String, kind: MediaItem.Kind) -> MediaItem? {
        guard let items, !items.isEmpty else { return nil }
        return items
            .filter { $0.kind == kind }
            .map { ($0, titleScore($0.title, target)) }
            .filter { $0.1 >= 55 }
            .max(by: { $0.1 < $1.1 })?
            .0
            ?? items.first(where: { $0.kind == kind })
            ?? items.first
    }

    private func titleScore(_ lhs: String, _ rhs: String) -> Int {
        let a = normalizeTitle(lhs)
        let b = normalizeTitle(rhs)
        if a.isEmpty || b.isEmpty { return 0 }
        if a == b { return 100 }
        if a.contains(b) || b.contains(a) { return 85 }
        let aTokens = Set(a.split(separator: " ").map(String.init))
        let bTokens = Set(b.split(separator: " ").map(String.init))
        guard !aTokens.isEmpty, !bTokens.isEmpty else { return 0 }
        let inter = aTokens.intersection(bTokens).count
        let union = aTokens.union(bTokens).count
        return Int((Double(inter) / Double(union)) * 100)
    }

    private func normalizeTitle(_ value: String) -> String {
        value
            .lowercased()
            .replacingOccurrences(of: #"\(.*?\)|\[.*?\]"#, with: "", options: .regularExpression)
            .replacingOccurrences(of: #"[:'\!\?.,]"#, with: "", options: .regularExpression)
            .replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
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
