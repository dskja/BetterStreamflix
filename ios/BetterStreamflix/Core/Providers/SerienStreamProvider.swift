import Foundation
import SwiftSoup

struct SerienStreamProvider: CatalogProvider {
    let id = "serienstream"
    let name = "SerienStream"
    let language = "de"

    private static let candidateBases: [URL] = [
        URL(string: "https://s.to/")!,
        URL(string: "https://serienstream.to/")!,
        URL(string: "https://serienstream.sx/")!,
    ]

    /// Last working mirror for this process (Swift-6-safe mutable box).
    private static let resolvedBaseBox = MirrorBox(candidateBases[0])

    var baseURL: URL { Self.resolvedBaseBox.url }

    func home() async throws -> [CategoryRow] {
        let (html, base) = try await fetchHTML(path: "")
        let doc = try SwiftSoup.parse(html, base.absoluteString)
        var rows: [CategoryRow] = []

        let featured = try parseHero(doc)
        if !featured.isEmpty {
            rows.append(CategoryRow(id: "featured", title: "Featured", items: featured, isFeatured: true))
        }

        let trending = try parseTrending(doc)
        if !trending.isEmpty {
            rows.append(CategoryRow(id: "trending", title: "Angesagt", items: trending))
        }

        let neu = try parseNewShows(doc)
        if !neu.isEmpty {
            rows.append(CategoryRow(id: "new", title: "Neu auf S.to", items: neu))
        }

        for (index, column) in try doc.select("#discover-blocks .col").array().enumerated() {
            let title = try column.selectFirst("h4")?.text().trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            guard !title.isEmpty else { continue }
            var items: [MediaItem] = []
            for li in try column.select("li").array() {
                let href = try li.selectFirst("a")?.attr("href") ?? ""
                guard let id = extractSerieId(from: href) else { continue }
                let name = try li.selectFirst("span.h6")?.text().trimmingCharacters(in: .whitespacesAndNewlines) ?? id
                items.append(
                    MediaItem(
                        id: id,
                        title: name,
                        posterURL: try extractPoster(from: li),
                        kind: .tvShow,
                        providerHint: self.id
                    )
                )
            }
            if !items.isEmpty {
                rows.append(CategoryRow(id: "discover-\(index)", title: title, items: dedupe(items)))
            }
        }

        if rows.isEmpty {
            let fallback = try parseCards(doc.select("a[href*=/serie/]").array())
            rows.append(CategoryRow(id: "all", title: "Serien", items: fallback))
        }
        return rows.filter { !$0.items.isEmpty }
    }

    func search(query: String) async throws -> [MediaItem] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return [] }
        var components = URLComponents(url: URL(string: "suche", relativeTo: baseURL)!.absoluteURL, resolvingAgainstBaseURL: false)!
        components.queryItems = [
            URLQueryItem(name: "term", value: trimmed),
            URLQueryItem(name: "page", value: "1"),
            URLQueryItem(name: "tab", value: "shows"),
        ]
        guard let url = components.url else { throw ProviderError.invalidURL }
        let html = try await HTTPClient.getHTML(url: url, referer: baseURL, desktopUA: true, allowLenientTLS: true)
        let doc = try SwiftSoup.parse(html, baseURL.absoluteString)
        let cards = try doc.select("div.search-results-list div.card.cover-card").array()
        if !cards.isEmpty {
            return try cards.compactMap { card in
                let href = try card.selectFirst("a[href^=/serie/]")?.attr("href") ?? ""
                guard let id = extractSerieId(from: href) else { return nil }
                let title = try card.selectFirst("h6.show-title")?.text()
                    ?? card.selectFirst("h6")?.text()
                    ?? id
                return MediaItem(
                    id: id,
                    title: title.trimmingCharacters(in: .whitespacesAndNewlines),
                    posterURL: try extractPoster(from: card),
                    kind: .tvShow,
                    providerHint: self.id
                )
            }.deduped()
        }
        return try parseCards(doc.select("a[href*=/serie/]").array())
    }

    func detail(id: String, kind: MediaItem.Kind) async throws -> ShowDetail {
        let url = URL(string: "serie/\(id)", relativeTo: baseURL)!.absoluteURL
        let html = try await HTTPClient.getHTML(url: url, referer: baseURL, desktopUA: true, allowLenientTLS: true)
        let doc = try SwiftSoup.parse(html, baseURL.absoluteString)
        let title = try doc.selectFirst("h1")?.text().trimmingCharacters(in: .whitespacesAndNewlines)
            ?? id.replacingOccurrences(of: "-", with: " ").capitalized
        let overview = try doc.selectFirst("span.description-text")?.text()
            ?? doc.selectFirst("div.series-description p")?.text()
        let poster = try extractShowPoster(doc)
        let banner = try extractShowBanner(doc) ?? poster

        var seasons: [SeasonInfo] = []
        for link in try doc.select("#season-nav ul li a").array() {
            let text = try link.text().trimmingCharacters(in: .whitespacesAndNewlines)
            let href = try link.attr("href")
            let number = Int(text.filter(\.isNumber)) ?? (text.localizedCaseInsensitiveContains("filme") ? 0 : seasons.count + 1)
            let seasonPath = seasonId(from: href, showId: id, number: number)
            if seasons.contains(where: { $0.number == number }) { continue }
            seasons.append(SeasonInfo(id: seasonPath, number: number, title: text.isEmpty ? "Staffel \(number)" : text))
        }
        if seasons.isEmpty {
            seasons = [SeasonInfo(id: "\(id)/staffel-1", number: 1, title: "Staffel 1")]
        }

        return ShowDetail(
            id: id,
            title: title,
            overview: overview,
            posterURL: poster,
            bannerURL: banner,
            year: try doc.selectFirst("a.small.text-muted")?.text(),
            rating: nil,
            seasons: seasons,
            kind: .tvShow,
            genres: try doc.select(".series-group a").array().prefix(8).compactMap { try? $0.text() },
            cast: try doc.select(".series-group a").array().prefix(12).compactMap { try? $0.text() }
        )
    }

    func episodes(showId: String, seasonId: String) async throws -> [EpisodeInfo] {
        let path = seasonId.contains("/") ? seasonId : "\(showId)/\(seasonId)"
        let url = URL(string: "serie/\(path)", relativeTo: baseURL)!.absoluteURL
        let html = try await HTTPClient.getHTML(url: url, referer: baseURL, desktopUA: true, allowLenientTLS: true)
        let doc = try SwiftSoup.parse(html, baseURL.absoluteString)
        var episodes: [EpisodeInfo] = []

        for row in try doc.select("tr.episode-row").array() {
            let number = Int(try row.selectFirst(".episode-number-cell")?.text().trimmingCharacters(in: .whitespacesAndNewlines) ?? "") ?? (episodes.count + 1)
            let onclick = try row.attr("onclick")
            let href: String = {
                if let start = onclick.range(of: "window.location='"),
                   let end = onclick[start.upperBound...].range(of: "'") {
                    return String(onclick[start.upperBound..<end.lowerBound])
                }
                return (try? row.selectFirst("a")?.attr("href")) ?? ""
            }()
            let epId = episodeId(from: href, fallback: "\(path)/episode-\(number)")
            let title = try row.selectFirst(".episode-title-ger")?.text()
                ?? row.selectFirst(".episode-title-eng")?.text()
                ?? "Episode \(number)"
            if episodes.contains(where: { $0.id == epId }) { continue }
            episodes.append(EpisodeInfo(id: epId, number: number, title: title, overview: nil, thumbnailURL: nil))
        }

        if episodes.isEmpty {
            for link in try doc.select("a[href*=/episode-]").array() {
                let href = try link.attr("href")
                let epId = episodeId(from: href, fallback: "")
                guard !epId.isEmpty, !episodes.contains(where: { $0.id == epId }) else { continue }
                let number = episodes.count + 1
                let title = try link.text().trimmingCharacters(in: .whitespacesAndNewlines)
                episodes.append(EpisodeInfo(id: epId, number: number, title: title.isEmpty ? "Episode \(number)" : title))
            }
        }
        return episodes
    }

    func streams(showId: String, seasonId: String?, episodeId: String?, detail: ShowDetail?) async throws -> [StreamSource] {
        let path = episodeId ?? seasonId ?? showId
        let url = URL(string: "serie/\(path)", relativeTo: baseURL)!.absoluteURL
        let html = try await HTTPClient.getHTML(url: url, referer: baseURL, desktopUA: true, allowLenientTLS: true)
        let doc = try SwiftSoup.parse(html, baseURL.absoluteString)
        var sources: [StreamSource] = []

        for (idx, button) in try doc.select("button.link-box").array().enumerated() {
            let providerName = try button.attr("data-provider-name").ifBlank("Host")
            let language = try button.attr("data-language-label")
            let href = try button.attr("data-play-url")
            guard !href.isEmpty, let playURL = HTTPClient.absoluteURL(href, base: baseURL) else { continue }
            let needsGate = playURL.absoluteString.contains("/r?") || StreamResolver.isSerienStreamHost(playURL)
            sources.append(
                StreamSource(
                    id: "ss-\(idx)-\(providerName)",
                    name: "\(providerName) (\(language))".trimmingCharacters(in: .whitespaces),
                    url: playURL,
                    headers: ["User-Agent": HTTPClient.desktopUserAgent, "Referer": baseURL.absoluteString],
                    resolveKind: needsGate ? .serienstreamGate : .followRedirect
                )
            )
        }

        if sources.isEmpty {
            for (idx, host) in try doc.select("a[href*=/redirect/], a[href*=/r?], li[data-link-id] a").array().prefix(12).enumerated() {
                let href = try host.attr("abs:href").ifBlank(try host.attr("href"))
                guard let streamURL = HTTPClient.absoluteURL(href, base: baseURL) else { continue }
                let name = try host.text().trimmingCharacters(in: .whitespacesAndNewlines)
                sources.append(
                    StreamSource(
                        id: "legacy-\(idx)",
                        name: name.isEmpty ? "Host \(idx + 1)" : name,
                        url: streamURL,
                        headers: ["User-Agent": HTTPClient.desktopUserAgent, "Referer": baseURL.absoluteString],
                        resolveKind: .serienstreamGate
                    )
                )
            }
        }
        return sources
    }


    // MARK: - Networking

    private func fetchHTML(path: String) async throws -> (String, URL) {
        do {
            let result = try await HTTPClient.getHTML(path: path, bases: Self.candidateBases, desktopUA: true)
            Self.resolvedBaseBox.url = result.base
            return (result.html, result.base)
        } catch {
            throw ProviderError.parseFailed(
                "SerienStream unreachable (tried s.to / serienstream.to / .sx). \(error.localizedDescription)"
            )
        }
    }

    // MARK: - Parsing helpers

    private func parseHero(_ doc: Document) throws -> [MediaItem] {
        try doc.select(".home-hero-slide").array().compactMap { slide in
            let href = try slide.selectFirst("a.home-hero-cta")?.attr("href") ?? ""
            guard let id = extractSerieId(from: href) else { return nil }
            let title = try slide.selectFirst("h2.home-hero-title")?.text() ?? id
            let banner = try heroBanner(from: slide)
            return MediaItem(
                id: id,
                title: title,
                posterURL: banner,
                bannerURL: banner,
                kind: .tvShow,
                providerHint: self.id
            )
        }.deduped()
    }

    private func parseTrending(_ doc: Document) throws -> [MediaItem] {
        try doc.select(".trending-widget .swiper-slide").array().compactMap { slide in
            let href = try slide.selectFirst("h3.trend-title a")?.attr("href") ?? ""
            guard let id = extractSerieId(from: href) else { return nil }
            let title = try slide.selectFirst("h3.trend-title a")?.text() ?? id
            return MediaItem(id: id, title: title, posterURL: try extractPoster(from: slide), kind: .tvShow, providerHint: self.id)
        }.deduped()
    }

    private func parseNewShows(_ doc: Document) throws -> [MediaItem] {
        try doc.select("section.continue-widget.new-shows-slider .swiper-slide").array().compactMap { slide in
            let href = try slide.selectFirst("a.continue-cover, h3.continue-title a")?.attr("href") ?? ""
            guard let id = extractSerieId(from: href) else { return nil }
            let title = try slide.selectFirst("h3.continue-title a")?.text() ?? id
            return MediaItem(id: id, title: title, posterURL: try extractPoster(from: slide), kind: .tvShow, providerHint: self.id)
        }.deduped()
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
            items.append(
                MediaItem(
                    id: id,
                    title: title.ifBlank(id.replacingOccurrences(of: "-", with: " ").capitalized),
                    posterURL: try extractPoster(from: el),
                    kind: .tvShow,
                    providerHint: self.id
                )
            )
        }
        return items
    }

    private func heroBanner(from slide: Element) throws -> URL? {
        let srcsets = try slide.select("picture.home-hero-bg img, picture.home-hero-bg source").array()
            .flatMap { el -> [String] in
                let srcset = (try? el.attr("srcset")) ?? ""
                return srcset.split(separator: ",").map { String($0).trimmingCharacters(in: .whitespaces) }
            }
        let preferred = srcsets.first(where: { $0.contains("hero-2x-desktop") }) ?? srcsets.first
        let urlPart = preferred?.split(separator: " ").first.map(String.init)
        return HTTPClient.absoluteURL(urlPart, base: baseURL)
    }

    private func extractPoster(from el: Element) throws -> URL? {
        let img = try el.selectFirst("img")
        let raw = try img?.attr("data-src").ifBlank(try img?.attr("src"))
        return HTTPClient.absoluteURL(raw, base: baseURL)
    }

    private func extractShowPoster(_ doc: Document) throws -> URL? {
        let img = try doc.selectFirst("img.seriesCoverBox, .seriesCoverBox img, img[itemprop=image]")
        let raw = try img?.attr("data-src").ifBlank(try img?.attr("src"))
        return HTTPClient.absoluteURL(raw, base: baseURL)
    }

    private func extractShowBanner(_ doc: Document) throws -> URL? {
        let style = try doc.selectFirst(".backdrop, .series-backdrop")?.attr("style") ?? ""
        guard let range = style.range(of: "url(") else { return nil }
        var rest = style[range.upperBound...]
        if rest.hasPrefix("'") || rest.hasPrefix("\"") { rest = rest.dropFirst() }
        let end = rest.firstIndex(where: { $0 == ")" || $0 == "'" || $0 == "\"" }) ?? rest.endIndex
        let path = String(rest[..<end]).trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        return HTTPClient.absoluteURL(path.hasPrefix("http") ? path : "/\(path)", base: baseURL)
    }

    private func extractSerieId(from href: String) -> String? {
        guard let range = href.range(of: "/serie/") else { return nil }
        let slug = href[range.upperBound...].split(separator: "/").first.map(String.init) ?? ""
        return slug.isEmpty ? nil : slug
    }

    private func seasonId(from href: String, showId: String, number: Int) -> String {
        if let range = href.range(of: "/serie/") {
            let rest = href[range.upperBound...].trimmingCharacters(in: CharacterSet(charactersIn: "/"))
            if !rest.isEmpty { return rest }
        }
        return "\(showId)/staffel-\(max(number, 1))"
    }

    private func episodeId(from href: String, fallback: String) -> String {
        if let range = href.range(of: "/serie/") {
            let rest = href[range.upperBound...].trimmingCharacters(in: CharacterSet(charactersIn: "/"))
            if !rest.isEmpty { return rest }
        }
        return fallback
    }

    private func dedupe(_ items: [MediaItem]) -> [MediaItem] { items.deduped() }
}

private extension Array where Element == MediaItem {
    func deduped() -> [MediaItem] {
        var seen = Set<String>()
        return filter { seen.insert($0.id).inserted }
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


private final class MirrorBox: @unchecked Sendable {
    var url: URL
    init(_ url: URL) { self.url = url }
}
