import Foundation

enum VideasyExtractor {
    private static let apiBase = URL(string: "https://api.speedracelight.com")!
    private static let decryptURL = URL(string: "https://enc-dec.app/api/dec-videasy")!
    private static let referer = "https://player.videasy.net/"

    private struct Endpoint {
        let name: String
        let path: String
        let language: String?
    }

    /// DE Killjoy (`meine`) is often HTTP 500 upstream — keep it, but always offer EN mirrors.
    private static let endpoints: [Endpoint] = [
        // DE `meine` is frequently HTTP 500 upstream — keep last.
        .init(name: "Yoru · EN", path: "cdn", language: nil),
        .init(name: "Breach · EN", path: "m4uhd", language: nil),
        .init(name: "Vyse · EN", path: "hdmovie", language: nil),
        .init(name: "Cypher · EN", path: "downloader2", language: nil),
        .init(name: "Neon · EN", path: "vsrc", language: nil),
        .init(name: "Killjoy · DE", path: "meine", language: "german"),
    ]

    static func streamSources(
        tmdbId: String,
        title: String,
        mediaType: String,
        year: String,
        imdbId: String?,
        season: Int?,
        episode: Int?
    ) -> [StreamSource] {
        endpoints.map { endpoint in
            StreamSource(
                id: "videasy-\(endpoint.path)",
                name: "\(endpoint.name) (Videasy)",
                url: sourcesURL(
                    endpoint: endpoint.path,
                    tmdbId: tmdbId,
                    title: title,
                    mediaType: mediaType,
                    year: year,
                    imdbId: imdbId,
                    season: season,
                    episode: episode,
                    language: endpoint.language
                ),
                headers: [
                    "User-Agent": HTTPClient.desktopUserAgent,
                    "Referer": referer,
                ],
                resolveKind: .videasy
            )
        }
    }

    static func sourcesURL(
        endpoint: String = "meine",
        tmdbId: String,
        title: String,
        mediaType: String,
        year: String,
        imdbId: String?,
        season: Int?,
        episode: Int?,
        language: String? = "german"
    ) -> URL {
        // Match Android VideasyExtractor path segments: /{endpoint}/sources-with-title
        guard let url = HTTPClient.apiURL(
            base: apiBase,
            path: "\(endpoint)/sources-with-title",
            query: {
                var items: [URLQueryItem] = [
                    .init(name: "title", value: title),
                    .init(name: "mediaType", value: mediaType),
                    .init(name: "year", value: year),
                    .init(name: "tmdbId", value: tmdbId),
                    .init(name: "imdbId", value: imdbId ?? ""),
                ]
                if let language, !language.isEmpty {
                    items.append(.init(name: "language", value: language))
                }
                if let season {
                    items.append(.init(name: "seasonId", value: String(season)))
                }
                if let episode {
                    items.append(.init(name: "episodeId", value: String(episode)))
                }
                return items
            }()
        ) else {
            return apiBase
        }
        return url
    }

    static func resolve(_ sourceURL: URL) async throws -> URL {
        let encData: Data
        do {
            encData = try await HTTPClient.getJSON(
                url: sourceURL,
                headers: [
                    "User-Agent": HTTPClient.desktopUserAgent,
                    "Referer": referer,
                ]
            )
        } catch let error as ProviderError {
            if case .http(let code, _) = error {
                throw ProviderError.parseFailed(
                    "Videasy mirror offline (HTTP \(code)). Pick another source — host scrapes usually work."
                )
            }
            throw error
        }

        let encText = String(data: encData, encoding: .utf8) ?? ""
        let tmdbId = URLComponents(url: sourceURL, resolvingAgainstBaseURL: false)?
            .queryItems?.first(where: { $0.name == "tmdbId" })?.value ?? ""

        let payload: [String: String] = ["text": encText, "id": tmdbId]
        let body = try JSONSerialization.data(withJSONObject: payload)
        let decData = try await HTTPClient.postJSON(url: decryptURL, body: body)
        let root = try JSONSerialization.jsonObject(with: decData) as? [String: Any]
        let resultString = root?["result"] as? String ?? ""
        guard let resultData = resultString.data(using: .utf8),
              let result = try JSONSerialization.jsonObject(with: resultData) as? [String: Any],
              let sources = result["sources"] as? [[String: Any]],
              let first = sources.first,
              let urlString = first["url"] as? String,
              let url = URL(string: urlString) else {
            throw ProviderError.parseFailed("Videasy returned no playable source")
        }
        return url
    }
}
