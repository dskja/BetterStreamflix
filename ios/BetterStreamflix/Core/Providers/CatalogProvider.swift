import Foundation

protocol CatalogProvider: Identifiable, Sendable {
    var id: String { get }
    var name: String { get }
    var language: String { get }
    var baseURL: URL { get }

    func home() async throws -> [CategoryRow]
    func search(query: String) async throws -> [MediaItem]
    func detail(id: String, kind: MediaItem.Kind) async throws -> ShowDetail
    func episodes(showId: String, seasonId: String) async throws -> [EpisodeInfo]
    func streams(showId: String, seasonId: String?, episodeId: String?) async throws -> [StreamSource]
}

enum ProviderError: LocalizedError {
    case invalidURL
    case emptyResponse
    case parseFailed(String)
    case http(Int)
    case unsupported

    var errorDescription: String? {
        switch self {
        case .invalidURL: "Invalid URL"
        case .emptyResponse: "Empty response from provider"
        case .parseFailed(let reason): "Parse failed: \(reason)"
        case .http(let code): "HTTP \(code)"
        case .unsupported: "Not supported by this provider"
        }
    }
}
