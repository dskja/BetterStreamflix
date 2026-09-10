import Foundation

enum VideasyExtractor {
    private static let apiBase = URL(string: "https://api.speedracelight.com")!
    private static let decryptURL = URL(string: "https://enc-dec.app/api/dec-videasy")!

    static func sourcesURL(
        tmdbId: String,
        title: String,
        mediaType: String,
        year: String,
        imdbId: String?,
        season: Int?,
        episode: Int?,
        language: String = "german"
    ) -> URL {
        var components = URLComponents(
            url: apiBase.appendingPathComponent("meine/sources-with-title"),
            resolvingAgainstBaseURL: false
        )!
        var items: [URLQueryItem] = [
            .init(name: "title", value: title),
            .init(name: "mediaType", value: mediaType),
            .init(name: "year", value: year),
            .init(name: "tmdbId", value: tmdbId),
            .init(name: "imdbId", value: imdbId ?? ""),
            .init(name: "language", value: language),
        ]
        if let season {
            items.append(.init(name: "seasonId", value: String(season)))
        }
        if let episode {
            items.append(.init(name: "episodeId", value: String(episode)))
        }
        components.queryItems = items
        return components.url!
    }

    static func resolve(_ sourceURL: URL) async throws -> URL {
        let encData = try await HTTPClient.getJSON(url: sourceURL)
        let encText = String(data: encData, encoding: .utf8) ?? ""
        let tmdbId = URLComponents(url: sourceURL, resolvingAgainstBaseURL: false)?
            .queryItems?.first(where: { $0.name == "tmdbId" })?.value ?? ""

        let payload: [String: String] = ["text": encText, "id": tmdbId]
        let body = try JSONSerialization.data(withJSONObject: payload)
        let decData = try await HTTPClient.postJSON(url: decryptURL, body: body)
        let root = try JSONSerialization.jsonObject(with: decData) as? [String: Any]
        let resultString = root?["result"] as? String ?? ""
        let resultData = Data(resultString.utf8)
        let result = try JSONSerialization.jsonObject(with: resultData) as? [String: Any]
        let sources = result?["sources"] as? [[String: Any]]
        guard let first = sources?.first,
              let urlString = first["url"] as? String,
              let url = URL(string: urlString) else {
            throw ProviderError.parseFailed("Videasy returned no playable source")
        }
        return url
    }
}
