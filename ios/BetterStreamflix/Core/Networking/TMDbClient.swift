import Foundation

enum TMDbClient {
    static let apiBase = URL(string: "https://api.themoviedb.org/3/")!
    static let imageBase = URL(string: "https://image.tmdb.org/t/p/")!

    static func imageURL(_ path: String?, size: String = "w500") -> URL? {
        guard let path, !path.isEmpty else { return nil }
        if path.hasPrefix("http") { return URL(string: path) }
        return URL(string: "\(size)\(path)", relativeTo: imageBase)?.absoluteURL
    }

    static func get<T: Decodable>(
        _ path: String,
        query: [String: String] = [:],
        language: String = "de-DE"
    ) async throws -> T {
        guard AppSecrets.hasTMDbKey else {
            throw ProviderError.missingAPIKey("TMDb")
        }
        var components = URLComponents(url: apiBase.appendingPathComponent(path), resolvingAgainstBaseURL: false)!
        var items = [
            URLQueryItem(name: "api_key", value: AppSecrets.tmdbAPIKey),
            URLQueryItem(name: "language", value: language),
        ]
        for (key, value) in query {
            items.append(URLQueryItem(name: key, value: value))
        }
        components.queryItems = items
        guard let url = components.url else { throw ProviderError.invalidURL }
        let data = try await HTTPClient.getJSON(url: url)
        return try JSONDecoder().decode(T.self, from: data)
    }

    struct Page<T: Decodable>: Decodable {
        let results: [T]
        let page: Int?
        let totalPages: Int?

        enum CodingKeys: String, CodingKey {
            case results, page
            case totalPages = "total_pages"
        }
    }

    struct Multi: Decodable, Identifiable {
        let id: Int
        let mediaType: String?
        let title: String?
        let name: String?
        let overview: String?
        let posterPath: String?
        let backdropPath: String?
        let releaseDate: String?
        let firstAirDate: String?
        let voteAverage: Double?

        enum CodingKeys: String, CodingKey {
            case id, title, name, overview
            case mediaType = "media_type"
            case posterPath = "poster_path"
            case backdropPath = "backdrop_path"
            case releaseDate = "release_date"
            case firstAirDate = "first_air_date"
            case voteAverage = "vote_average"
        }

        var displayTitle: String { title ?? name ?? "Untitled" }
        var year: String? {
            let raw = releaseDate ?? firstAirDate
            return raw.flatMap { String($0.prefix(4)) }
        }

        var kind: MediaItem.Kind {
            if mediaType == "movie" { return .movie }
            if mediaType == "tv" { return .tvShow }
            if title != nil, name == nil { return .movie }
            return .tvShow
        }

        var catalogID: String {
            "\(kind == .movie ? "movie" : "tv"):\(id)"
        }

        func asMediaItem() -> MediaItem? {
            guard mediaType != "person" else { return nil }
            return MediaItem(
                id: catalogID,
                title: displayTitle,
                posterURL: TMDbClient.imageURL(posterPath),
                bannerURL: TMDbClient.imageURL(backdropPath, size: "original"),
                overview: overview,
                year: year,
                rating: voteAverage,
                kind: kind,
                providerHint: "tmdb-de"
            )
        }
    }

    struct MovieDetail: Decodable {
        let id: Int
        let title: String
        let overview: String?
        let posterPath: String?
        let backdropPath: String?
        let releaseDate: String?
        let voteAverage: Double?
        let imdbId: String?
        let runtime: Int?
        let genres: [Genre]?
        let credits: Credits?

        enum CodingKeys: String, CodingKey {
            case id, title, overview, runtime, genres, credits
            case posterPath = "poster_path"
            case backdropPath = "backdrop_path"
            case releaseDate = "release_date"
            case voteAverage = "vote_average"
            case imdbId = "imdb_id"
        }
    }

    struct TVDetail: Decodable {
        let id: Int
        let name: String
        let overview: String?
        let posterPath: String?
        let backdropPath: String?
        let firstAirDate: String?
        let voteAverage: Double?
        let genres: [Genre]?
        let credits: Credits?
        let seasons: [Season]?
        let externalIds: ExternalIds?

        enum CodingKeys: String, CodingKey {
            case id, name, overview, genres, credits, seasons
            case posterPath = "poster_path"
            case backdropPath = "backdrop_path"
            case firstAirDate = "first_air_date"
            case voteAverage = "vote_average"
            case externalIds = "external_ids"
        }
    }

    struct Season: Decodable {
        let id: Int?
        let name: String?
        let seasonNumber: Int
        let posterPath: String?
        let episodeCount: Int?

        enum CodingKeys: String, CodingKey {
            case id, name
            case seasonNumber = "season_number"
            case posterPath = "poster_path"
            case episodeCount = "episode_count"
        }
    }

    struct SeasonDetail: Decodable {
        let episodes: [Episode]
    }

    struct Episode: Decodable {
        let id: Int
        let name: String?
        let overview: String?
        let episodeNumber: Int
        let stillPath: String?

        enum CodingKeys: String, CodingKey {
            case id, name, overview
            case episodeNumber = "episode_number"
            case stillPath = "still_path"
        }
    }

    struct Genre: Decodable {
        let id: Int
        let name: String
    }

    struct Credits: Decodable {
        let cast: [CastMember]?
    }

    struct CastMember: Decodable {
        let name: String?
    }

    struct ExternalIds: Decodable {
        let imdbId: String?

        enum CodingKeys: String, CodingKey {
            case imdbId = "imdb_id"
        }
    }
}
