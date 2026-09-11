import Foundation

@MainActor
@Observable
final class LibraryStore {
    static let shared = LibraryStore()

    private let favoritesKey = "library.favorites.v2"
    private let continueKey = "library.continue.v2"

    var favorites: [MediaItem] = []
    var continueWatching: [ContinueWatchEntry] = []

    private init() {
        load()
    }

    func isFavorite(_ item: MediaItem, providerID: String) -> Bool {
        favorites.contains { $0.id == item.id && $0.providerHint == providerID }
    }

    func toggleFavorite(_ item: MediaItem, providerID: String) {
        if let index = favorites.firstIndex(where: { $0.id == item.id && $0.providerHint == providerID }) {
            favorites.remove(at: index)
        } else {
            var copy = item
            copy.providerHint = providerID
            favorites.insert(copy, at: 0)
        }
        persistFavorites()
    }

    func recordProgress(
        item: MediaItem,
        providerID: String,
        seasonID: String?,
        episodeID: String?,
        progress: Double
    ) {
        let entry = ContinueWatchEntry(
            id: "\(providerID):\(item.id):\(seasonID ?? ""):\(episodeID ?? "")",
            title: item.title,
            posterURL: item.posterURL,
            kind: item.kind,
            providerID: providerID,
            mediaID: item.id,
            seasonID: seasonID,
            episodeID: episodeID,
            progress: progress,
            updatedAt: Date()
        )
        continueWatching.removeAll { $0.id == entry.id }
        continueWatching.insert(entry, at: 0)
        if continueWatching.count > 40 {
            continueWatching = Array(continueWatching.prefix(40))
        }
        persistContinue()
    }

    func removeContinue(_ entry: ContinueWatchEntry) {
        continueWatching.removeAll { $0.id == entry.id }
        persistContinue()
    }

    private func load() {
        let decoder = JSONDecoder()
        if let data = UserDefaults.standard.data(forKey: favoritesKey),
           let decoded = try? decoder.decode([MediaItemDTO].self, from: data) {
            favorites = decoded.map(\.asMediaItem)
        }
        if let data = UserDefaults.standard.data(forKey: continueKey),
           let decoded = try? decoder.decode([ContinueWatchDTO].self, from: data) {
            continueWatching = decoded.map(\.asEntry).sorted { $0.updatedAt > $1.updatedAt }
        }
    }

    private func persistFavorites() {
        let encoder = JSONEncoder()
        let dtos = favorites.map(MediaItemDTO.init)
        if let data = try? encoder.encode(dtos) {
            UserDefaults.standard.set(data, forKey: favoritesKey)
        }
    }

    private func persistContinue() {
        let encoder = JSONEncoder()
        let dtos = continueWatching.map(ContinueWatchDTO.init)
        if let data = try? encoder.encode(dtos) {
            UserDefaults.standard.set(data, forKey: continueKey)
        }
    }
}

private struct MediaItemDTO: Codable {
    var id: String
    var title: String
    var poster: String?
    var banner: String?
    var overview: String?
    var year: String?
    var rating: Double?
    var kind: String
    var providerHint: String?

    init(_ item: MediaItem) {
        id = item.id
        title = item.title
        poster = item.posterURL?.absoluteString
        banner = item.bannerURL?.absoluteString
        overview = item.overview
        year = item.year
        rating = item.rating
        kind = item.kind.rawValue
        providerHint = item.providerHint
    }

    var asMediaItem: MediaItem {
        MediaItem(
            id: id,
            title: title,
            posterURL: poster.flatMap(URL.init(string:)),
            bannerURL: banner.flatMap(URL.init(string:)),
            overview: overview,
            year: year,
            rating: rating,
            kind: MediaItem.Kind(rawValue: kind) ?? .tvShow,
            providerHint: providerHint
        )
    }
}
