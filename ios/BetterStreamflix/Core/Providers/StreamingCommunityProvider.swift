import Foundation
import SwiftSoup

/// Port of Android `StreamingCommunityProvider` — Inertia/JSON catalog for `it` and `en`.
/// Uses `allowLenientTLS` to mirror Android `NetworkClient.trustAll` / `buildUnsafe` SSL fallback.
struct StreamingCommunityProvider: CatalogProvider {
    let language: String
    private let langCode: String
    /// Live mirror (`.cc` 301 → `.win`). Android resolves redirects once and caches the host.
    private static let defaultDomain = "streamingunity.win"
    private static let seedDomains = ["streamingunity.win", "streamingunity.cc"]
    private static let domainBox = DomainBox(defaultDomain)

    var id: String { language == "en" ? "streamingcommunity-en" : "streamingcommunity-it" }
    var name: String { language == "en" ? "StreamingCommunity (EN)" : "StreamingCommunity" }
    var baseURL: URL { URL(string: "https://\(Self.domainBox.host)/")! }

    private let lenientTLS = true

    init(language: String = "it") {
        self.language = language == "en" ? "en" : "it"
        self.langCode = self.language
    }

    func home() async throws -> [CategoryRow] {
        try await resolveDomainIfNeeded()
        let page = try await fetchInertiaHome()
        guard let props = page["props"] as? [String: Any] else {
            throw ProviderError.parseFailed("StreamingCommunity: missing props")
        }

        var rows: [CategoryRow] = []
        var usedNames = Set<String>()

        let sliders = (props["sliders"] as? [[String: Any]]) ?? []
        let hero = sliders.first { ($0["name"] as? String) == "hero" } ?? sliders.first
        if let hero, let titles = hero["titles"] as? [[String: Any]] {
            let items = mapShows(titles).prefix(10).map { $0 }
            if !items.isEmpty {
                rows.append(CategoryRow(id: "featured", title: "Featured", items: Array(items), isFeatured: true))
                if let name = hero["name"] as? String { usedNames.insert(name) }
            }
        }

        for slider in sliders {
            let name = (slider["name"] as? String) ?? ""
            guard !usedNames.contains(name),
                  let titles = slider["titles"] as? [[String: Any]], !titles.isEmpty else { continue }
            let display = sliderDisplayName(sliderName: name, label: slider["label"] as? String)
            let items = mapShows(titles)
            guard !items.isEmpty else { continue }
            usedNames.insert(name)
            rows.append(CategoryRow(id: name, title: display, items: items))
        }

        let propRows: [(String, String, Any?)] = [
            ("trending", "I titoli del momento", props["trending_titles"] ?? props["trending"]),
            ("latest-movies", "Film aggiunti di recente", props["latest_movies"]),
            ("latest-tv", "Serie TV aggiunte di recente", props["latest_tv_shows"]),
            ("top10", "Top 10 titoli di oggi", props["top_10_titles"] ?? props["top_10"]),
            ("upcoming", "In arrivo", props["upcoming_titles"] ?? props["upcoming"]),
        ]
        for (id, title, value) in propRows {
            guard let list = value as? [[String: Any]], !list.isEmpty else { continue }
            if rows.contains(where: { $0.title == title }) { continue }
            let items = mapShows(list)
            if !items.isEmpty {
                rows.append(CategoryRow(id: id, title: title, items: items))
            }
        }

        return rows.filter { !$0.items.isEmpty }
    }

    func search(query: String) async throws -> [MediaItem] {
        try await resolveDomainIfNeeded()
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return [] }
        guard let url = HTTPClient.apiURL(
            base: langBaseURL,
            path: "search",
            query: [
                URLQueryItem(name: "q", value: trimmed),
                URLQueryItem(name: "page", value: "1"),
                URLQueryItem(name: "lang", value: langCode),
            ]
        ) else {
            throw ProviderError.invalidURL
        }
        let data = try await HTTPClient.getJSON(url: url, headers: apiHeaders)
        let root = try JSONSerialization.jsonObject(with: data) as? [String: Any] ?? [:]
        let list = (root["data"] as? [[String: Any]]) ?? []
        return mapShows(list)
    }

    func detail(id: String, kind: MediaItem.Kind) async throws -> ShowDetail {
        try await resolveDomainIfNeeded()
        let page = try await fetchTitleDetails(id: id)
        guard let props = page["props"] as? [String: Any],
              let titleObj = props["title"] as? [String: Any] else {
            throw ProviderError.parseFailed("StreamingCommunity: title missing")
        }

        let name = (titleObj["name"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? id
        let plot = (titleObj["plot"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines)
        let images = (titleObj["images"] as? [[String: Any]]) ?? []
        let poster = imageURL(images, type: "poster")
        let banner = imageURL(images, type: "background") ?? poster
        let year = (titleObj["last_air_date"] as? String).flatMap { String($0.prefix(4)) }
        let rating = (titleObj["score"] as? String).flatMap { Double($0) }
            ?? (titleObj["score"] as? Double)
        let genres = ((titleObj["genres"] as? [[String: Any]]) ?? []).compactMap { $0["name"] as? String }
        let cast = ((titleObj["main_actors"] as? [[String: Any]]) ?? []).compactMap { $0["name"] as? String }
        let type = (titleObj["type"] as? String) ?? (kind == .movie ? "movie" : "tv")

        if type == "movie" || kind == .movie {
            return ShowDetail(
                id: id,
                title: name,
                overview: plot?.isEmpty == true ? nil : plot,
                posterURL: poster,
                bannerURL: banner,
                year: year,
                rating: rating,
                seasons: [],
                kind: .movie,
                genres: genres,
                cast: cast
            )
        }

        let seasonsRaw = (titleObj["seasons"] as? [[String: Any]]) ?? []
        let seasons: [SeasonInfo] = seasonsRaw.enumerated().compactMap { idx, s in
            let numberStr = (s["number"] as? String) ?? "\(idx + 1)"
            let number = Int(numberStr) ?? (idx + 1)
            let seasonName = (s["name"] as? String) ?? "Season \(number)"
            return SeasonInfo(id: "\(id)/season-\(numberStr)", number: number, title: seasonName)
        }

        return ShowDetail(
            id: id,
            title: name,
            overview: plot?.isEmpty == true ? nil : plot,
            posterURL: poster,
            bannerURL: banner,
            year: year,
            rating: rating,
            seasons: seasons,
            kind: .tvShow,
            genres: genres,
            cast: cast
        )
    }

    func episodes(showId: String, seasonId: String) async throws -> [EpisodeInfo] {
        try await resolveDomainIfNeeded()
        let path = seasonId.contains("/") ? seasonId : "\(showId)/\(seasonId)"
        let page = try await fetchInertiaJSON(path: "titles/\(path)")
        guard let props = page["props"] as? [String: Any],
              let loaded = props["loadedSeason"] as? [String: Any],
              let episodes = loaded["episodes"] as? [[String: Any]] else {
            return []
        }
        let showKey = seasonId.split(separator: "/").first.map(String.init) ?? showId
        return episodes.enumerated().compactMap { idx, ep in
            let eid = stringID(ep["id"])
            guard !eid.isEmpty else { return nil }
            let number = Int(stringID(ep["number"])) ?? (idx + 1)
            let title = (ep["name"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? "Episode \(number)"
            let overview = (ep["plot"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines)
            let images = (ep["images"] as? [[String: Any]]) ?? []
            return EpisodeInfo(
                id: "\(showKey)?episode_id=\(eid)",
                number: number,
                title: title,
                overview: overview?.isEmpty == true ? nil : overview,
                thumbnailURL: imageURL(images, type: "cover")
            )
        }
    }

    func streams(showId: String, seasonId: String?, episodeId: String?, detail: ShowDetail?) async throws -> [StreamSource] {
        try await resolveDomainIfNeeded()
        let streamID = episodeId?.isEmpty == false ? episodeId! : showId
        let iframePath: String
        if streamID.contains("?episode_id=") {
            let base = streamID.split(separator: "?").first.map(String.init) ?? streamID
            let eid = streamID.split(separator: "=").last.map(String.init) ?? ""
            iframePath = "\(langCode)/iframe/\(base)?episode_id=\(eid)&next_episode=1&language=\(langCode)"
        } else {
            let numeric = streamID.split(separator: "-").first.map(String.init) ?? streamID
            iframePath = "\(langCode)/iframe/\(numeric)?language=\(langCode)"
        }
        guard let iframeURL = HTTPClient.apiURL(base: baseURL, path: iframePath) else {
            throw ProviderError.invalidURL
        }
        let html = try await fetchHTML(url: iframeURL)
        let doc = try SwiftSoup.parse(html, baseURL.absoluteString)
        let src = try doc.selectFirst("iframe")?.attr("src")
            .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        guard !src.isEmpty, let embedURL = HTTPClient.absoluteURL(src, base: baseURL) else {
            throw ProviderError.parseFailed("StreamingCommunity: no iframe stream")
        }
        return [
            StreamSource(
                id: "sc-vixcloud",
                name: "Vixcloud",
                url: embedURL,
                headers: [
                    "User-Agent": HTTPClient.desktopUserAgent,
                    "Referer": iframeURL.absoluteString,
                    "Accept-Language": acceptLanguage,
                    "Cookie": "language=\(langCode)",
                ],
                resolveKind: .followRedirect
            ),
        ]
    }

    // MARK: - Networking / Inertia

    private var activeDomain: String { Self.domainBox.host }

    private var langBaseURL: URL {
        URL(string: "https://\(activeDomain)/\(langCode)/")!
    }

    /// Follow Android `resolveFinalBaseUrl` — pick the post-redirect host once.
    private func resolveDomainIfNeeded() async throws {
        if Self.domainBox.resolved { return }
        for seed in Self.seedDomains {
            guard let start = URL(string: "https://\(seed)/") else { continue }
            do {
                let final = try await HTTPClient.followRedirects(
                    url: start,
                    headers: [
                        "User-Agent": HTTPClient.desktopUserAgent,
                        "Accept-Language": acceptLanguage,
                    ]
                )
                let host = final.host()?.lowercased() ?? seed
                Self.domainBox.host = host
                Self.domainBox.resolved = true
                return
            } catch {
                continue
            }
        }
        Self.domainBox.host = Self.defaultDomain
        Self.domainBox.resolved = true
    }

    private var acceptLanguage: String {
        language == "en" ? "en-US,en;q=0.9" : "it-IT,it;q=0.9"
    }

    private var apiHeaders: [String: String] {
        [
            "User-Agent": HTTPClient.desktopUserAgent,
            "Accept": "application/json, text/plain, */*",
            "Accept-Language": acceptLanguage,
            "Cookie": "language=\(langCode)",
            "Referer": langBaseURL.absoluteString,
            "X-Requested-With": "XMLHttpRequest",
        ]
    }

    private func fetchInertiaHome() async throws -> [String: Any] {
        if let json = try? await fetchInertiaJSON(path: ""), !json.isEmpty {
            return json
        }
        let html = try await HTTPClient.getHTML(url: langBaseURL, desktopUA: true, allowLenientTLS: lenientTLS)
        return try parseInertiaPage(html: html)
    }

    private func fetchTitleDetails(id: String) async throws -> [String: Any] {
        if let json = try? await fetchInertiaJSON(path: "titles/\(id)"), json["props"] != nil {
            return json
        }
        guard let url = HTTPClient.apiURL(base: langBaseURL, path: "titles/\(id)") else {
            throw ProviderError.invalidURL
        }
        let html = try await HTTPClient.getHTML(url: url, referer: langBaseURL, desktopUA: true, allowLenientTLS: lenientTLS)
        return try parseInertiaPage(html: html)
    }

    private func fetchInertiaJSON(path: String) async throws -> [String: Any] {
        let url: URL
        if path.isEmpty {
            url = langBaseURL
        } else if let built = HTTPClient.apiURL(base: langBaseURL, path: path) {
            url = built
        } else {
            throw ProviderError.invalidURL
        }
        var headers = apiHeaders
        headers["x-inertia"] = "true"
        headers["Accept"] = "application/json, text/plain, */*"
        // Version optional — empty still works on many mirrors; HTML fallback covers failures.
        let data = try await HTTPClient.getJSON(url: url, headers: headers)
        if let obj = try JSONSerialization.jsonObject(with: data) as? [String: Any] {
            return obj
        }
        // Sometimes returns HTML with data-page
        if let html = String(data: data, encoding: .utf8) {
            return try parseInertiaPage(html: html)
        }
        throw ProviderError.emptyResponse
    }

    private func fetchHTML(url: URL) async throws -> String {
        // Prefer JSON path with Cookie headers when possible; fall back to getHTML + lenient TLS.
        if let data = try? await HTTPClient.getJSON(
            url: url,
            headers: [
                "User-Agent": HTTPClient.desktopUserAgent,
                "Accept": "text/html,application/xhtml+xml",
                "Accept-Language": acceptLanguage,
                "Cookie": "language=\(langCode)",
                "Referer": langBaseURL.absoluteString,
                "X-Requested-With": "XMLHttpRequest",
            ]
        ), let html = String(data: data, encoding: .utf8), !html.isEmpty {
            return html
        }
        return try await HTTPClient.getHTML(url: url, referer: langBaseURL, desktopUA: true, allowLenientTLS: lenientTLS)
    }

    private func parseInertiaPage(html: String) throws -> [String: Any] {
        let doc = try SwiftSoup.parse(html)
        let raw = try doc.selectFirst("#app")?.attr("data-page")
            .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        guard !raw.isEmpty else { throw ProviderError.parseFailed("No Inertia data-page") }
        let unescaped = raw
            .replacingOccurrences(of: "&quot;", with: "\"")
            .replacingOccurrences(of: "&amp;", with: "&")
            .replacingOccurrences(of: "&#039;", with: "'")
            .replacingOccurrences(of: "&lt;", with: "<")
            .replacingOccurrences(of: "&gt;", with: ">")
        guard let data = unescaped.data(using: .utf8),
              let obj = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw ProviderError.parseFailed("Inertia JSON decode failed")
        }
        return obj
    }

    // MARK: - Mapping

    private func mapShows(_ list: [[String: Any]]) -> [MediaItem] {
        var items: [MediaItem] = []
        var seen = Set<String>()
        for show in list {
            let sid = stringID(show["id"])
            let slug = (show["slug"] as? String) ?? ""
            let mediaID = slug.isEmpty ? sid : "\(sid)-\(slug)"
            guard !sid.isEmpty, seen.insert(mediaID).inserted else { continue }
            let title = (show["name"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            guard !title.isEmpty else { continue }
            let images = (show["images"] as? [[String: Any]]) ?? []
            let type = (show["type"] as? String) ?? "tv"
            let year = (show["last_air_date"] as? String).flatMap { String($0.prefix(4)) }
            let rating = (show["score"] as? String).flatMap { Double($0) } ?? (show["score"] as? Double)
            items.append(
                MediaItem(
                    id: mediaID,
                    title: title,
                    posterURL: imageURL(images, type: "poster"),
                    bannerURL: imageURL(images, type: "background"),
                    year: year,
                    rating: rating,
                    kind: type == "movie" ? .movie : .tvShow,
                    providerHint: self.id
                )
            )
        }
        return items
    }

    private func imageURL(_ images: [[String: Any]], type: String) -> URL? {
        guard let filename = images.first(where: { ($0["type"] as? String) == type })?["filename"] as? String,
              !filename.isEmpty else { return nil }
        return URL(string: "https://cdn.\(activeDomain)/images/\(filename)")
    }

    private func sliderDisplayName(sliderName: String, label: String?) -> String {
        let lower = sliderName.lowercased()
        if lower.contains("trending") { return "I titoli del momento" }
        if lower.contains("latest-movies") { return "Film aggiunti di recente" }
        if lower.contains("latest-tv-shows") { return "Serie TV aggiunte di recente" }
        if lower.contains("top-10") { return "Top 10 titoli di oggi" }
        if lower.contains("upcoming") { return "In arrivo" }
        if lower.contains("new-releases") { return "Nuove uscite" }
        if let label {
            let trimmed = label.trimmingCharacters(in: .whitespacesAndNewlines)
            if !trimmed.isEmpty { return trimmed }
        }
        return sliderName
    }

    private func stringID(_ value: Any?) -> String {
        if let s = value as? String { return s }
        if let n = value as? Int { return String(n) }
        if let n = value as? NSNumber { return n.stringValue }
        return ""
    }
}

private final class DomainBox: @unchecked Sendable {
    private let lock = NSLock()
    private var _host: String
    private var _resolved = false

    init(_ host: String) { _host = host }

    var host: String {
        get { lock.lock(); defer { lock.unlock() }; return _host }
        set { lock.lock(); _host = newValue; lock.unlock() }
    }

    var resolved: Bool {
        get { lock.lock(); defer { lock.unlock() }; return _resolved }
        set { lock.lock(); _resolved = newValue; lock.unlock() }
    }
}
