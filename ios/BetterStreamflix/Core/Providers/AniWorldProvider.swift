import Foundation
import SwiftSoup

struct AniWorldProvider: CatalogProvider {
    let id = "aniworld"
    let name = "AniWorld"
    let language = "de"
    let baseURL = URL(string: "https://aniworld.to/")!

    private static let cache = AlphabetCache()

    func home() async throws -> [CategoryRow] {
        Task { try? await Self.cache.ensure(baseURL: baseURL) }

        var rows: [CategoryRow] = []

        if let popularHTML = try? await HTTPClient.getHTML(
            url: URL(string: "beliebte-animes", relativeTo: baseURL)!.absoluteURL,
            referer: baseURL,
            desktopUA: true,
            allowLenientTLS: true
        ),
           let popularDoc = try? SwiftSoup.parse(popularHTML, baseURL.absoluteString) {
            let items = try parseCoverList(popularDoc)
            if !items.isEmpty {
                rows.append(CategoryRow(id: "popular", title: "Beliebte Animes", items: items))
            }
        }

        let html = try await HTTPClient.getHTML(url: baseURL, desktopUA: true, allowLenientTLS: true)
        let doc = try SwiftSoup.parse(html, baseURL.absoluteString)

        let sectionSelectors = [
            ("hot", "Beliebt bei AniWorld", "div.container > div:nth-child(7) > div.previews div.coverListItem"),
            ("new", "Neue Animes", "div.container > div:nth-child(11) > div.previews div.coverListItem"),
            ("now", "Derzeit beliebte Animes", "div.container > div:nth-child(16) > div.previews div.coverListItem"),
        ]
        for (id, title, selector) in sectionSelectors {
            let items = try parseCoverItems(doc.select(selector).array())
            if !items.isEmpty {
                rows.append(CategoryRow(id: id, title: title, items: items))
            }
        }

        if rows.isEmpty {
            let fallback = try parseCards(doc.select("a[href*=/anime/stream/]").array().prefix(40).map { $0 })
            rows.append(CategoryRow(id: "home", title: "Animes", items: fallback))
        }
        return rows.filter { !$0.items.isEmpty }
    }

    func search(query: String) async throws -> [MediaItem] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return [] }
        try await Self.cache.ensure(baseURL: baseURL)
        let needle = trimmed.lowercased()
        let cached = await Self.cache.items
        let filtered = cached.filter { $0.title.lowercased().contains(needle) }
        if !filtered.isEmpty { return Array(filtered.prefix(60)) }

        // Fallback: site search page if alphabet cache is empty
        let encoded = trimmed.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? trimmed
        if let url = URL(string: "search?q=\(encoded)", relativeTo: baseURL)?.absoluteURL,
           let html = try? await HTTPClient.getHTML(url: url, referer: baseURL, desktopUA: true, allowLenientTLS: true),
           let doc = try? SwiftSoup.parse(html, baseURL.absoluteString) {
            return try parseCards(doc.select("a[href*=/anime/stream/]").array())
        }
        return []
    }

    func detail(id: String, kind: MediaItem.Kind) async throws -> ShowDetail {
        let url = URL(string: "anime/stream/\(id)", relativeTo: baseURL)!.absoluteURL
        let html = try await HTTPClient.getHTML(url: url, referer: baseURL, desktopUA: true, allowLenientTLS: true)
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
        for (idx, link) in try doc.select("#stream > ul:nth-child(1) > li a").array().enumerated() {
            let text = try link.text()
            let href = try link.attr("href")
            let number: Int = {
                if text.localizedCaseInsensitiveContains("filme") || text.localizedCaseInsensitiveContains("special") {
                    return 0
                }
                return Int(text.filter(\.isNumber)) ?? (idx + 1)
            }()
            let seasonPath = href.replacingOccurrences(of: "/anime/stream/", with: "")
                .trimmingCharacters(in: CharacterSet(charactersIn: "/"))
            seasons.append(
                SeasonInfo(
                    id: seasonPath.isEmpty ? "\(id)/staffel-\(max(number, 1))" : seasonPath,
                    number: number,
                    title: text.isEmpty ? "Staffel \(number)" : text
                )
            )
        }
        if seasons.isEmpty {
            seasons = [SeasonInfo(id: "\(id)/staffel-1", number: 1, title: "Staffel 1")]
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
            kind: .tvShow,
            genres: try doc.select(".genres li a").array().compactMap { try? $0.text() },
            cast: try doc.select(".cast li[itemprop=actor] span").array().prefix(12).compactMap { try? $0.text() }
        )
    }

    func episodes(showId: String, seasonId: String) async throws -> [EpisodeInfo] {
        let path = seasonId.contains("/") ? seasonId : "\(showId)/\(seasonId)"
        let url = URL(string: "anime/stream/\(path)", relativeTo: baseURL)!.absoluteURL
        let html = try await HTTPClient.getHTML(url: url, referer: baseURL, desktopUA: true, allowLenientTLS: true)
        let doc = try SwiftSoup.parse(html, baseURL.absoluteString)
        var episodes: [EpisodeInfo] = []

        for row in try doc.select("tbody tr").array() {
            let epNumber = Int(try row.selectFirst("meta")?.attr("content") ?? "") ?? (episodes.count + 1)
            let href = try row.selectFirst("a")?.attr("href") ?? ""
            let epPath = href.replacingOccurrences(of: "/anime/stream/", with: "")
                .trimmingCharacters(in: CharacterSet(charactersIn: "/"))
            guard !epPath.isEmpty else { continue }
            if episodes.contains(where: { $0.id == epPath }) { continue }
            let title = try row.selectFirst("strong")?.text() ?? "Episode \(epNumber)"
            episodes.append(EpisodeInfo(id: epPath, number: epNumber, title: title))
        }

        if episodes.isEmpty {
            for (idx, link) in try doc.select("a[href*=/episode-]").array().enumerated() {
                let href = try link.attr("href")
                let epPath = href.replacingOccurrences(of: "/anime/stream/", with: "")
                    .trimmingCharacters(in: CharacterSet(charactersIn: "/"))
                guard !epPath.isEmpty, !episodes.contains(where: { $0.id == epPath }) else { continue }
                let title = try link.text().trimmingCharacters(in: .whitespacesAndNewlines)
                episodes.append(
                    EpisodeInfo(
                        id: epPath,
                        number: idx + 1,
                        title: title.isEmpty ? "Episode \(idx + 1)" : title
                    )
                )
            }
        }
        return episodes
    }

    func streams(showId: String, seasonId: String?, episodeId: String?, detail: ShowDetail?) async throws -> [StreamSource] {
        let path = episodeId ?? seasonId ?? showId
        let url = URL(string: "anime/stream/\(path)", relativeTo: baseURL)!.absoluteURL
        let html = try await HTTPClient.getHTML(url: url, referer: baseURL, desktopUA: true, allowLenientTLS: true)
        let doc = try SwiftSoup.parse(html, baseURL.absoluteString)
        var sources: [StreamSource] = []

        for (idx, host) in try doc.select("div.hosterSiteVideo > ul > li").array().enumerated() {
            let href = try host.selectFirst("a")?.attr("href") ?? ""
            guard let streamURL = HTTPClient.absoluteURL(href, base: baseURL) else { continue }
            let baseName = try host.selectFirst("h4")?.text() ?? "Host \(idx + 1)"
            let lang: String = {
                switch try? host.attr("data-lang-key") {
                case "1": return "DUB"
                case "2": return "SUB EN"
                case "3": return "SUB"
                default: return ""
                }
            }()
            let name = lang.isEmpty ? baseName : "\(baseName) - \(lang)"
            sources.append(
                StreamSource(
                    id: "aw-\(idx)-\(baseName)",
                    name: name,
                    url: streamURL,
                    headers: ["User-Agent": HTTPClient.desktopUserAgent, "Referer": baseURL.absoluteString],
                    resolveKind: .followRedirect
                )
            )
        }

        if sources.isEmpty {
            for (idx, host) in try doc.select("a[href*=/redirect/], li[data-link-id] a").array().prefix(12).enumerated() {
                let href = try host.attr("abs:href").ifBlank(try host.attr("href"))
                guard let streamURL = HTTPClient.absoluteURL(href, base: baseURL) else { continue }
                let name = try host.text().trimmingCharacters(in: .whitespacesAndNewlines)
                sources.append(
                    StreamSource(
                        id: "aw-legacy-\(idx)",
                        name: name.isEmpty ? "Host \(idx + 1)" : name,
                        url: streamURL,
                        headers: ["User-Agent": HTTPClient.desktopUserAgent, "Referer": baseURL.absoluteString],
                        resolveKind: .followRedirect
                    )
                )
            }
        }
        return sources
    }

    // MARK: - Alphabet cache (Android Room equivalent)

    private actor AlphabetCache {
        private var stored: [MediaItem] = []

        var items: [MediaItem] { stored }

        func ensure(baseURL: URL) async throws {
            if !stored.isEmpty { return }
            let url = URL(string: "animes-alphabet", relativeTo: baseURL)!.absoluteURL
            let html = try await HTTPClient.getHTML(url: url, referer: baseURL, desktopUA: true, allowLenientTLS: true)
            let doc = try SwiftSoup.parse(html, baseURL.absoluteString)
            var items: [MediaItem] = []
            var seen = Set<String>()
            for link in try doc.select(".genre > ul > li a, a[href*=/anime/stream/]").array() {
                let href = try link.attr("href")
                guard let id = AniWorldProvider.extractAnimeId(from: href), seen.insert(id).inserted else { continue }
                let title = try link.attr("data-alternative-title").ifBlank(try link.text())
                    .trimmingCharacters(in: .whitespacesAndNewlines)
                items.append(
                    MediaItem(
                        id: id,
                        title: title.ifBlank(id.replacingOccurrences(of: "-", with: " ").capitalized),
                        posterURL: nil,
                        kind: .tvShow,
                        providerHint: "aniworld"
                    )
                )
            }
            stored = items
        }
    }

    private func parseCoverList(_ doc: Document) throws -> [MediaItem] {
        let items = try parseCoverItems(doc.select("div.coverListItem, a[href*=/anime/stream/]").array())
        return items
    }

    private func parseCoverItems(_ elements: [Element]) throws -> [MediaItem] {
        var items: [MediaItem] = []
        var seen = Set<String>()
        for el in elements {
            let link = try el.selectFirst("a") ?? el
            let href = try link.attr("href")
            guard let id = AniWorldProvider.extractAnimeId(from: href), seen.insert(id).inserted else { continue }
            let title = try el.selectFirst("a h3, h3")?.text()
                ?? link.attr("title").ifBlank(try link.text())
            let img = try el.selectFirst("img")
            let poster = HTTPClient.absoluteURL(
                try img?.attr("data-src").ifBlank(try img?.attr("src")),
                base: baseURL
            )
            items.append(
                MediaItem(
                    id: id,
                    title: title.trimmingCharacters(in: .whitespacesAndNewlines).ifBlank(id),
                    posterURL: poster,
                    kind: .tvShow,
                    providerHint: self.id
                )
            )
        }
        return items
    }

    private func parseCards(_ elements: [Element]) throws -> [MediaItem] {
        try parseCoverItems(elements)
    }

    fileprivate static func extractAnimeId(from href: String) -> String? {
        guard let range = href.range(of: "/anime/stream/") else { return nil }
        let slug = href[range.upperBound...].split(separator: "/").first.map(String.init) ?? ""
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
