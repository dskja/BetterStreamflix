import Foundation

protocol CatalogProvider: Identifiable, Sendable {
    var id: String { get }
    var name: String { get }
    var language: String { get }
    var baseURL: URL { get }
    /// When false, provider is catalog-only and streams come from linked extractors.
    var providesNativeStreams: Bool { get }

    func home() async throws -> [CategoryRow]
    func search(query: String) async throws -> [MediaItem]
    func detail(id: String, kind: MediaItem.Kind) async throws -> ShowDetail
    func episodes(showId: String, seasonId: String) async throws -> [EpisodeInfo]
    func streams(showId: String, seasonId: String?, episodeId: String?, detail: ShowDetail?) async throws -> [StreamSource]
}

extension CatalogProvider {
    var providesNativeStreams: Bool { true }

    func streams(showId: String, seasonId: String?, episodeId: String?) async throws -> [StreamSource] {
        try await streams(showId: showId, seasonId: seasonId, episodeId: episodeId, detail: nil)
    }
}

enum ProviderError: LocalizedError {
    case invalidURL
    case emptyResponse
    case parseFailed(String)
    case http(Int, url: String?)
    case unsupported
    case missingAPIKey(String)
    case streamGate(String)

    var errorDescription: String? {
        switch self {
        case .invalidURL: "Invalid URL"
        case .emptyResponse: "Empty response from provider"
        case .parseFailed(let reason): "Parse failed: \(reason)"
        case .http(let code, let url):
            if let url, !url.isEmpty {
                return "HTTP \(code) · \(url)"
            }
            return "HTTP \(code)"
        case .unsupported: "Not supported by this provider"
        case .missingAPIKey(let name): "Missing API key for \(name)"
        case .streamGate(let reason): reason
        }
    }
}
