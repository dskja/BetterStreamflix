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

    enum Kind: String, Hashable, Sendable {
        case tvShow
        case movie
    }
}

struct CategoryRow: Identifiable, Hashable, Sendable {
    let id: String
    var title: String
    var items: [MediaItem]
}

struct SeasonInfo: Identifiable, Hashable, Sendable {
    let id: String
    var number: Int
    var title: String
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
}

struct StreamSource: Identifiable, Hashable, Sendable {
    let id: String
    var name: String
    var url: URL
    var headers: [String: String]
}

struct PlaybackRequest: Hashable, Sendable {
    var title: String
    var sources: [StreamSource]
}
