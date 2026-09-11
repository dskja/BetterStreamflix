import Foundation
import SwiftSoup

/// Port of Android `AnimeWorldProvider` (https://www.animeworld.ac).
/// Uses `allowLenientTLS` to mirror Android trust-all / `buildUnsafe` SSL fallback.
struct AnimeWorldProvider: CatalogProvider {
    let id = "animeworld"
    let name = "AnimeWorld"
    let language = "it"
    let baseURL = URL(string: "https://www.animeworld.ac/")!

    private let lenientTLS = true

    func home() async throws -> [CategoryRow] {
        let html = try await HTTPClient.getHTML(url: baseURL, desktopUA: true, allowLenientTLS: lenientTLS)
        let doc = try SwiftSoup.parse(html, baseURL.absoluteString)
        var rows: [CategoryRow] = []

        let featured = try doc.select("#swiper-container > div.swiper-wrapper div.swiper-slide").array().compactMap { slide -> MediaItem? in
            let href = try slide.selectFirst("a")?.attr("href") ?? ""
            let id = href.split(separator: "/").last.map(String.init) ?? ""
            let title = try slide.selectFirst("a")?.text().trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            guard !id.isEmpty, !title.isEmpty else { return nil }
            let overview = try slide.selectFirst("p")?.text()
            let style = try slide.attr("style")
            let banner = bannerFromStyle(style)
            return MediaItem(
                id: id,
                title: title,
                bannerURL: banner,
                overview: overview,
                kind: .tvShow,
                providerHint: self.id
            )
        }
        let featuredUnique = featured.uniqued(byTitle: true)
        if !featuredUnique.isEmpty {
            rows.append(CategoryRow(id: "featured", title: "Featured", items: featuredUnique, isFeatured: true))
        }

        let widgets: [(Int, String, String)] = [
            (4, "tendenze", "Tendenze"),
            (3, "ultimi-doppiati", "Ultimi episodi (Doppiati)"),
            (2, "ultimi-sub", "Ultimi episodi (Sottotitolati)"),
        ]
        for (nth, rowID, title) in widgets {
            let block = try doc.select("#main div.widget.hotnew > div.widget-body > div:nth-child(\(nth))").array().first
            guard let block else { continue }
            let items = try parseItems(block.select("div.item").array()).uniqued(byTitle: true)
            if !items.isEmpty {
                rows.append(CategoryRow(id: rowID, title: title, items: items))
            }
        }
        if rows.isEmpty {
            throw ProviderError.parseFailed("AnimeWorld: empty home (site layout changed or blocked)")
        }
        return rows
    }

    func search(query: String) async throws -> [MediaItem] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return [] }
        let keyword = trimmed.replacingOccurrences(of: " ", with: "+")
        guard let url = HTTPClient.apiURL(
            base: baseURL,
            path: "search",
            query: [
                URLQueryItem(name: "keyword", value: keyword),
                URLQueryItem(name: "page", value: "1"),
            ]
        ) else {
            throw ProviderError.invalidURL
        }
        let html = try await HTTPClient.getHTML(url: url, referer: baseURL, desktopUA: true, allowLenientTLS: lenientTLS)
        let doc = try SwiftSoup.parse(html, baseURL.absoluteString)
        return try parseItems(doc.select("div.film-list .item").array())
    }

    func detail(id: String, kind: MediaItem.Kind) async throws -> ShowDetail {
        guard let pageURL = HTTPClient.apiURL(base: baseURL, path: "play/\(id)") else {
            throw ProviderError.invalidURL
        }
        let html = try await HTTPClient.getHTML(url: pageURL, referer: baseURL, desktopUA: true, allowLenientTLS: lenientTLS)
        let doc = try SwiftSoup.parse(html, baseURL.absoluteString)

        let title = try doc.selectFirst("#anime-title")?.text()
            .trimmingCharacters(in: .whitespacesAndNewlines) ?? id
        let long = try doc.selectFirst("#main div.widget.info div.info div.desc > div.long")?.text()
        let short = try doc.selectFirst("#main div.widget.info div.info div.desc")?.text()
        let overview = (long?.isEmpty == false ? long : short)?
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let poster = HTTPClient.absoluteURL(
            try doc.selectFirst("#thumbnail-watch > img")?.attr("src"),
            base: baseURL
        )
        let released = try doc.select("#main div.widget.info div.info > div.row > dl:nth-child(1) > dd:nth-child(6)").text()
        let year = released.split(separator: " ").last.map(String.init)
        let ratingText = try doc.select(".rating #average-vote").text()
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let rating = Double(ratingText)
        let genres = try doc.select("#main div.widget.info div.info > div.row > dl:nth-child(1) > dd:nth-child(12) a")
            .array()
            .compactMap { el -> String? in
                try el.text().trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty
            }

        let isMovie = kind == .movie
            || ((try? doc.selectFirst("div.status > div.movie")?.text()) == "Movie")
        if isMovie {
            return ShowDetail(
                id: id,
                title: title,
                overview: overview?.isEmpty == true ? nil : overview,
                posterURL: poster,
                bannerURL: poster,
                year: year,
                rating: rating,
                seasons: [],
                kind: .movie,
                genres: genres
            )
        }

        var seasons = try doc.select("#animeId div.widget-body div.server[data-id=\"9\"] div.range span").array().compactMap { span -> SeasonInfo? in
            let rangeID = try span.attr("data-range-id").trimmingCharacters(in: .whitespacesAndNewlines)
            guard !rangeID.isEmpty else { return nil }
            let number = (Int(rangeID) ?? 0) + 1
            let label = try span.text().trimmingCharacters(in: .whitespacesAndNewlines)
            return SeasonInfo(
                id: "\(id)/\(rangeID)",
                number: number,
                title: label.isEmpty ? "Episodi" : "Episodi: \(label)"
            )
        }
        if seasons.isEmpty {
            seasons = [SeasonInfo(id: "\(id)/", number: 1, title: "Episodi")]
        }

        return ShowDetail(
            id: id,
            title: title,
            overview: overview?.isEmpty == true ? nil : overview,
            posterURL: poster,
            bannerURL: poster,
            year: year,
            rating: rating,
            seasons: seasons,
            kind: .tvShow,
            genres: genres
        )
    }

    func episodes(showId: String, seasonId: String) async throws -> [EpisodeInfo] {
        let parts = seasonId.split(separator: "/", omittingEmptySubsequences: false).map(String.init)
        let animeID = parts.first?.isEmpty == false ? parts[0] : showId
        let range = parts.count > 1 ? parts[1] : ""
        guard let pageURL = HTTPClient.apiURL(base: baseURL, path: "play/\(animeID)") else {
            throw ProviderError.invalidURL
        }
        let html = try await HTTPClient.getHTML(url: pageURL, referer: baseURL, desktopUA: true, allowLenientTLS: lenientTLS)
        let doc = try SwiftSoup.parse(html, baseURL.absoluteString)

        func collect(serverID: String) throws -> [EpisodeInfo] {
            var css = "#animeId > div.widget-body > div[data-id=\"\(serverID)\"]"
            if !range.isEmpty {
                css += " ul.episodes.range[data-range-id=\"\(range)\"]"
            }
            css += " a"
            return try doc.select(css).array().compactMap { a in
                let eid = try a.attr("data-episode-id").trimmingCharacters(in: .whitespacesAndNewlines)
                guard !eid.isEmpty else { return nil }
                let text = try a.text().trimmingCharacters(in: .whitespacesAndNewlines)
                let number = Int(text.split(separator: "-").first?
                    .split(separator: ".").first?
                    .trimmingCharacters(in: .whitespacesAndNewlines) ?? "") ?? 0
                return EpisodeInfo(id: "\(animeID)/\(eid)", number: number, title: text.isEmpty ? "Ep \(number)" : text)
            }
        }

        let aw = try collect(serverID: "9")
        if !aw.isEmpty { return aw }
        return try collect(serverID: "8")
    }

    func streams(showId: String, seasonId: String?, episodeId: String?, detail: ShowDetail?) async throws -> [StreamSource] {
        let animeID: String
        let serverEpisodeID: String

        if let episodeId, episodeId.contains("/") {
            let parts = episodeId.split(separator: "/")
            animeID = String(parts[0])
            serverEpisodeID = parts.count > 1 ? String(parts[1]) : ""
        } else {
            animeID = showId
            // Movie: pick first AW / Streamtape episode id from detail page.
            guard let pageURL = HTTPClient.apiURL(base: baseURL, path: "play/\(animeID)") else {
                throw ProviderError.invalidURL
            }
            let html = try await HTTPClient.getHTML(url: pageURL, referer: baseURL, desktopUA: true, allowLenientTLS: lenientTLS)
            let doc = try SwiftSoup.parse(html, baseURL.absoluteString)
            let awID = try doc.select("#animeId > div.widget-body > div[data-id=\"9\"] a").array().first?
                .attr("data-episode-id")
                .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            if !awID.isEmpty {
                serverEpisodeID = awID
            } else {
                serverEpisodeID = try doc.select("#animeId > div.widget-body > div[data-id=\"8\"] a").array().first?
                    .attr("data-episode-id")
                    .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            }
        }

        guard !serverEpisodeID.isEmpty,
              let pageURL = HTTPClient.apiURL(base: baseURL, path: "play/\(animeID)") else {
            throw ProviderError.parseFailed("AnimeWorld: missing episode id")
        }
        let html = try await HTTPClient.getHTML(url: pageURL, referer: baseURL, desktopUA: true, allowLenientTLS: lenientTLS)
        let doc = try SwiftSoup.parse(html, baseURL.absoluteString)

        var sources: [StreamSource] = []
        for server in try doc.select("#animeId div.widget-body div.server").array() {
            let dataServer = try server.attr("data-id")
            let linkID = try server.selectFirst("a[data-episode-id=\"\(serverEpisodeID)\"]")?.attr("data-id")
                .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            guard !linkID.isEmpty else { continue }
            let name: String
            switch dataServer {
            case "9": name = "AnimeWorld Server"
            case "8": name = "Streamtape"
            default: name = "Server \(dataServer)"
            }
            guard let infoURL = HTTPClient.apiURL(
                base: baseURL,
                path: "api/episode/info",
                query: [
                    URLQueryItem(name: "id", value: linkID),
                    URLQueryItem(name: "alt", value: "0"),
                ]
            ),
            let data = try? await HTTPClient.getJSON(
                url: infoURL,
                headers: [
                    "User-Agent": HTTPClient.desktopUserAgent,
                    "Referer": pageURL.absoluteString,
                    "Accept": "application/json",
                ]
            ),
            let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
            var grabber = (json["grabber"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines),
            !grabber.isEmpty else { continue }

            if name == "Streamtape", grabber.contains("/") {
                // Android: substringBeforeLast("/")
                if let lastSlash = grabber.lastIndex(of: "/") {
                    grabber = String(grabber[..<lastSlash])
                }
            }
            guard let streamURL = URL(string: grabber) else { continue }
            sources.append(
                StreamSource(
                    id: "aw-\(linkID)",
                    name: name,
                    url: streamURL,
                    headers: [
                        "User-Agent": HTTPClient.desktopUserAgent,
                        "Referer": baseURL.absoluteString,
                    ],
                    resolveKind: name == "AnimeWorld Server" ? .direct : .followRedirect
                )
            )
        }
        if sources.isEmpty {
            throw ProviderError.parseFailed("AnimeWorld: no stream servers")
        }
        return sources
    }

    // MARK: - Helpers

    private func parseItems(_ elements: [Element]) throws -> [MediaItem] {
        var items: [MediaItem] = []
        var seen = Set<String>()
        for el in elements {
            let href = try el.selectFirst("a")?.attr("href") ?? ""
            // Android uses substringBeforeLast("/").substringAfterLast("/") for list cards,
            // and substringAfterLast("/") for search — both yield the play slug.
            let id = href.split(separator: "/").last.map(String.init) ?? ""
            guard !id.isEmpty, seen.insert(id).inserted else { continue }
            let title = try el.selectFirst("a.name")?.text()
                .trimmingCharacters(in: .whitespacesAndNewlines)
                ?? el.selectFirst("a")?.text().trimmingCharacters(in: .whitespacesAndNewlines)
                ?? ""
            guard !title.isEmpty else { continue }
            let poster = HTTPClient.absoluteURL(try el.selectFirst("img")?.attr("src"), base: baseURL)
            let isMovie = try el.selectFirst("div.status > div.movie")?.text() == "Movie"
            items.append(
                MediaItem(
                    id: id,
                    title: title,
                    posterURL: poster,
                    kind: isMovie ? .movie : .tvShow,
                    providerHint: self.id
                )
            )
        }
        return items
    }

    private func bannerFromStyle(_ style: String) -> URL? {
        guard let start = style.range(of: "url(")?.upperBound,
              let end = style[start...].range(of: ")")?.lowerBound else { return nil }
        let raw = String(style[start..<end]).trimmingCharacters(in: CharacterSet(charactersIn: "\"' "))
        return HTTPClient.absoluteURL(raw, base: baseURL)
    }
}

private extension Array where Element == MediaItem {
    func uniqued(byTitle: Bool) -> [MediaItem] {
        var seen = Set<String>()
        return filter { item in
            let key = byTitle ? item.title.lowercased() : item.id
            return seen.insert(key).inserted
        }
    }
}

private extension String {
    var nilIfEmpty: String? {
        let t = trimmingCharacters(in: .whitespacesAndNewlines)
        return t.isEmpty ? nil : t
    }
}
