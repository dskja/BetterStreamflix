import Foundation
import SwiftSoup

struct SerienStreamProvider: CatalogProvider {
    let id = "serienstream"
    let name = "SerienStream"
    let language = "de"
    let baseURL = URL(string: "https://serienstream.to/")!

    func home() async throws -> [CategoryRow] {
        let html = try await HTTPClient.getHTML(url: baseURL)
        let doc = try SwiftSoup.parse(html, baseURL.absoluteString)
        var rows: [CategoryRow] = []

        let featured = try parseCards(
            doc.select(".home-hero-slide, .seriesListContainer a[href*=/serie/]").array().prefix(12).map { $0 }
        )
        if !featured.isEmpty {
            rows.append(CategoryRow(id: "featured", title: "Featured", items: featured))
        }

        // Section titles + following lists
        let headings = try doc.select("h2, h3.trend-title, .carousel-title").array()
        for (index, heading) in headings.prefix(8).enumerated() {
            let title = try heading.text().trimmingCharacters(in: .whitespacesAndNewlines)
            guard !title.isEmpty else { continue }
            let parent = heading.parent()
            let links = try parent?.select("a[href*=/serie/]").array()
                ?? doc.select("a[href*=/serie/]").array()
            let items = try parseCards(Array(links.prefix(18)))
            if !items.isEmpty {
                rows.append(CategoryRow(id: "sec-\(index)", title: title, items: items))
            }
        }

        if rows.isEmpty {
            let all = try parseCards(doc.select("a[href*=/serie/]").array().prefix(30).map { $0 })
            rows.append(CategoryRow(id: "all", title: "Popular", items: all))
        }
        return dedupeRows(rows)
    }

    func search(query: String) async throws -> [MediaItem] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return [] }
        let encoded = trimmed.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? trimmed
        let url = URL(string: "suche?q=\(encoded)", relativeTo: baseURL)!.absoluteURL
        let html = try await HTTPClient.getHTML(url: url, referer: baseURL)
        let doc = try SwiftSoup.parse(html, baseURL.absoluteString)
        return try parseCards(doc.select("a[href*=/serie/]").array())
    }

    func detail(id: String, kind: MediaItem.Kind) async throws -> ShowDetail {
        let url = URL(string: "serie/\(id)", relativeTo: baseURL)!.absoluteURL
        let html = try await HTTPClient.getHTML(url: url, referer: baseURL)
        let doc = try SwiftSoup.parse(html, baseURL.absoluteString)
        let title = try doc.selectFirst("h1, h2.series-title, .series-title")?.text()
            ?? id.replacingOccurrences(of: "-", with: " ").capitalized
        let overview = try doc.selectFirst("p.seri_des, .seri_des, [itemprop=description], .description")?.text()
        let poster = HTTPClient.absoluteURL(
            try doc.selectFirst("img.seriesCoverBox, .seriesCoverBox img, img[itemprop=image]")?.attr("data-src")
                ?? doc.selectFirst("img.seriesCoverBox, .seriesCoverBox img, img[itemprop=image]")?.attr("src"),
            base: baseURL
        )
        let banner = HTTPClient.absoluteURL(
            try doc.selectFirst(".backdrop, .series-backdrop")?.attr("style")
                .replacingOccurrences(of: "background-image: url(", with: "")
                .replacingOccurrences(of: ")", with: "")
                .trimmingCharacters(in: CharacterSet(charactersIn: "/")),
            base: baseURL
        )
        var seasons: [SeasonInfo] = []
        let seasonLinks = try doc.select("#stream ul li a, .hosterSiteTitle a, a[href*=/staffel-]").array()
        for (idx, link) in seasonLinks.enumerated() {
            let href = try link.attr("href")
            let text = try link.text()
            let number = Int(text.filter(\.isNumber)) ?? (idx + 1)
            let seasonId = href.split(separator: "/").dropFirst().joined(separator: "/")
            if seasons.contains(where: { $0.number == number }) { continue }
            seasons.append(SeasonInfo(id: seasonId.isEmpty ? "\(id)/staffel-\(number)" : seasonId, number: number, title: text.isEmpty ? "Season \(number)" : text))
        }
        if seasons.isEmpty {
            seasons = [SeasonInfo(id: "\(id)/staffel-1", number: 1, title: "Season 1")]
        }
        return ShowDetail(
            id: id,
            title: title,
            overview: overview,
            posterURL: poster,
            bannerURL: banner ?? poster,
            year: nil,
            rating: nil,
            seasons: seasons,
            kind: .tvShow
        )
    }

    func episodes(showId: String, seasonId: String) async throws -> [EpisodeInfo] {
        let path = seasonId.contains("/") ? seasonId : "\(showId)/\(seasonId)"
        let url = URL(string: "serie/\(path)", relativeTo: baseURL)!.absoluteURL
        let html = try await HTTPClient.getHTML(url: url, referer: baseURL)
        let doc = try SwiftSoup.parse(html, baseURL.absoluteString)
        var episodes: [EpisodeInfo] = []
        let rows = try doc.select("tr[data-episode-season-id], .episodeSeasonTitle, a[href*=/episode-]").array()
        for (idx, row) in rows.enumerated() {
            let href = try row.attr("href")
            let title = try row.text().trimmingCharacters(in: .whitespacesAndNewlines)
            let number = Int(title.filter(\.isNumber).prefix(3)) ?? (idx + 1)
            let epId = href.split(separator: "/").dropFirst().joined(separator: "/")
            guard !epId.isEmpty else { continue }
            if episodes.contains(where: { $0.id == epId }) { continue }
            episodes.append(
                EpisodeInfo(
                    id: epId,
                    number: number,
                    title: title.isEmpty ? "Episode \(number)" : title,
                    overview: nil,
                    thumbnailURL: nil
                )
            )
        }
        if episodes.isEmpty {
            // Fallback synthetic first episode so player path is testable
            episodes = [
                EpisodeInfo(id: "\(path)/episode-1", number: 1, title: "Episode 1", overview: nil, thumbnailURL: nil),
            ]
        }
        return episodes
    }

    func streams(showId: String, seasonId: String?, episodeId: String?) async throws -> [StreamSource] {
        let path = episodeId ?? seasonId ?? showId
        let url = URL(string: "serie/\(path)", relativeTo: baseURL)!.absoluteURL
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
            guard let id = extractSerieId(from: href), seen.insert(id).inserted else { continue }
            let title = try el.attr("title").ifBlank(try el.text())
                .replacingOccurrences(of: " stream online", with: "", options: .caseInsensitive)
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

    private func extractSerieId(from href: String) -> String? {
        guard let range = href.range(of: "/serie/") else { return nil }
        let rest = href[range.upperBound...]
        let slug = rest.split(separator: "/").first.map(String.init) ?? ""
        return slug.isEmpty ? nil : slug
    }

    private func dedupeRows(_ rows: [CategoryRow]) -> [CategoryRow] {
        var seenTitles = Set<String>()
        return rows.filter { row in
            let key = row.title.lowercased()
            return seenTitles.insert(key).inserted && !row.items.isEmpty
        }
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
