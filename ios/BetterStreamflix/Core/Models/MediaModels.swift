import Foundation

struct MediaItem: Identifiable, Hashable, Sendable {
    let id: String
    var title: String
    var posterURL: URL?
    var bannerURL: URL?
    var overview: String?
    var year: String?
    var rating: Double?
    var kind: Kind
    var providerHint: String?

    enum Kind: String, Hashable, Codable, Sendable {
        case tvShow
        case movie
    }
}

struct CategoryRow: Identifiable, Hashable, Sendable {
    let id: String
    var title: String
    var items: [MediaItem]
    var isFeatured: Bool = false
}

struct SeasonInfo: Identifiable, Hashable, Sendable {
    let id: String
    var number: Int
    var title: String
    var posterURL: URL?
}

struct EpisodeInfo: Identifiable, Hashable, Sendable {
    let id: String
    var number: Int
    var title: String
    var overview: String?
    var thumbnailURL: URL?
}

struct ShowDetail: Identifiable, Hashable, Sendable {
    let id: String
    var title: String
    var overview: String?
    var posterURL: URL?
    var bannerURL: URL?
    var year: String?
    var rating: Double?
    var seasons: [SeasonInfo]
    var kind: MediaItem.Kind
    var imdbId: String?
    var genres: [String]
    var cast: [String]

    init(
        id: String,
        title: String,
        overview: String? = nil,
        posterURL: URL? = nil,
        bannerURL: URL? = nil,
        year: String? = nil,
        rating: Double? = nil,
        seasons: [SeasonInfo] = [],
        kind: MediaItem.Kind,
        imdbId: String? = nil,
        genres: [String] = [],
        cast: [String] = []
    ) {
        self.id = id
        self.title = title
        self.overview = overview
        self.posterURL = posterURL
        self.bannerURL = bannerURL
        self.year = year
        self.rating = rating
        self.seasons = seasons
        self.kind = kind
        self.imdbId = imdbId
        self.genres = genres
        self.cast = cast
    }
}

struct StreamSource: Identifiable, Hashable, Sendable {
    let id: String
    var name: String
    var url: URL
    var headers: [String: String]
    var resolveKind: ResolveKind

    enum ResolveKind: String, Hashable, Sendable {
        case direct
        case followRedirect
        case videasy
        case serienstreamGate
    }
}

struct ContinueWatchEntry: Identifiable, Hashable, Sendable {
    let id: String
    var title: String
    var posterURL: URL?
    var kind: MediaItem.Kind
    var providerID: String
    var mediaID: String
    var seasonID: String?
    var episodeID: String?
    var progress: Double
    var updatedAt: Date

    var asMediaItem: MediaItem {
        MediaItem(
            id: mediaID,
            title: title,
            posterURL: posterURL,
            bannerURL: nil,
            overview: nil,
            year: nil,
            rating: nil,
            kind: kind,
            providerHint: providerID
        )
    }
}

struct ContinueWatchDTO: Codable {
    var id: String
    var title: String
    var poster: String?
    var kind: String
    var providerID: String
    var mediaID: String
    var seasonID: String?
    var episodeID: String?
    var progress: Double
    var updatedAt: Date

    init(_ entry: ContinueWatchEntry) {
        id = entry.id
        title = entry.title
        poster = entry.posterURL?.absoluteString
        kind = entry.kind.rawValue
        providerID = entry.providerID
        mediaID = entry.mediaID
        seasonID = entry.seasonID
        episodeID = entry.episodeID
        progress = entry.progress
        updatedAt = entry.updatedAt
    }

    var asEntry: ContinueWatchEntry {
        ContinueWatchEntry(
            id: id,
            title: title,
            posterURL: poster.flatMap(URL.init(string:)),
            kind: MediaItem.Kind(rawValue: kind) ?? .tvShow,
            providerID: providerID,
            mediaID: mediaID,
            seasonID: seasonID,
            episodeID: episodeID,
            progress: progress,
            updatedAt: updatedAt
        )
    }
}
