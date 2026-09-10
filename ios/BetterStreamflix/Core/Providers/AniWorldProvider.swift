import Foundation
import SwiftSoup

struct AniWorldProvider: CatalogProvider {
    let id = "aniworld"
    let name = "AniWorld"
    let language = "de"
    let baseURL = URL(string: "https://aniworld.to/")!

    func home() async throws -> [CategoryRow] {
        let html = try await HTTPClient.getHTML(url: baseURL)
        let doc = try SwiftSoup.parse(html, baseURL.absoluteString)
        var rows: [CategoryRow] = []

        let popularURL = URL(string: "beliebte-animes", relativeTo: baseURL)!.absoluteURL
        if let popularHTML = try? await HTTPClient.getHTML(url: popularURL, referer: baseURL),
           let popularDoc = try? SwiftSoup.parse(popularHTML, baseURL.absoluteString) {
            let items = try parseCards(popularDoc.select("a[href*=/anime/stream/]").array())
            if !items.isEmpty {
                rows.append(CategoryRow(id: "popular", title: "Beliebte Animes", items: items))
            }
        }

        let featured = try parseCards(doc.select("a[href*=/anime/stream/]").array().prefix(24).map { $0 })
        if !featured.isEmpty {
            rows.append(CategoryRow(id: "home", title: "Neu & Entdecken", items: featured))
        }
        return rows
    }

    func search(query: String) async throws -> [MediaItem] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return [] }
        let encoded = trimmed.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? trimmed
        let url = URL(string: "search?q=\(encoded)", relativeTo: baseURL)!.absoluteURL
        let html = try await HTTPClient.getHTML(url: url, referer: baseURL)
        let doc = try SwiftSoup.parse(html, baseURL.absoluteString)
        return try parseCards(doc.select("a[href*=/anime/stream/]").array())
    }

    func detail(id: String, kind: MediaItem.Kind) async throws -> ShowDetail {
        let url = URL(string: "anime/stream/\(id)", relativeTo: baseURL)!.absoluteURL
        let html = try await HTTPClient.getHTML(url: url, referer: baseURL)
        let doc = try SwiftSoup.parse(html, baseURL.absoluteString)
        let title = try doc.selectFirst("h1 > span, h1")?.text()
            ?? id.replacingOccurrences(of: "-", with: " ").capitalized
        let overview = try doc.selectFirst("p.seri_des")?.attr("data-full-description")
            .ifBlank(try doc.selectFirst("p.seri_des")?.text())
        let poster = HTTPClient.absoluteURL(
            try doc.selectFirst("div.seriesCoverBox img")?.attr("data-src")
                ?? doc.selectFirst("div.seriesCoverBox img")?.attr("src"),
            base: baseURL
        )
        var seasons: [SeasonInfo] = []
        let seasonLinks = try doc.select("#stream > ul:nth-child(1) > li a").array()
        for (idx, link) in seasonLinks.enumerated() {
            let text = try link.text()
            let href = try link.attr("href")
            let number: Int = {
                if text.localizedCaseInsensitiveContains("filme") || text.localizedCaseInsensitiveContains("special") {
                    return 0
                }
                return Int(text.filter(\.isNumber)) ?? (idx + 1)
            }()
            let seasonPath = href.replacingOccurrences(of: "/anime/stream/", with: "").trimmingCharacters(in: CharacterSet(charactersIn: "/"))
            seasons.append(
                SeasonInfo(
                    id: seasonPath.isEmpty ? "\(id)/staffel-\(max(number, 1))" : seasonPath,
                    number: number,
                    title: text.isEmpty ? "Season \(number)" : text
                )
            )
        }
        if seasons.isEmpty {
            seasons = [SeasonInfo(id: "\(id)/staffel-1", number: 1, title: "Season 1")]
        }
        return ShowDetail(
            id: id,
            title: title,
            overview: overview,
            posterURL: poster,
            bannerURL: poster,
            year: try doc.selectFirst("div.series-title small span")?.text(),
            rating: nil,
            seasons: seasons,
            kind: .tvShow
        )
    }

    func episodes(showId: String, seasonId: String) async throws -> [EpisodeInfo] {
        let path = seasonId.contains("/") ? seasonId : "\(showId)/\(seasonId)"
        let url = URL(string: "anime/stream/\(path)", relativeTo: baseURL)!.absoluteURL
        let html = try await HTTPClient.getHTML(url: url, referer: baseURL)
        let doc = try SwiftSoup.parse(html, baseURL.absoluteString)
        var episodes: [EpisodeInfo] = []
        let links = try doc.select("table.episodes tr a, a[href*=/episode-]").array()
        for (idx, link) in links.enumerated() {
            let href = try link.attr("href")
            let title = try link.text().trimmingCharacters(in: .whitespacesAndNewlines)
            let epPath = href.replacingOccurrences(of: "/anime/stream/", with: "").trimmingCharacters(in: CharacterSet(charactersIn: "/"))
            guard !epPath.isEmpty else { continue }
            if episodes.contains(where: { $0.id == epPath }) { continue }
            episodes.append(
                EpisodeInfo(
                    id: epPath,
                    number: idx + 1,
                    title: title.isEmpty ? "Episode \(idx + 1)" : title,
                    overview: nil,
                    thumbnailURL: nil
                )
            )
        }
        if episodes.isEmpty {
            episodes = [EpisodeInfo(id: "\(path)/episode-1", number: 1, title: "Episode 1", overview: nil, thumbnailURL: nil)]
        }
        return episodes
    }

    func streams(showId: String, seasonId: String?, episodeId: String?) async throws -> [StreamSource] {
        let path = episodeId ?? seasonId ?? showId
        let url = URL(string: "anime/stream/\(path)", relativeTo: baseURL)!.absoluteURL
        let html = try await HTTPClient.getHTML(url: url, referer: baseURL)
        let doc = try SwiftSoup.parse(html, baseURL.absoluteString)
        var sources: [StreamSource] = []
        let hosts = try doc.select("a[href*=/redirect/], li[data-link-id] a, .hosterSiteVideo a").array()
        for (idx, host) in hosts.prefix(12).enumerated() {
            let href = try host.attr("abs:href")
            let name = try host.text().trimmingCharacters(in: .whitespacesAndNewlines)
            guard let streamURL = URL(string: href), !href.isEmpty else { continue }
            sources.append(
                StreamSource(
                    id: "\(idx)-\(streamURL.host ?? "host")",
                    name: name.isEmpty ? "Host \(idx + 1)" : name,
                    url: streamURL,
                    headers: ["User-Agent": HTTPClient.userAgent, "Referer": baseURL.absoluteString]
                )
            )
        }
        return sources
    }

    private func parseCards(_ elements: [Element]) throws -> [MediaItem] {
        var items: [MediaItem] = []
        var seen = Set<String>()
        for el in elements {
            let href = try el.attr("href")
            guard let id = extractAnimeId(from: href), seen.insert(id).inserted else { continue }
            let rawTitle = try el.selectFirst("h3")?.text()
                ?? el.attr("title")
                .ifBlank(try el.text())
            let title = rawTitle
                .replacingOccurrences(of: #"\s*stream online.*$"#, with: "", options: .regularExpression)
                .trimmingCharacters(in: .whitespacesAndNewlines)
            let img = try el.selectFirst("img")
            let poster = HTTPClient.absoluteURL(
                try img?.attr("data-src").ifBlank(try img?.attr("src")),
                base: baseURL
            )
            items.append(
                MediaItem(
                    id: id,
                    title: title.ifBlank(id.replacingOccurrences(of: "-", with: " ").capitalized),
                    posterURL: poster,
                    bannerURL: nil,
                    overview: nil,
                    year: nil,
                    rating: nil,
                    kind: .tvShow
                )
            )
        }
        return items
    }

    private func extractAnimeId(from href: String) -> String? {
        guard let range = href.range(of: "/anime/stream/") else { return nil }
        let rest = href[range.upperBound...]
        let slug = rest.split(separator: "/").first.map(String.init) ?? ""
        return (slug.isEmpty || slug == "stream") ? nil : slug
    }
}

private extension String {
    func ifBlank(_ fallback: String?) -> String {
        let trimmed = trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty { return fallback?.trimmingCharacters(in: .whitespacesAndNewlines) ?? "" }
        return trimmed
    }
}

private extension Optional where Wrapped == String {
    func ifBlank(_ fallback: String?) -> String? {
        guard let self else { return fallback }
        return self.ifBlank(fallback)
    }
}
