import Foundation
import SwiftSoup

/// Port of Android `FilmoProvider` (https://filmo.to/) — movies; streams via CSRF mint + redirect.
struct FilmoProvider: CatalogProvider {
    let id = "filmo"
    let name = "Filmo"
    let language = "de"
    let baseURL = URL(string: "https://filmo.to/")!

    func home() async throws -> [CategoryRow] {
        let html = try await HTTPClient.getHTML(url: baseURL, desktopUA: true, allowLenientTLS: false)
        let doc = try SwiftSoup.parse(html, baseURL.absoluteString)
        let movies = try parseMovieCards(doc)
        guard !movies.isEmpty else { return [] }
        return [CategoryRow(id: "movies", title: "Filme", items: movies)]
    }

    func search(query: String) async throws -> [MediaItem] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return [] }

        if let suggestURL = HTTPClient.apiURL(
            base: baseURL,
            path: "search/suggest",
            query: [URLQueryItem(name: "q", value: trimmed)]
        ),
           let data = try? await HTTPClient.getJSON(
               url: suggestURL,
               headers: [
                   "User-Agent": HTTPClient.desktopUserAgent,
                   "Accept": "application/json",
               ]
           ),
           let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
           let movies = root["movies"] as? [[String: Any]] {
            var items: [MediaItem] = []
            var seen = Set<String>()
            for obj in movies {
                let title = (obj["title"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                let urlString = (obj["url"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                guard !title.isEmpty, !urlString.isEmpty,
                      let abs = HTTPClient.absoluteURL(urlString, base: baseURL),
                      seen.insert(abs.absoluteString).inserted else { continue }
                items.append(
                    MediaItem(id: abs.absoluteString, title: title, kind: .movie, providerHint: self.id)
                )
            }
            if !items.isEmpty { return items }
        }

        var components = URLComponents(url: URL(string: "search", relativeTo: baseURL)!.absoluteURL, resolvingAgainstBaseURL: false)!
        components.queryItems = [URLQueryItem(name: "q", value: trimmed)]
        guard let url = components.url else { throw ProviderError.invalidURL }
        let html = try await HTTPClient.getHTML(url: url, referer: baseURL, desktopUA: true, allowLenientTLS: false)
        let doc = try SwiftSoup.parse(html, baseURL.absoluteString)
        return try parseMovieCards(doc)
    }

    func detail(id: String, kind: MediaItem.Kind) async throws -> ShowDetail {
        guard let pageURL = HTTPClient.absoluteURL(id, base: baseURL) else { throw ProviderError.invalidURL }
        let html = try await HTTPClient.getHTML(url: pageURL, referer: baseURL, desktopUA: true, allowLenientTLS: false)
        let doc = try SwiftSoup.parse(html, baseURL.absoluteString)
        let title = try doc.selectFirst("h1")?.text().trimmingCharacters(in: .whitespacesAndNewlines)
            ?? ogTitle(from: doc)
            ?? pageURL.lastPathComponent.replacingOccurrences(of: "-", with: " ").capitalized
        let overview = try doc.selectFirst("meta[property=og:description]")?.attr("content")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let posterRaw = try doc.selectFirst("img[src*=/img/poster/]")?.attr("src")
            ?? doc.selectFirst("meta[property=og:image]")?.attr("content")
        let poster = HTTPClient.absoluteURL(posterRaw, base: baseURL)
        let pageText = try doc.text()
        let year = Self.yearRegex.firstMatch(in: pageText)?.firstCaptured
            ?? Self.looseYearRegex.firstMatch(in: (try? doc.selectFirst(".ft-meta, .movie-detail")?.text()) ?? "")?.matched
        let rating = Self.ratingRegex.firstMatch(in: pageText)?.firstCaptured.flatMap(Double.init)
        let genres = try doc.select("a[href*=/genres/]").array().compactMap { a -> String? in
            let name = try a.text().trimmingCharacters(in: .whitespacesAndNewlines)
            return name.isEmpty ? nil : name
        }
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
            genres: Array(Set(genres)).sorted()
        )
    }

    func episodes(showId: String, seasonId: String) async throws -> [EpisodeInfo] {
        throw ProviderError.unsupported
    }

    func streams(showId: String, seasonId: String?, episodeId: String?, detail: ShowDetail?) async throws -> [StreamSource] {
        guard let pageURL = HTTPClient.absoluteURL(showId, base: baseURL) else { throw ProviderError.invalidURL }
        let html = try await HTTPClient.getHTML(url: pageURL, referer: baseURL, desktopUA: true, allowLenientTLS: false)
        let doc = try SwiftSoup.parse(html, baseURL.absoluteString)
        let csrf = try extractCsrf(from: doc)
        let chips = try doc.select("[data-provider-chip][data-movie-link-id][data-p]").array()

        var sources: [StreamSource] = []
        var seenLinkIds = Set<String>()
        for (idx, chip) in chips.enumerated() {
            let linkId = try chip.attr("data-movie-link-id").trimmingCharacters(in: .whitespacesAndNewlines)
            let payload = try chip.attr("data-p").trimmingCharacters(in: .whitespacesAndNewlines)
            guard !payload.isEmpty else { continue }
            if !linkId.isEmpty, !seenLinkIds.insert(linkId).inserted { continue }

            let name = try chip.attr("aria-label").trimmingCharacters(in: .whitespacesAndNewlines)
                .ifBlank(try chip.selectFirst(".provider-chip__name")?.text())
                .ifBlank("Server")

            if let hoster = try? await mintHosterURL(payload: payload, csrf: csrf) {
                sources.append(
                    StreamSource(
                        id: "filmo-\(idx)",
                        name: name,
                        url: hoster,
                        headers: [
                            "User-Agent": HTTPClient.desktopUserAgent,
                            "Referer": baseURL.absoluteString,
                        ],
                        resolveKind: .followRedirect
                    )
                )
            }
        }

        if sources.isEmpty {
            throw ProviderError.streamGate(
                "Filmo Streams nicht verfügbar (CSRF/Mint fehlgeschlagen). Seite ggf. neu laden."
            )
        }
        return sources.uniqued(by: \.url)
    }

    // MARK: - Parsing

    private func parseMovieCards(_ doc: Document) throws -> [MediaItem] {
        let cards = try doc.select(
            "a.video-card[href*=/movies/], a.movie-poster-grid-card[href*=/movies/], a.popular-spotlight-card__link[href*=/movies/]"
        ).array()
        var items: [MediaItem] = []
        var seen = Set<String>()
        for el in cards {
            guard let item = try parseVideoCard(el), seen.insert(item.id).inserted else { continue }
            items.append(item)
        }
        return items
    }

    private func parseVideoCard(_ el: Element) throws -> MediaItem? {
        var href = try el.attr("href").trimmingCharacters(in: .whitespacesAndNewlines)
        if href.isEmpty {
            href = try el.selectFirst("a[href*=/movies/]")?.attr("href")
                .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        }
        guard href.contains("/movies/"), let abs = HTTPClient.absoluteURL(href, base: baseURL) else { return nil }
        let title = try el.selectFirst(".video-card__title, .movie-poster-grid-card__title, h2, h3")?.text()
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .ifBlank(try el.selectFirst("img")?.attr("alt"))
            .ifBlank(abs.lastPathComponent.replacingOccurrences(of: "-", with: " "))
            ?? abs.lastPathComponent.replacingOccurrences(of: "-", with: " ")
        guard !title.isEmpty else { return nil }
        let posterRaw = try el.selectFirst("img")?.attr("src").ifBlank(try el.selectFirst("img")?.attr("data-src"))
        return MediaItem(
            id: abs.absoluteString,
            title: title,
            posterURL: HTTPClient.absoluteURL(posterRaw, base: baseURL),
            kind: .movie,
            providerHint: self.id
        )
    }

    private func ogTitle(from doc: Document) throws -> String? {
        guard let raw = try doc.selectFirst("meta[property=og:title]")?.attr("content") else { return nil }
        var title = raw
        if let range = title.range(of: " jetzt", options: .caseInsensitive) {
            title = String(title[..<range.lowerBound])
        }
        if let range = title.range(of: " kostenlos", options: .caseInsensitive) {
            title = String(title[..<range.lowerBound])
        }
        let trimmed = title.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
    }

    private func extractCsrf(from doc: Document) throws -> String {
        try doc.selectFirst("meta[name=csrf-token]")?.attr("content")
            .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
    }

    /// Android: POST /n with `{p}` → token `x` → GET /n/{token} → Location hoster.
    private func mintHosterURL(payload: String, csrf: String) async throws -> URL {
        var effectiveCsrf = csrf
        if effectiveCsrf.isEmpty {
            let homeHTML = try await HTTPClient.getHTML(url: baseURL, desktopUA: true, allowLenientTLS: false)
            let homeDoc = try SwiftSoup.parse(homeHTML, baseURL.absoluteString)
            effectiveCsrf = try extractCsrf(from: homeDoc)
        }
        let xsrf = readXsrfToken()
        guard !effectiveCsrf.isEmpty, !xsrf.isEmpty else {
            throw ProviderError.streamGate("Filmo CSRF-Session fehlt")
        }

        guard let mintURL = HTTPClient.apiURL(base: baseURL, path: "n") else {
            throw ProviderError.invalidURL
        }
        let body = try JSONSerialization.data(withJSONObject: ["p": payload])
        let data = try await HTTPClient.postJSON(
            url: mintURL,
            body: body,
            headers: [
                "User-Agent": HTTPClient.desktopUserAgent,
                "Content-Type": "application/json",
                "Accept": "application/json",
                "X-Requested-With": "XMLHttpRequest",
                "X-CSRF-TOKEN": effectiveCsrf,
                "X-XSRF-TOKEN": xsrf,
                "Origin": origin,
                "Referer": baseURL.absoluteString,
            ]
        )
        let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] ?? [:]
        let token = (json["x"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        guard !token.isEmpty else {
            throw ProviderError.parseFailed("Filmo Mint-Token fehlt")
        }
        guard let tokenURL = HTTPClient.apiURL(base: baseURL, path: "n/\(token)") else {
            throw ProviderError.invalidURL
        }
        // URLSession follows redirects → final hoster URL (Android reads Location once).
        let final = try await HTTPClient.followRedirects(
            url: tokenURL,
            headers: [
                "User-Agent": HTTPClient.desktopUserAgent,
                "Referer": baseURL.absoluteString,
            ]
        )
        if final.host()?.contains("filmo") == true {
            throw ProviderError.parseFailed("Filmo Hoster-Redirect fehlt")
        }
        return final
    }

    private var origin: String {
        baseURL.absoluteString.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
    }

    private func readXsrfToken() -> String {
        let cookies = HTTPCookieStorage.shared.cookies(for: baseURL) ?? []
        guard let raw = cookies.first(where: { $0.name.caseInsensitiveCompare("XSRF-TOKEN") == .orderedSame })?.value else {
            return ""
        }
        return raw.removingPercentEncoding ?? raw
    }

    private static let yearRegex = try! NSRegularExpression(
        pattern: #"Erscheinungsdatum\s+(\d{4})"#,
        options: [.caseInsensitive]
    )
    private static let looseYearRegex = try! NSRegularExpression(pattern: #"\b((?:19|20)\d{2})\b"#)
    private static let ratingRegex = try! NSRegularExpression(
        pattern: #"Bewertung\s+([\d.]+)\s*/\s*10"#,
        options: [.caseInsensitive]
    )
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
