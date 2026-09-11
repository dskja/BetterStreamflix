import Foundation
import SwiftSoup

/// Port of Android `MEGAKinoProvider` (https://megakino.me/).
struct MEGAKinoProvider: CatalogProvider {
    let id = "megakino"
    let name = "MEGAKino"
    let language = "de"
    let baseURL = URL(string: "https://megakino.me/")!

    private static let tokenBox = TokenBox()

    func home() async throws -> [CategoryRow] {
        try await ensureToken()
        let html = try await HTTPClient.getHTML(url: baseURL, desktopUA: true, allowLenientTLS: true)
        let doc = try SwiftSoup.parse(html, baseURL.absoluteString)
        let sections = try doc.select("section.sect").array()
        var rows: [CategoryRow] = []
        var seenTitles = Set<String>()
        for (idx, sec) in sections.enumerated() {
            let items = try parseContentItems(sec)
            guard !items.isEmpty else { continue }
            var title = try sec.select("h2.sect__title").text().trimmingCharacters(in: .whitespacesAndNewlines)
            if title.isEmpty { title = idx == 0 ? "Topaktuelle Neuheiten" : "Mehr \(idx + 1)" }
            // Skip pure news/blog rails that aren't playable posters when possible.
            let key = title.lowercased()
            if key.contains("news") && items.count < 3 { continue }
            guard seenTitles.insert(key).inserted else { continue }
            let isFeatured = idx == 0 || key.contains("neuheit") || key.contains("featured")
            rows.append(CategoryRow(id: "sect-\(idx)", title: title, items: items, isFeatured: isFeatured && rows.isEmpty))
        }
        if rows.isEmpty {
            let fallback = try parseContentItems(doc)
            if !fallback.isEmpty {
                rows.append(CategoryRow(id: "neuheiten", title: "Topaktuelle Neuheiten", items: fallback, isFeatured: true))
            }
        }
        return rows
    }

    func search(query: String) async throws -> [MediaItem] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return [] }
        try await ensureToken()
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
        return try parseContentItems(doc)
    }

    func detail(id: String, kind: MediaItem.Kind) async throws -> ShowDetail {
        try await ensureToken()
        guard let pageURL = HTTPClient.absoluteURL(id, base: baseURL) else { throw ProviderError.invalidURL }
        let html = try await HTTPClient.getHTML(url: pageURL, referer: baseURL, desktopUA: true, allowLenientTLS: true)
        let doc = try SwiftSoup.parse(html, baseURL.absoluteString)
        let titleRaw = try doc.selectFirst("h1[itemprop=name], h1")?.text()
            .trimmingCharacters(in: .whitespacesAndNewlines) ?? pageURL.lastPathComponent
        let poster = HTTPClient.absoluteURL(
            try doc.selectFirst("div.pmovie__poster img[itemprop=image], .pmovie__poster img")?.attr("data-src")
                .ifBlank(try doc.selectFirst("div.pmovie__poster img")?.attr("src")),
            base: baseURL
        )
        let overview = try doc.selectFirst("div.page__text[itemprop=description], .page__text")?.text()
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let year = try doc.selectFirst("div.pmovie__year span[itemprop=dateCreated], .pmovie__year")?.text()
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let genres = try doc.selectFirst("div.pmovie__genres[itemprop=genre], .pmovie__genres")?.text()
            .split(separator: "/")
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty } ?? []
        let cast = try doc.select("span[itemprop=actors] a").array().compactMap { a -> String? in
            let name = try a.text().trimmingCharacters(in: .whitespacesAndNewlines)
            return name.isEmpty ? nil : name
        }

        let isSerial = id.contains("/serials/") || kind == .tvShow
            || Self.staffelRegex.firstMatch(in: titleRaw) != nil
        if isSerial {
            let seasonNumber = Self.staffelRegex.firstMatch(in: titleRaw)?.firstCaptured.flatMap(Int.init) ?? 1
            return ShowDetail(
                id: id,
                title: titleRaw,
                overview: overview?.isEmpty == true ? nil : overview,
                posterURL: poster,
                bannerURL: poster,
                year: year,
                seasons: [SeasonInfo(id: id, number: seasonNumber, title: "Episodes")],
                kind: .tvShow,
                genres: genres,
                cast: cast
            )
        }

        return ShowDetail(
            id: id,
            title: titleRaw,
            overview: overview?.isEmpty == true ? nil : overview,
            posterURL: poster,
            bannerURL: poster,
            year: year,
            seasons: [],
            kind: .movie,
            genres: genres,
            cast: cast
        )
    }

    func episodes(showId: String, seasonId: String) async throws -> [EpisodeInfo] {
        try await ensureToken()
        let target = seasonId.isEmpty ? showId : seasonId
        guard let pageURL = HTTPClient.absoluteURL(target, base: baseURL) else { throw ProviderError.invalidURL }
        let html = try await HTTPClient.getHTML(url: pageURL, referer: baseURL, desktopUA: true, allowLenientTLS: true)
        let doc = try SwiftSoup.parse(html, baseURL.absoluteString)
        var episodes: [EpisodeInfo] = []
        var seen = Set<String>()
        for option in try doc.select("select.se-select option, select.episode-select option, select[name*=episode] option").array() {
            let value = try option.attr("value").trimmingCharacters(in: .whitespacesAndNewlines)
            let name = try option.text().trimmingCharacters(in: .whitespacesAndNewlines)
            guard !value.isEmpty, seen.insert(value).inserted else { continue }
            let number = Self.episodeRegex.firstMatch(in: name)?.firstCaptured.flatMap(Int.init)
                ?? (episodes.count + 1)
            episodes.append(
                EpisodeInfo(
                    id: "\(pageURL.absoluteString)|\(value)",
                    number: number,
                    title: name.isEmpty ? "Episode \(number)" : name
                )
            )
        }
        // Some layouts list episodes as buttons / tabs instead of <select>.
        if episodes.isEmpty {
            for (idx, el) in try doc.select(".pmovie__episodes a, .episodes-list a, [data-episode], .ep-item").array().enumerated() {
                let value = try el.attr("data-episode").ifBlank(try el.attr("href")).ifBlank("\(idx + 1)")
                let name = try el.text().trimmingCharacters(in: .whitespacesAndNewlines)
                guard seen.insert(value).inserted else { continue }
                let number = Self.episodeRegex.firstMatch(in: name)?.firstCaptured.flatMap(Int.init) ?? (idx + 1)
                episodes.append(
                    EpisodeInfo(
                        id: "\(pageURL.absoluteString)|\(value)",
                        number: number,
                        title: name.isEmpty ? "Episode \(number)" : name
                    )
                )
            }
        }
        return episodes
    }

    func streams(showId: String, seasonId: String?, episodeId: String?, detail: ShowDetail?) async throws -> [StreamSource] {
        try await ensureToken()
        if let episodeId, episodeId.contains("|") {
            return try await episodeStreams(episodeId: episodeId)
        }

        guard let pageURL = HTTPClient.absoluteURL(showId, base: baseURL) else { throw ProviderError.invalidURL }
        let html = try await HTTPClient.getHTML(url: pageURL, referer: baseURL, desktopUA: true, allowLenientTLS: true)
        let doc = try SwiftSoup.parse(html, baseURL.absoluteString)
        var sources: [StreamSource] = []

        let tabNames = try doc.select("div.tabs-block__select span, .tabs-block__select span, .player-tabs span, .nav-tabs a")
            .array()
            .compactMap { try? $0.text().trimmingCharacters(in: .whitespacesAndNewlines) }
        let contents = try doc.select("div.tabs-block__content, .tabs-block__content, .tab-content .tab-pane, .player iframe").array()

        for (index, content) in contents.enumerated() {
            let iframe = content.tagName() == "iframe" ? content : try content.selectFirst("iframe")
            let serverSrc = try iframe?.attr("data-src").ifBlank(try iframe?.attr("src")) ?? ""
            if shouldKeepEmbed(serverSrc), let url = HTTPClient.absoluteURL(serverSrc, base: baseURL) {
                let name = tabNames.indices.contains(index) && !tabNames[index].isEmpty
                    ? tabNames[index]
                    : "Server \(index + 1)"
                sources.append(streamSource(id: "mk-\(index)", name: name, url: url))
            }

            for link in try content.select("a[href*=/dl/]").array() {
                let href = try link.attr("href").trimmingCharacters(in: .whitespacesAndNewlines)
                guard !href.isEmpty else { continue }
                if let resolved = await resolveDlStream(href) {
                    let name = tabNames.indices.contains(index) && !tabNames[index].isEmpty
                        ? tabNames[index]
                        : (try link.text().trimmingCharacters(in: .whitespacesAndNewlines)).ifBlank("Server")
                    sources.append(streamSource(id: "mk-dl-\(sources.count)", name: name, url: resolved))
                } else if let abs = HTTPClient.absoluteURL(href, base: baseURL) {
                    let name = try link.text().trimmingCharacters(in: .whitespacesAndNewlines).ifBlank("Server")
                    sources.append(streamSource(id: "mk-dl-\(sources.count)", name: name, url: abs))
                }
            }
        }

        if sources.isEmpty {
            for (index, iframe) in try doc.select("iframe[src], iframe[data-src], [data-src*=http]").array().enumerated() {
                let serverSrc = try iframe.attr("data-src").ifBlank(try iframe.attr("src"))
                guard shouldKeepEmbed(serverSrc),
                      let url = HTTPClient.absoluteURL(serverSrc, base: baseURL) else { continue }
                sources.append(streamSource(id: "mk-fb-\(index)", name: "Server \(index + 1)", url: url))
            }
        }

        if sources.isEmpty {
            for (index, link) in try doc.select("a[href*=/dl/]").array().enumerated() {
                let href = try link.attr("href").trimmingCharacters(in: .whitespacesAndNewlines)
                guard !href.isEmpty else { continue }
                let src = await resolveDlStream(href) ?? HTTPClient.absoluteURL(href, base: baseURL)
                guard let src else { continue }
                sources.append(streamSource(id: "mk-dl2-\(index)", name: "Server \(index + 1)", url: src))
            }
        }

        if sources.isEmpty, let imdb = Self.imdbRegex.firstMatch(in: html)?.matched {
            let embed = URL(string: "https://meinecloud.click/movie/\(imdb)")!
            sources.append(contentsOf: await parseMeinecloudMirrors(embedURL: embed))
        }

        let distinct = sources.uniqued(by: \.url)
        if distinct.isEmpty {
            throw ProviderError.streamGate(
                "Keine Stream-Server gefunden. MEGAKino verlangt oft eine VPN-Verbindung."
            )
        }
        return distinct
    }

    // MARK: - Helpers

    private func parseContentItems(_ element: Element) throws -> [MediaItem] {
        var items: [MediaItem] = []
        var seen = Set<String>()
        for el in try element.select("div#dle-content a.poster.grid-item, a.poster.grid-item").array() {
            let href = try el.attr("href").trimmingCharacters(in: .whitespacesAndNewlines)
            guard !href.isEmpty, let abs = HTTPClient.absoluteURL(href, base: baseURL),
                  seen.insert(abs.absoluteString).inserted else { continue }
            let title = try el.select("h3.poster__title").text().trimmingCharacters(in: .whitespacesAndNewlines)
            guard !title.isEmpty else { continue }
            let poster = HTTPClient.absoluteURL(
                try el.select("div.poster__img img").attr("data-src")
                    .ifBlank(try el.select("div.poster__img img").attr("src")),
                base: baseURL
            )
            let kind: MediaItem.Kind = href.contains("/serials/") ? .tvShow : .movie
            items.append(
                MediaItem(
                    id: abs.absoluteString,
                    title: title,
                    posterURL: poster,
                    kind: kind,
                    providerHint: self.id
                )
            )
        }
        return items
    }

    private func episodeStreams(episodeId: String) async throws -> [StreamSource] {
        let parts = episodeId.split(separator: "|", maxSplits: 1).map(String.init)
        guard parts.count >= 2,
              let pageURL = HTTPClient.absoluteURL(parts[0], base: baseURL) else {
            throw ProviderError.invalidURL
        }
        let epId = parts[1]
        let html = try await HTTPClient.getHTML(url: pageURL, referer: baseURL, desktopUA: true, allowLenientTLS: true)
        let doc = try SwiftSoup.parse(html, baseURL.absoluteString)
        var sources: [StreamSource] = []

        let options = try doc.select("select#\(epId) option, select.mr-select#\(epId) option, select.episode-servers option, select[name*=server] option").array()
        for option in options {
            let serverURL = try option.attr("value").trimmingCharacters(in: .whitespacesAndNewlines)
            let serverName = try option.text().trimmingCharacters(in: .whitespacesAndNewlines)
            guard !serverURL.isEmpty,
                  serverURL.hasPrefix("http") || serverURL.hasPrefix("//"),
                  !serverURL.localizedCaseInsensitiveContains("youtube"),
                  let url = HTTPClient.absoluteURL(serverURL, base: baseURL) else { continue }
            sources.append(streamSource(id: "mk-ep-\(sources.count)", name: serverName.ifBlank("Server"), url: url))
        }

        if sources.isEmpty {
            for (index, el) in try doc.select("iframe[src], iframe[data-src], a[href*=/dl/]").array().enumerated() {
                let serverSrc: String
                if el.tagName() == "a" {
                    serverSrc = try el.attr("href")
                } else {
                    serverSrc = try el.attr("data-src").ifBlank(try el.attr("src"))
                }
                guard !serverSrc.isEmpty else { continue }
                let resolved: URL?
                if serverSrc.contains("/dl/") {
                    resolved = await resolveDlStream(serverSrc) ?? HTTPClient.absoluteURL(serverSrc, base: baseURL)
                } else {
                    resolved = HTTPClient.absoluteURL(serverSrc, base: baseURL)
                }
                guard let resolved else { continue }
                sources.append(streamSource(id: "mk-ep-fb-\(index)", name: "Server \(index + 1)", url: resolved))
            }
        }

        let distinct = sources.uniqued(by: \.url)
        if distinct.isEmpty {
            throw ProviderError.streamGate(
                "Keine Stream-Server gefunden. MEGAKino verlangt oft eine VPN-Verbindung."
            )
        }
        return distinct
    }

    private func resolveDlStream(_ href: String) async -> URL? {
        guard let url = HTTPClient.absoluteURL(href, base: baseURL) else { return nil }
        do {
            let html = try await HTTPClient.getHTML(url: url, referer: baseURL, desktopUA: true, allowLenientTLS: true)
            let doc = try SwiftSoup.parse(html, baseURL.absoluteString)
            let text = try doc.text()
            if text.localizedCaseInsensitiveContains("VPN"),
               text.localizedCaseInsensitiveContains("Verschlüsselung")
                || text.localizedCaseInsensitiveContains("einrichten") {
                return nil
            }
            let iframe = try doc.selectFirst("iframe[src], iframe[data-src]")
            let src = try iframe?.attr("data-src").ifBlank(try iframe?.attr("src")) ?? ""
            if !src.isEmpty, !src.localizedCaseInsensitiveContains("youtube"),
               let abs = HTTPClient.absoluteURL(src, base: baseURL) {
                return abs
            }
            for a in try doc.select("a[href^=http]").array() {
                let link = try a.attr("href")
                let hosts = ["voe", "mixdrop", "streamtape", "vidoza", "dood", "filemoon", "meinecloud"]
                if hosts.contains(where: { link.localizedCaseInsensitiveContains($0) }),
                   let abs = HTTPClient.absoluteURL(link, base: baseURL) {
                    return abs
                }
            }
            return nil
        } catch {
            return nil
        }
    }

    private func parseMeinecloudMirrors(embedURL: URL) async -> [StreamSource] {
        guard let html = try? await HTTPClient.getHTML(url: embedURL, referer: baseURL, desktopUA: true, allowLenientTLS: true),
              let doc = try? SwiftSoup.parse(html, embedURL.absoluteString) else { return [] }
        var sources: [StreamSource] = []
        for li in (try? doc.select("ul._source_list li[data-link], li[data-link]").array()) ?? [] {
            let raw = (try? li.attr("data-link").trimmingCharacters(in: .whitespacesAndNewlines)) ?? ""
            guard !raw.isEmpty, let decoded = decodeEmbedDataLink(raw),
                  let url = HTTPClient.absoluteURL(decoded, base: embedURL) else { continue }
            let name = li.ownText()
                .ifBlank(try? li.text())
                .ifBlank("Server")
            sources.append(streamSource(id: "mk-mc-\(sources.count)", name: name, url: url))
        }
        return sources
    }

    private func decodeEmbedDataLink(_ raw: String) -> String? {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.hasPrefix("http") || trimmed.hasPrefix("//") { return trimmed }
        guard let data = Data(base64Encoded: trimmed),
              let decoded = String(data: data, encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines),
              decoded.hasPrefix("http") || decoded.hasPrefix("//") else {
            return trimmed.contains(".") ? trimmed : nil
        }
        return decoded
    }

    private func shouldKeepEmbed(_ src: String) -> Bool {
        guard !src.isEmpty else { return false }
        if src.localizedCaseInsensitiveContains("youtube") { return false }
        if src.localizedCaseInsensitiveContains("stream-start") { return false }
        if src.hasSuffix(".png") || src.hasSuffix(".jpg") { return false }
        return true
    }

    private func streamSource(id: String, name: String, url: URL) -> StreamSource {
        StreamSource(
            id: id,
            name: name,
            url: url,
            headers: [
                "User-Agent": HTTPClient.desktopUserAgent,
                "Referer": baseURL.absoluteString,
            ],
            resolveKind: .followRedirect
        )
    }

    private func ensureToken() async throws {
        let now = Date().timeIntervalSince1970
        if now - Self.tokenBox.lastTokenTime < 10 * 60 { return }
        if let url = URL(string: "index.php?yg=token", relativeTo: baseURL)?.absoluteURL {
            _ = try? await HTTPClient.getHTML(url: url, referer: baseURL, desktopUA: true, allowLenientTLS: true)
        }
        Self.tokenBox.lastTokenTime = now
    }

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

    private static let staffelRegex = try! NSRegularExpression(pattern: #"- (\d+) Staffel"#)
    private static let episodeRegex = try! NSRegularExpression(pattern: #"Episode\s+(\d+)"#, options: [.caseInsensitive])
    private static let imdbRegex = try! NSRegularExpression(pattern: #"tt\d{7,8}"#)
}

private final class TokenBox: @unchecked Sendable {
    var lastTokenTime: TimeInterval = 0
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
