import Foundation

/// Port of Android `EinschaltenProvider` (https://einschalten.in) — movies via JSON API.
struct EinschaltenProvider: CatalogProvider {
    let id = "einschalten"
    let name = "Einschalten"
    let language = "de"
    let baseURL = URL(string: "https://einschalten.in/")!

    private var jsonHeaders: [String: String] {
        [
            "User-Agent": HTTPClient.desktopUserAgent,
            "Content-Type": "application/json",
            "Accept": "application/json",
        ]
    }

    func home() async throws -> [CategoryRow] {
        var rows: [CategoryRow] = []
        if let neue = try? await fetchMovies(order: "new"), !neue.isEmpty {
            rows.append(CategoryRow(id: "new", title: "Neue Filme", items: neue))
        }
        if let added = try? await fetchMovies(order: "added"), !added.isEmpty {
            rows.append(CategoryRow(id: "added", title: "Zuletzt hinzugefügte Filme", items: added))
        }
        if rows.isEmpty {
            throw ProviderError.parseFailed("Einschalten unreachable")
        }
        return rows
    }

    func search(query: String) async throws -> [MediaItem] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return [] }
        let payload: [String: Any] = [
            "query": trimmed,
            "pageSize": 32,
            "pageNumber": 1,
        ]
        let body = try JSONSerialization.data(withJSONObject: payload)
        guard let url = HTTPClient.apiURL(base: baseURL, path: "api/search") else {
            throw ProviderError.invalidURL
        }
        let data = try await HTTPClient.postJSON(url: url, body: body, headers: jsonHeaders)
        return try parseMoviesArray(from: data)
    }

    func detail(id: String, kind: MediaItem.Kind) async throws -> ShowDetail {
        guard kind == .movie else { throw ProviderError.unsupported }
        guard let url = HTTPClient.apiURL(base: baseURL, path: "api/movies/\(id)") else {
            throw ProviderError.invalidURL
        }
        let data = try await HTTPClient.getJSON(url: url, headers: jsonHeaders)
        let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] ?? [:]
        let title = (json["title"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? id
        let overview = (json["overview"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines)
        let posterPath = json["posterPath"] as? String ?? ""
        let releaseDate = json["releaseDate"] as? String
        let year = releaseDate.flatMap { String($0.prefix(4)) }
        let rating = json["voteAverage"] as? Double
        let genres = ((json["genres"] as? [[String: Any]]) ?? []).compactMap { $0["name"] as? String }
        return ShowDetail(
            id: id,
            title: title.isEmpty ? id : title,
            overview: overview?.isEmpty == true ? nil : overview,
            posterURL: posterURL(movieId: id, posterPath: posterPath),
            bannerURL: posterURL(movieId: id, posterPath: posterPath),
            year: year,
            rating: (rating ?? 0) > 0 ? rating : nil,
            seasons: [],
            kind: .movie,
            genres: genres
        )
    }

    func episodes(showId: String, seasonId: String) async throws -> [EpisodeInfo] {
        throw ProviderError.unsupported
    }

    func streams(showId: String, seasonId: String?, episodeId: String?, detail: ShowDetail?) async throws -> [StreamSource] {
        guard let url = HTTPClient.apiURL(base: baseURL, path: "api/movies/\(showId)/watch") else {
            throw ProviderError.invalidURL
        }
        let data = try await HTTPClient.getJSON(url: url, headers: jsonHeaders)
        let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] ?? [:]
        let streamURLString = (json["streamUrl"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        guard !streamURLString.isEmpty, let streamURL = URL(string: streamURLString) else {
            throw ProviderError.parseFailed("Kein Stream von Einschalten")
        }
        return [
            StreamSource(
                id: "einschalten-dood",
                name: "DoodStream",
                url: streamURL,
                headers: [
                    "User-Agent": HTTPClient.desktopUserAgent,
                    "Referer": baseURL.absoluteString,
                ],
                resolveKind: .followRedirect
            ),
        ]
    }

    // MARK: - Helpers

    private func fetchMovies(order: String) async throws -> [MediaItem] {
        guard let url = HTTPClient.apiURL(
            base: baseURL,
            path: "api/movies",
            query: [
                URLQueryItem(name: "pageNumber", value: "1"),
                URLQueryItem(name: "order", value: order),
            ]
        ) else {
            throw ProviderError.invalidURL
        }
        let data = try await HTTPClient.getJSON(url: url, headers: jsonHeaders)
        return try parseMoviesArray(from: data)
    }

    private func parseMoviesArray(from data: Data) throws -> [MediaItem] {
        let root = try JSONSerialization.jsonObject(with: data)
        let array: [[String: Any]]
        if let dict = root as? [String: Any], let dataArr = dict["data"] as? [[String: Any]] {
            array = dataArr
        } else if let dataArr = root as? [[String: Any]] {
            array = dataArr
        } else {
            return []
        }
        var items: [MediaItem] = []
        var seen = Set<String>()
        for obj in array {
            let movieId: String
            if let n = obj["id"] as? Int {
                movieId = String(n)
            } else if let s = obj["id"] as? String {
                movieId = s
            } else {
                continue
            }
            let title = (obj["title"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            guard !movieId.isEmpty, !title.isEmpty, seen.insert(movieId).inserted else { continue }
            let posterPath = obj["posterPath"] as? String ?? ""
            items.append(
                MediaItem(
                    id: movieId,
                    title: title,
                    posterURL: posterURL(movieId: movieId, posterPath: posterPath),
                    kind: .movie,
                    providerHint: self.id
                )
            )
        }
        return items
    }

    private func posterURL(movieId: String, posterPath: String) -> URL? {
        if !posterPath.isEmpty {
            return HTTPClient.absoluteURL("api/image/poster\(posterPath)", base: baseURL)
        }
        return nil
    }
}
