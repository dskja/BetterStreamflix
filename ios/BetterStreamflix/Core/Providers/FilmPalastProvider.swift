import Foundation
import SwiftSoup

struct FilmPalastProvider: CatalogProvider {
    let id = "filmpalast"
    let name = "Filmpalast"
    let language = "de"
    let baseURL = URL(string: "https://filmpalast.to/")!

    func home() async throws -> [CategoryRow] {
        var rows: [CategoryRow] = []

        if let html = try? await HTTPClient.getHTML(
            url: URL(string: "movies/new/page/1", relativeTo: baseURL)!.absoluteURL,
            desktopUA: true,
            allowLenientTLS: true
        ),
           let doc = try? SwiftSoup.parse(html, baseURL.absoluteString) {
            let featured = try doc.select("div.headerslider ul#sliderDla li").array().compactMap { li -> MediaItem? in
                let title = try li.selectFirst("span.title.rb")?.text() ?? ""
                let href = try li.selectFirst("a.moviSliderPlay")?.attr("href") ?? ""
                let id = href.split(separator: "/").last.map(String.init) ?? ""
                guard !id.isEmpty, !title.isEmpty else { return nil }
                let poster = HTTPClient.absoluteURL(try li.selectFirst("a img")?.attr("src"), base: baseURL)
                return MediaItem(
                    id: id,
                    title: title,
                    posterURL: poster,
                    bannerURL: poster,
                    overview: try li.selectFirst("div.moviedescription")?.text(),
                    year: try li.selectFirst("span.releasedate b")?.text(),
                    kind: .movie,
                    providerHint: self.id
                )
            }
            if !featured.isEmpty {
                rows.append(CategoryRow(id: "featured", title: "Featured", items: featured, isFeatured: true))
            }

            let movies = try parseArticles(doc, kind: .movie)
            if !movies.isEmpty {
                rows.append(CategoryRow(id: "movies", title: "Filme", items: movies))
            }
        }

        if let tvHTML = try? await HTTPClient.getHTML(
            url: URL(string: "serien/view/page/1", relativeTo: baseURL)!.absoluteURL,
            referer: baseURL,
            desktopUA: true,
            allowLenientTLS: true
        ),
           let tvDoc = try? SwiftSoup.parse(tvHTML, baseURL.absoluteString) {
            let shows = try parseArticles(tvDoc, kind: .tvShow)
            if !shows.isEmpty {
                rows.append(CategoryRow(id: "series", title: "Serien", items: shows))
            }
        }

        if rows.isEmpty {
            throw ProviderError.parseFailed("Filmpalast unreachable")
        }
        return rows
    }

    func search(query: String) async throws -> [MediaItem] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return [] }
        let encoded = trimmed.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? trimmed
        let url = URL(string: "search/title/\(encoded)", relativeTo: baseURL)!.absoluteURL
        let html = try await HTTPClient.getHTML(url: url, referer: baseURL, desktopUA: true, allowLenientTLS: true)
        let doc = try SwiftSoup.parse(html, baseURL.absoluteString)
        // Prefer path-based kind detection
        var items: [MediaItem] = []
        var seen = Set<String>()
        for article in try doc.select("div#content article").array() {
            let href = try article.selectFirst("h2 a")?.attr("href") ?? ""
            let id = href.split(separator: "/").last.map(String.init) ?? ""
            guard !id.isEmpty, seen.insert(id).inserted else { continue }
            let title = try article.selectFirst("h2 a")?.text() ?? id
            let poster = HTTPClient.absoluteURL(try article.selectFirst("a img")?.attr("src"), base: baseURL)
            let kind: MediaItem.Kind = href.contains("/serien/") || href.contains("/series/") ? .tvShow : .movie
            items.append(
                MediaItem(id: id, title: title, posterURL: poster, kind: kind, providerHint: self.id)
            )
        }
        if items.isEmpty {
            items = (try parseArticles(doc, kind: .movie)) + (try parseArticles(doc, kind: .tvShow))
        }
        return items
    }

    func detail(id: String, kind: MediaItem.Kind) async throws -> ShowDetail {
        let pathCandidates = [
            "stream/\(id).html",
            "stream/\(id)",
            id.hasSuffix(".html") ? "stream/\(id)" : "stream/\(id).html",
        ]
        var lastError: Error = ProviderError.emptyResponse
        for path in pathCandidates {
            guard let url = URL(string: path, relativeTo: baseURL)?.absoluteURL else { continue }
            do {
                let html = try await HTTPClient.getHTML(url: url, referer: baseURL, desktopUA: true, allowLenientTLS: true)
                let doc = try SwiftSoup.parse(html, baseURL.absoluteString)
                let title = try doc.selectFirst("h1")?.text()
                    ?? id.replacingOccurrences(of: "-", with: " ").capitalized
                let overview = try doc.selectFirst("div.detail-desc, #detail-desc, .seriesDescr")?.text()
                let poster = HTTPClient.absoluteURL(
                    try doc.selectFirst(".detail-cover img, .cover img, img[itemprop=image]")?.attr("src"),
                    base: baseURL
                )
                return ShowDetail(
                    id: id,
                    title: title,
                    overview: overview,
                    posterURL: poster,
                    bannerURL: poster,
                    year: nil,
                    rating: nil,
                    seasons: kind == .tvShow
                        ? [SeasonInfo(id: id, number: 1, title: "Episodes")]
                        : [],
                    kind: kind
                )
            } catch {
                lastError = error
            }
        }
        throw lastError
    }

    func episodes(showId: String, seasonId: String) async throws -> [EpisodeInfo] {
        let url = URL(string: "stream/\(showId).html", relativeTo: baseURL)!.absoluteURL
        let html = try await HTTPClient.getHTML(url: url, referer: baseURL, desktopUA: true, allowLenientTLS: true)
        let doc = try SwiftSoup.parse(html, baseURL.absoluteString)
        var episodes: [EpisodeInfo] = []
        for (idx, link) in try doc.select("a[href*=staffel], a[href*=episode], .episodelist a").array().enumerated() {
            let href = try link.attr("href")
            let title = try link.text().trimmingCharacters(in: .whitespacesAndNewlines)
            let epId = href.split(separator: "/").last.map(String.init) ?? "\(showId)-e\(idx + 1)"
            episodes.append(
                EpisodeInfo(
                    id: epId,
                    number: idx + 1,
                    title: title.isEmpty ? "Episode \(idx + 1)" : title
                )
            )
        }
        if episodes.isEmpty {
            episodes = [EpisodeInfo(id: showId, number: 1, title: "Play")]
        }
        return episodes
    }

    func streams(showId: String, seasonId: String?, episodeId: String?, detail: ShowDetail?) async throws -> [StreamSource] {
        let target = episodeId ?? showId
        let path = target.contains(".html") ? "stream/\(target)" : "stream/\(target).html"
        let url = URL(string: path, relativeTo: baseURL)!.absoluteURL
        let html = try await HTTPClient.getHTML(url: url, referer: baseURL, desktopUA: true, allowLenientTLS: true)
        let doc = try SwiftSoup.parse(html, baseURL.absoluteString)
        var sources: [StreamSource] = []

        for (idx, el) in try doc.select("ul.currentStreamLinks a, a.iconPlay, a[data-player-url], a[href*=voe], a[href*=streamtape], a[href*=vidoza]").array().enumerated() {
            let href = try el.attr("data-player-url").ifBlank(try el.attr("href"))
            guard var streamURL = HTTPClient.absoluteURL(href, base: baseURL) else { continue }
            let name = try el.text().trimmingCharacters(in: .whitespacesAndNewlines)
            if streamURL.host()?.contains("filmpalast") == true,
               let path = URLComponents(url: streamURL, resolvingAgainstBaseURL: false)?.path,
               path.contains("/e/") || path.contains("/v/") {
                // leave as-is; followRedirect will resolve
            }
            // VOE often needs voe.sx host
            if let host = streamURL.host(), host.contains("voe"), !host.contains("voe.sx") {
                streamURL = URL(string: "https://voe.sx\(streamURL.path)") ?? streamURL
            }
            sources.append(
                StreamSource(
                    id: "fp-\(idx)",
                    name: name.isEmpty ? "Host \(idx + 1)" : name,
                    url: streamURL,
                    headers: ["User-Agent": HTTPClient.desktopUserAgent, "Referer": baseURL.absoluteString],
                    resolveKind: .followRedirect
                )
            )
        }
        return sources.uniqued(by: \.url)
    }

    private func parseArticles(_ doc: Document, kind: MediaItem.Kind) throws -> [MediaItem] {
        var items: [MediaItem] = []
        var seen = Set<String>()
        for article in try doc.select("div#content article").array() {
            let href = try article.selectFirst("h2 a")?.attr("href") ?? ""
            let id = href.split(separator: "/").last.map(String.init) ?? ""
            guard !id.isEmpty, seen.insert(id).inserted else { continue }
            let title = try article.selectFirst("h2 a")?.text() ?? id
            let poster = HTTPClient.absoluteURL(try article.selectFirst("a img")?.attr("src"), base: baseURL)
            items.append(
                MediaItem(id: id, title: title, posterURL: poster, kind: kind, providerHint: self.id)
            )
        }
        return items
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
