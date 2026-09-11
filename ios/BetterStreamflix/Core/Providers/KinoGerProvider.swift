import Foundation
import SwiftSoup

/// Port of Android `KinoGerProvider` (https://kinoger.fun/).
struct KinoGerProvider: CatalogProvider {
    let id = "kinoger"
    let name = "KinoGer"
    let language = "de"
    let baseURL = URL(string: "https://kinoger.fun/")!

    func home() async throws -> [CategoryRow] {
        let html = try await HTTPClient.getHTML(url: baseURL, desktopUA: true, allowLenientTLS: true)
        let doc = try SwiftSoup.parse(html, baseURL.absoluteString)
        let items = try parseShorts(doc)
        guard !items.isEmpty else { return [] }
        return [CategoryRow(id: "home", title: "Kino Stream", items: items)]
    }

    func search(query: String) async throws -> [MediaItem] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return [] }
        guard let url = URL(string: "index.php?do=search", relativeTo: baseURL)?.absoluteURL else {
            throw ProviderError.invalidURL
        }
        let html = try await postForm(
            url: url,
            fields: [
                "do": "search",
                "subaction": "search",
                "search_start": "1",
                "full_search": "0",
                "result_from": "1",
                "story": trimmed,
            ]
        )
        let doc = try SwiftSoup.parse(html, baseURL.absoluteString)
        return try parseShorts(doc)
    }

    func detail(id: String, kind: MediaItem.Kind) async throws -> ShowDetail {
        guard let pageURL = HTTPClient.absoluteURL(id, base: baseURL) else { throw ProviderError.invalidURL }
        let html = try await HTTPClient.getHTML(url: pageURL, referer: baseURL, desktopUA: true, allowLenientTLS: true)
        let doc = try SwiftSoup.parse(html, baseURL.absoluteString)
        let titleRaw = try doc.selectFirst("h1#news-title, h1.title, h1")?.text()
            .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let title = cleanTitle(titleRaw)
        let poster = HTTPClient.absoluteURL(
            try doc.selectFirst(".content_text img, .full-text img, img[itemprop=image]")?.attr("src"),
            base: baseURL
        )
        let overviewRaw = try doc.selectFirst("meta[property=og:description]")?.attr("content")
            .ifBlank(try doc.selectFirst("[itemprop=description]")?.text())
            .ifBlank(try doc.selectFirst(".full-text p, .content_text p")?.text())
            .ifBlank(try doc.selectFirst(".full-text, .content_text")?.text())
            ?? ""
        let overview = scrubOverview(overviewRaw)
        let year = Self.parenYear.firstMatch(in: titleRaw)?.firstCaptured

        if kind == .tvShow || isSeriesDocument(doc, title: titleRaw) {
            let seasonNumber = Self.staffelRegex.firstMatch(in: titleRaw)?.firstCaptured.flatMap(Int.init) ?? 1
            return ShowDetail(
                id: id,
                title: title.isEmpty ? humanizeSlug(pageURL.lastPathComponent) : title,
                overview: overview?.isEmpty == true ? nil : overview,
                posterURL: poster,
                bannerURL: poster,
                year: year,
                seasons: [SeasonInfo(id: "\(pageURL.absoluteString)#season-\(seasonNumber)", number: seasonNumber, title: "Staffel \(seasonNumber)")],
                kind: .tvShow
            )
        }

        return ShowDetail(
            id: id,
            title: title.isEmpty ? humanizeSlug(pageURL.lastPathComponent) : title,
            overview: overview?.isEmpty == true ? nil : overview,
            posterURL: poster,
            bannerURL: poster,
            year: year,
            seasons: [],
            kind: .movie
        )
    }

    func episodes(showId: String, seasonId: String) async throws -> [EpisodeInfo] {
        let showURLString = seasonId.contains("#") ? String(seasonId.split(separator: "#").first ?? Substring(showId)) : showId
        let seasonNumber = Int(seasonId.split(separator: "-").last.map(String.init) ?? "")
            ?? Int(seasonId.components(separatedBy: "#season-").last ?? "")
            ?? 1
        guard let pageURL = HTTPClient.absoluteURL(showURLString, base: baseURL) else {
            throw ProviderError.invalidURL
        }
        let html = try await HTTPClient.getHTML(url: pageURL, referer: baseURL, desktopUA: true, allowLenientTLS: true)
        let doc = try SwiftSoup.parse(html, baseURL.absoluteString)
        return try parseEpisodes(showURL: pageURL.absoluteString, seasonNumber: seasonNumber, doc: doc)
    }

    func streams(showId: String, seasonId: String?, episodeId: String?, detail: ShowDetail?) async throws -> [StreamSource] {
        if let episodeId, episodeId.contains("#s") {
            return try await episodeStreams(episodeId: episodeId)
        }

        guard let pageURL = HTTPClient.absoluteURL(showId, base: baseURL) else { throw ProviderError.invalidURL }
        let html = try await HTTPClient.getHTML(url: pageURL, referer: baseURL, desktopUA: true, allowLenientTLS: true)
        let doc = try SwiftSoup.parse(html, baseURL.absoluteString)
        var sources: [StreamSource] = []
        for (idx, span) in try doc.select(".player-mirrors span[data-link]").array().enumerated() {
            let raw = try span.attr("data-link")
            guard let link = normalizeStreamURL(raw),
                  !link.absoluteString.localizedCaseInsensitiveContains("youtube") else { continue }
            let label = span.ownText().ifBlank(try span.text())
            sources.append(
                StreamSource(
                    id: "kg-\(idx)",
                    name: hosterDisplayName(url: link, fallback: label),
                    url: link,
                    headers: defaultHeaders,
                    resolveKind: .followRedirect
                )
            )
        }
        return sources.uniqued(by: \.url)
    }

    // MARK: - Parsing

    private func parseShorts(_ doc: Document) throws -> [MediaItem] {
        var items: [MediaItem] = []
        var seen = Set<String>()
        for el in try doc.select("div.short").array() {
            guard let item = try parseShort(el), seen.insert(item.id).inserted else { continue }
            items.append(item)
        }
        return items
    }

    private func parseShort(_ el: Element) throws -> MediaItem? {
        guard let link = try el.selectFirst(".title a[href$=.html]") else { return nil }
        let href = try link.attr("href").trimmingCharacters(in: .whitespacesAndNewlines)
        guard !href.isEmpty, let abs = HTTPClient.absoluteURL(href, base: baseURL) else { return nil }
        let titleRaw = try link.text().trimmingCharacters(in: .whitespacesAndNewlines)
        guard !titleRaw.isEmpty else { return nil }
        let poster = HTTPClient.absoluteURL(try el.selectFirst(".content_text img")?.attr("src"), base: baseURL)
        let kind: MediaItem.Kind = try isSeriesCard(el, title: titleRaw) ? .tvShow : .movie
        return MediaItem(
            id: abs.absoluteString,
            title: cleanTitle(titleRaw),
            posterURL: poster,
            kind: kind,
            providerHint: self.id
        )
    }

    private func isSeriesCard(_ el: Element, title: String) throws -> Bool {
        if try el.selectFirst(".serie-num") != nil { return true }
        if title.localizedCaseInsensitiveContains("Staffel") { return true }
        let cats = try el.select(".content_text").text()
        return cats.localizedCaseInsensitiveContains("Serien")
    }

    private func isSeriesDocument(_ doc: Document, title: String) -> Bool {
        if title.localizedCaseInsensitiveContains("Staffel") { return true }
        if let episodes = try? doc.select("ul.ep-menu li[id^=serie-]").array(), !episodes.isEmpty {
            return true
        }
        return false
    }

    private func parseEpisodes(showURL: String, seasonNumber: Int, doc: Document) throws -> [EpisodeInfo] {
        var episodes: [EpisodeInfo] = []
        for li in try doc.select("ul.ep-menu li[id^=serie-]").array() {
            let idAttr = li.id().replacingOccurrences(of: "serie-", with: "")
            let parts = idAttr.split(separator: "_")
            let s = parts.first.flatMap { Int($0) } ?? seasonNumber
            guard let e = parts.dropFirst().first.flatMap({ Int($0) }), s == seasonNumber else { continue }
            episodes.append(
                EpisodeInfo(
                    id: "\(showURL)#s\(s)e\(e)",
                    number: e,
                    title: "Episode \(e)"
                )
            )
        }
        return episodes
            .reduce(into: [Int: EpisodeInfo]()) { $0[$1.number] = $1 }
            .values
            .sorted { $0.number < $1.number }
    }

    private func episodeStreams(episodeId: String) async throws -> [StreamSource] {
        let pagePart = episodeId.split(separator: "#").first.map(String.init) ?? episodeId
        let marker = episodeId.split(separator: "#").last.map(String.init) ?? ""
        let season = Int(marker.dropFirst().prefix(while: { $0.isNumber })) ?? 1
        let episode = Int(marker.split(separator: "e").last.map(String.init) ?? "") ?? 1
        guard let pageURL = HTTPClient.absoluteURL(pagePart, base: baseURL) else {
            throw ProviderError.invalidURL
        }
        let html = try await HTTPClient.getHTML(url: pageURL, referer: baseURL, desktopUA: true, allowLenientTLS: true)
        let doc = try SwiftSoup.parse(html, baseURL.absoluteString)
        let li = try doc.selectFirst("ul.ep-menu li#serie-\(season)_\(episode)")
            ?? doc.selectFirst("ul.ep-menu li[id=serie-\(season)_\(episode)]")
        var sources: [StreamSource] = []
        for (idx, a) in try (li?.select("a[data-link]").array() ?? []).enumerated() {
            let raw = try a.attr("data-link")
            guard let link = normalizeStreamURL(raw),
                  !link.absoluteString.localizedCaseInsensitiveContains("youtube") else { continue }
            let label = a.ownText().ifBlank(try a.text())
            sources.append(
                StreamSource(
                    id: "kg-ep-\(idx)",
                    name: hosterDisplayName(url: link, fallback: label),
                    url: link,
                    headers: defaultHeaders,
                    resolveKind: .followRedirect
                )
            )
        }
        return sources.uniqued(by: \.url)
    }

    private func cleanTitle(_ raw: String) -> String {
        raw.replacingOccurrences(of: #"\s*\(\d{4}\)\s*$"#, with: "", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private func normalizeStreamURL(_ raw: String) -> URL? {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        if trimmed.localizedCaseInsensitiveContains("/vod/vpn") { return nil }
        return HTTPClient.absoluteURL(trimmed, base: baseURL)
    }

    private func hosterDisplayName(url: URL, fallback: String) -> String {
        let host = (url.host() ?? "")
            .replacingOccurrences(of: "www.", with: "")
            .split(separator: ".")
            .first
            .map(String.init)?
            .lowercased() ?? ""
        switch host {
        case "voe": return "Voe"
        case "meinecloud": return "Meinecloud"
        case "vidara": return "Vidara"
        case "firestream": return "Firestream"
        case "mixdrop": return "Mixdrop"
        case "streamtape": return "Streamtape"
        case let h where h.hasPrefix("dood"): return "Doodstream"
        default:
            if !fallback.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                return fallback.trimmingCharacters(in: .whitespacesAndNewlines)
            }
            if !host.isEmpty {
                return host.prefix(1).uppercased() + host.dropFirst()
            }
            return "Server"
        }
    }

    private var defaultHeaders: [String: String] {
        [
            "User-Agent": HTTPClient.desktopUserAgent,
            "Referer": baseURL.absoluteString,
        ]
    }

    /// Form POST for DLE search.
    private func postForm(url: URL, fields: [String: String]) async throws -> String {
        let data = try await HTTPClient.postForm(
            url: url,
            fields: fields,
            headers: [
                "User-Agent": HTTPClient.desktopUserAgent,
                "Referer": baseURL.absoluteString,
            ]
        )
        guard let html = String(data: data, encoding: .utf8), !html.isEmpty else {
            throw ProviderError.emptyResponse
        }
        return html
    }

    private static let parenYear = try! NSRegularExpression(pattern: #"\((\d{4})\)"#)
    private static let staffelRegex = try! NSRegularExpression(
        pattern: #"Staffel\s+(\d+)"#,
        options: [.caseInsensitive]
    )

    private func scrubOverview(_ raw: String) -> String? {
        var text = raw
        let junk = [
            "Streamanbieter aussuchen", "auf 'Play' klicken", "Das schnellste VPN",
            "Hier den Film bewerten", "Ähnliche Films", "Ähnliche Filme", "0/5 von",
            "WEBRip", "Stream deutsch kostenlos"
        ]
        for j in junk {
            if let r = text.range(of: j, options: .caseInsensitive) {
                text = String(text[..<r.lowerBound])
            }
        }
        text = text.replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
        return text.count > 40 ? text : (text.isEmpty ? nil : text)
    }

    private func humanizeSlug(_ slug: String) -> String {
        var s = slug.replacingOccurrences(of: ".html", with: "")
        // Drop leading numeric ids: 25532-danke-team-...
        if let r = s.range(of: #"^\d+-"#, options: .regularExpression) {
            s.removeSubrange(r)
        }
        s = s.replacingOccurrences(of: "-", with: " ")
        return s.capitalized
    }

}

private extension Array {
    func uniqued<T: Hashable>(by keyPath: KeyPath<Element, T>) -> [Element] {
        var seen = Set<T>()
        return filter { seen.insert($0[keyPath: keyPath]).inserted }
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

private extension NSRegularExpression {
    struct MatchResult {
        let matched: String
        let firstCaptured: String?
    }

    func firstMatch(in string: String) -> MatchResult? {
        let range = NSRange(string.startIndex..., in: string)
        guard let match = firstMatch(in: string, options: [], range: range),
              let full = Range(match.range, in: string) else { return nil }
        let captured: String?
        if match.numberOfRanges > 1, let cap = Range(match.range(at: 1), in: string) {
            captured = String(string[cap])
        } else {
            captured = nil
        }
        return MatchResult(matched: String(string[full]), firstCaptured: captured)
    }
}
