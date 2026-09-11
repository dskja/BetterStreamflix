import Foundation
import SwiftSoup

/// Port of Android `SflixProvider` (https://sflix.to/) — home, search, detail, episodes, streams subset.
struct SflixProvider: CatalogProvider {
    let id = "sflix"
    let name = "SFlix"
    let language = "en"
    let baseURL = URL(string: "https://sflix.to/")!

    func home() async throws -> [CategoryRow] {
        guard let url = HTTPClient.apiURL(base: baseURL, path: "home") else {
            throw ProviderError.invalidURL
        }
        let html = try await HTTPClient.getHTML(url: url, referer: baseURL, desktopUA: true)
        let doc = try SwiftSoup.parse(html, baseURL.absoluteString)
        if looksLikeCloudflare(html) {
            throw ProviderError.parseFailed("SFlix blocked or unreachable (Cloudflare/empty)")
        }

        var rows: [CategoryRow] = []

        let featured = try doc.select("div.swiper-wrapper > div.swiper-slide").array().compactMap { slide -> MediaItem? in
            try parseFeaturedSlide(slide)
        }
        if !featured.isEmpty {
            rows.append(CategoryRow(id: "featured", title: "Featured", items: featured, isFeatured: true))
        }

        let trendingMovies = try parseFlwItems(doc.select("div#trending-movies div.flw-item").array(), forceKind: .movie)
        if !trendingMovies.isEmpty {
            rows.append(CategoryRow(id: "trending-movies", title: "Trending Movies", items: trendingMovies))
        }

        let trendingTV = try parseFlwItems(doc.select("div#trending-tv div.flw-item").array(), forceKind: .tvShow)
        if !trendingTV.isEmpty {
            rows.append(CategoryRow(id: "trending-tv", title: "Trending TV Shows", items: trendingTV))
        }

        for section in try doc.select("section.section-id-02").array() {
            let heading = try section.selectFirst("h2.cat-heading")?.ownText()
                .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            let items = try parseFlwItems(section.select("div.flw-item").array())
            guard !items.isEmpty else { continue }
            if heading == "Latest Movies" {
                rows.append(CategoryRow(id: "latest-movies", title: heading, items: items))
            } else if heading == "Latest TV Shows" {
                rows.append(CategoryRow(id: "latest-tv", title: heading, items: items))
            }
        }

        return rows
    }

    func search(query: String) async throws -> [MediaItem] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return [] }
        let slug = trimmed.replacingOccurrences(of: " ", with: "-")
        guard let url = HTTPClient.apiURL(
            base: baseURL,
            path: "search/\(slug)",
            query: [URLQueryItem(name: "page", value: "1")]
        ) else {
            throw ProviderError.invalidURL
        }
        let html = try await HTTPClient.getHTML(url: url, referer: baseURL, desktopUA: true)
        let doc = try SwiftSoup.parse(html, baseURL.absoluteString)
        return try parseFlwItems(doc.select("div.flw-item").array())
    }

    func detail(id: String, kind: MediaItem.Kind) async throws -> ShowDetail {
        guard let pageURL = HTTPClient.absoluteURL(id, base: baseURL) else {
            throw ProviderError.invalidURL
        }
        let html = try await HTTPClient.getHTML(url: pageURL, referer: baseURL, desktopUA: true)
        let doc = try SwiftSoup.parse(html, baseURL.absoluteString)

        let title = try doc.selectFirst("h2.heading-name")?.text()
            .trimmingCharacters(in: .whitespacesAndNewlines) ?? pageURL.lastPathComponent
        let overview = try doc.selectFirst("div.description")?.ownText()
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let poster = HTTPClient.absoluteURL(
            try doc.selectFirst("div.detail_page-watch img.film-poster-img")?.attr("src"),
            base: baseURL
        )
        let bannerStyle = try doc.selectFirst("div.detail-container > div.cover_follow")?.attr("style") ?? ""
        let banner = bannerURL(from: bannerStyle) ?? poster
        let released = try rowLine(doc, containing: "Released")?
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let year: String? = {
            guard let released, released.count >= 4 else { return nil }
            return String(released.prefix(4))
        }()
        let ratingText = try doc.selectFirst(".fs-item > .imdb")?.text()
            .replacingOccurrences(of: "IMDB:", with: "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let rating = Double(ratingText ?? "")
        let genres = try rowLinks(doc, containing: "Genre")
        let cast = try rowLinks(doc, containing: "Cast")

        let isMovie = kind == .movie || id.contains("/movie/")
        if isMovie {
            return ShowDetail(
                id: id,
                title: title,
                overview: overview?.isEmpty == true ? nil : overview,
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

        let numericalId = numericalID(from: id)
        var seasons: [SeasonInfo] = []
        if let seasonsURL = HTTPClient.apiURL(base: baseURL, path: "ajax/season/list/\(numericalId)"),
           let seasonsHTML = try? await HTTPClient.getHTML(url: seasonsURL, referer: pageURL, desktopUA: true),
           let seasonsDoc = try? SwiftSoup.parse(seasonsHTML) {
            for (idx, a) in try seasonsDoc.select("div.dropdown-menu.dropdown-menu-model > a").array().enumerated() {
                let sid = try a.attr("data-id").trimmingCharacters(in: .whitespacesAndNewlines)
                guard !sid.isEmpty else { continue }
                let label = try a.text().trimmingCharacters(in: .whitespacesAndNewlines)
                seasons.append(
                    SeasonInfo(
                        id: sid,
                        number: idx + 1,
                        title: label.isEmpty ? "Season \(idx + 1)" : label
                    )
                )
            }
        }

        return ShowDetail(
            id: id,
            title: title,
            overview: overview?.isEmpty == true ? nil : overview,
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
        guard let url = HTTPClient.apiURL(base: baseURL, path: "ajax/season/episodes/\(seasonId)") else {
            throw ProviderError.invalidURL
        }
        let html = try await HTTPClient.getHTML(url: url, referer: baseURL, desktopUA: true)
        let doc = try SwiftSoup.parse(html)
        return try doc.select("div.flw-item.film_single-item.episode-item.eps-item").array().enumerated().compactMap { idx, el in
            let eid = try el.attr("data-id").trimmingCharacters(in: .whitespacesAndNewlines)
            guard !eid.isEmpty else { return nil }
            let numberText = try el.selectFirst("div.episode-number")?.text() ?? ""
            let number = Int(numberText.components(separatedBy: "Episode ").last?
                .components(separatedBy: ":").first?
                .trimmingCharacters(in: .whitespacesAndNewlines) ?? "") ?? (idx + 1)
            let title = try el.selectFirst("h3.film-name")?.text()
                .trimmingCharacters(in: .whitespacesAndNewlines) ?? "Episode \(number)"
            let thumb = HTTPClient.absoluteURL(try el.selectFirst("img")?.attr("src"), base: baseURL)
            return EpisodeInfo(id: eid, number: number, title: title, thumbnailURL: thumb)
        }
    }

    func streams(showId: String, seasonId: String?, episodeId: String?, detail: ShowDetail?) async throws -> [StreamSource] {
        let serverPath: String
        if let episodeId, !episodeId.isEmpty, !(detail?.kind == .movie) {
            serverPath = "ajax/episode/servers/\(episodeId)"
        } else {
            serverPath = "ajax/episode/list/\(numericalID(from: showId))"
        }
        guard let serversURL = HTTPClient.apiURL(base: baseURL, path: serverPath) else {
            throw ProviderError.invalidURL
        }
        let serversHTML = try await HTTPClient.getHTML(url: serversURL, referer: baseURL, desktopUA: true)
        let serversDoc = try SwiftSoup.parse(serversHTML)
        var sources: [StreamSource] = []
        for (idx, a) in try serversDoc.select("a").array().enumerated() {
            let dataID = try a.attr("data-id").trimmingCharacters(in: .whitespacesAndNewlines)
            guard !dataID.isEmpty else { continue }
            let name = try a.selectFirst("span")?.text().trimmingCharacters(in: .whitespacesAndNewlines)
                ?? "Server \(idx + 1)"
            guard let linkURL = HTTPClient.apiURL(base: baseURL, path: "ajax/episode/sources/\(dataID)"),
                  let data = try? await HTTPClient.getJSON(
                      url: linkURL,
                      headers: [
                          "User-Agent": HTTPClient.desktopUserAgent,
                          "Referer": baseURL.absoluteString,
                          "Accept": "application/json",
                      ]
                  ),
                  let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let link = (json["link"] as? String)?.trimmingCharacters(in: .whitespacesAndNewlines),
                  !link.isEmpty,
                  let streamURL = URL(string: link) else { continue }
            sources.append(
                StreamSource(
                    id: "sflix-\(dataID)",
                    name: name,
                    url: streamURL,
                    headers: defaultHeaders,
                    resolveKind: .followRedirect
                )
            )
        }
        if sources.isEmpty {
            throw ProviderError.parseFailed("No SFlix stream links found")
        }
        return sources
    }

    // MARK: - Parsing

    private func parseFeaturedSlide(_ slide: Element) throws -> MediaItem? {
        let href = try slide.selectFirst("a")?.attr("href")
            .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        guard !href.isEmpty else { return nil }
        let title = try slide.selectFirst("h2.film-title")?.text()
            .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        guard !title.isEmpty else { return nil }
        let overview = try slide.selectFirst("p.sc-desc")?.text()
        let poster = HTTPClient.absoluteURL(
            try slide.selectFirst("img.film-poster-img")?.attr("src"),
            base: baseURL
        )
        let banner = HTTPClient.absoluteURL(
            try slide.selectFirst("div.slide-photo img")?.attr("src"),
            base: baseURL
        )
        let kind: MediaItem.Kind = href.contains("/movie/") ? .movie : .tvShow
        return MediaItem(
            id: href,
            title: title,
            posterURL: poster,
            bannerURL: banner,
            overview: overview,
            kind: kind,
            providerHint: self.id
        )
    }

    private func parseFlwItems(_ elements: [Element], forceKind: MediaItem.Kind? = nil) throws -> [MediaItem] {
        var items: [MediaItem] = []
        var seen = Set<String>()
        for el in elements {
            let href = try el.selectFirst("a")?.attr("href")
                .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            guard !href.isEmpty, seen.insert(href).inserted else { continue }
            let title = try el.selectFirst("h3.film-name, h2.film-name")?.text()
                .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            guard !title.isEmpty else { continue }
            let poster = HTTPClient.absoluteURL(
                try el.selectFirst("div.film-poster > img.film-poster-img")?.attr("data-src")
                    ?? el.selectFirst("div.film-poster > img.film-poster-img")?.attr("src"),
                base: baseURL
            )
            let kind = forceKind ?? (href.contains("/movie/") ? .movie : .tvShow)
            let spans = try el.select("div.film-detail > div.fd-infor > span").array().map { try $0.text() }
            let year = spans.first { $0.range(of: #"^\d{4}$"#, options: .regularExpression) != nil }
            let rating = spans.first { $0.range(of: #"^\d(?:\.\d)?$"#, options: .regularExpression) != nil }
                .flatMap { Double($0) }
            items.append(
                MediaItem(
                    id: href,
                    title: title,
                    posterURL: poster,
                    year: year,
                    rating: rating,
                    kind: kind,
                    providerHint: self.id
                )
            )
        }
        return items
    }

    private func rowLine(_ doc: Document, containing needle: String) throws -> String? {
        for row in try doc.select("div.elements > .row > div > .row-line").array() {
            let type = try row.select(".type").text()
            if type.localizedCaseInsensitiveContains(needle) {
                return row.ownText()
            }
        }
        return nil
    }

    private func rowLinks(_ doc: Document, containing needle: String) throws -> [String] {
        for row in try doc.select("div.elements > .row > div > .row-line").array() {
            let type = try row.select(".type").text()
            if type.localizedCaseInsensitiveContains(needle) {
                return try row.select("a").array().compactMap {
                    let t = try $0.text().trimmingCharacters(in: .whitespacesAndNewlines)
                    return t.isEmpty ? nil : t
                }
            }
        }
        return []
    }

    private func numericalID(from id: String) -> String {
        if let last = id.split(separator: "-").last, last.allSatisfy(\.isNumber) {
            return String(last)
        }
        return id.split(separator: "/").last.map(String.init) ?? id
    }

    private func bannerURL(from style: String) -> URL? {
        guard let start = style.range(of: "url(")?.upperBound,
              let end = style[start...].range(of: ")")?.lowerBound else { return nil }
        let raw = String(style[start..<end]).trimmingCharacters(in: CharacterSet(charactersIn: "\"' "))
        return HTTPClient.absoluteURL(raw, base: baseURL)
    }

    private func looksLikeCloudflare(_ html: String) -> Bool {
        html.localizedCaseInsensitiveContains("Just a moment")
            || html.localizedCaseInsensitiveContains("cf-browser-verification")
            || html.localizedCaseInsensitiveContains("Error code 522")
    }

    private var defaultHeaders: [String: String] {
        [
            "User-Agent": HTTPClient.desktopUserAgent,
            "Referer": baseURL.absoluteString,
        ]
    }
}
