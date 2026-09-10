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

    enum Kind: String, Hashable, Sendable {
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

struct ContinueWatchEntry: Identifiable, Hashable, Codable, Sendable {
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
