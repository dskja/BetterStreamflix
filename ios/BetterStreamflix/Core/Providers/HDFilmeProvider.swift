import Foundation
import SwiftSoup

/// Port of Android `HDFilmeProvider` (https://hdfilme.cafe/) — solid subset: home, search, detail, episodes, streams.
struct HDFilmeProvider: CatalogProvider {
    let id = "hdfilme"
    let name = "HDFilme"
    let language = "de"
    let baseURL = URL(string: "https://hdfilme.cafe/")!

    private static let sitemapBox = SitemapBox()

    func home() async throws -> [CategoryRow] {
        let html = try await HTTPClient.getHTML(url: baseURL, desktopUA: true, allowLenientTLS: true)
        let doc = try SwiftSoup.parse(html, baseURL.absoluteString)
        var rows: [CategoryRow] = []

        let featured = try doc.select("ul.glide__slides li.glide__slide").array().compactMap { el -> MediaItem? in
            let title = try el.selectFirst("h3.title")?.text().trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            let href = try el.selectFirst("div.actions a.watchnow")?.attr("href")
                .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            guard !title.isEmpty, !href.isEmpty, let abs = HTTPClient.absoluteURL(href, base: baseURL) else { return nil }
            let banner = HTTPClient.absoluteURL(try el.selectFirst("img")?.attr("data-src"), base: baseURL)
            return MediaItem(
                id: abs.absoluteString,
                title: title,
                posterURL: banner,
                bannerURL: banner,
                kind: .movie,
                providerHint: self.id
            )
        }
        if !featured.isEmpty {
            rows.append(CategoryRow(id: "featured", title: "Featured", items: featured, isFeatured: true))
        }

        if let listing = try doc.selectFirst("div.listing.grid[id=dle-content]") {
            let items = try listing.select("div.item.relative.mt-3").array().compactMap { try parseGridItem($0, kind: .movie) }
            if !items.isEmpty {
                rows.append(CategoryRow(id: "filme", title: "Filme", items: items))
            }
        }

        let sections = try doc.select("section.sidebar-section").array()
        for sec in sections {
            let heading = try sec.selectFirst("h3")?.text() ?? ""
            if heading.localizedCaseInsensitiveContains("neueste Filme eingefügt") {
                let items = try parseSidebarLinks(sec, kind: .movie)
                if !items.isEmpty {
                    rows.append(CategoryRow(id: "latest-movies", title: "Neueste Filme Eingefügt", items: items))
                }
            } else if heading.localizedCaseInsensitiveContains("neueste Serie eingefügt") {
                let items = try parseSidebarLinks(sec, kind: .tvShow)
                if !items.isEmpty {
                    rows.append(CategoryRow(id: "latest-series", title: "Neueste Serie Eingefügt", items: items))
                }
            }
        }

        return rows
    }

    func search(query: String) async throws -> [MediaItem] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return [] }

        if let sitemapHits = try? await searchSitemap(query: trimmed), !sitemapHits.isEmpty {
            return sitemapHits
        }
        return try await searchCatalogue(query: trimmed)
    }

    func detail(id: String, kind: MediaItem.Kind) async throws -> ShowDetail {
        guard let pageURL = HTTPClient.absoluteURL(id, base: baseURL) else { throw ProviderError.invalidURL }
        let html = try await HTTPClient.getHTML(url: pageURL, referer: baseURL, desktopUA: true, allowLenientTLS: true)
        let doc = try SwiftSoup.parse(html, baseURL.absoluteString)
        let titleRaw = try doc.selectFirst("h1.font-bold, h1")?.text().trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let title = titleRaw.replacingOccurrences(of: #"\s*hdfilme\s*$"#, with: "", options: [.regularExpression, .caseInsensitive])
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let poster = HTTPClient.absoluteURL(
            try doc.selectFirst("figure.inline-block img")?.attr("data-src")
                .ifBlank(try doc.selectFirst("figure.inline-block img")?.attr("src")),
            base: baseURL
        )
        let overview = try extractOverview(doc)
        let year = try extractYear(doc)
        let ratingText = try doc.selectFirst("p.imdb-badge span.imdb-rate")?.text().trimmingCharacters(in: .whitespacesAndNewlines)
        let rating = ratingText.flatMap { Double($0) }
        let genres = try extractGenres(doc)
        let cast = try extractCast(doc)
        let isTV = kind == .tvShow || isTvShowDocument(doc)

        if isTV {
            let seasons = try await resolveSeasons(showId: id, doc: doc)
            return ShowDetail(
                id: id,
                title: title.isEmpty ? pageURL.lastPathComponent : title,
                overview: overview,
                posterURL: poster,
                bannerURL: poster,
                year: year,
                rating: rating,
                seasons: seasons,
                kind: .tvShow,
                genres: genres,
                cast: cast
            )
        }

        return ShowDetail(
            id: id,
            title: title.isEmpty ? pageURL.lastPathComponent : title,
            overview: overview,
            posterURL: poster,
            bannerURL: poster,
            year: year,
            rating: rating,
            seasons: [],
            kind: .movie,
            genres: genres,
            cast: cast
        )
    }

    func episodes(showId: String, seasonId: String) async throws -> [EpisodeInfo] {
        let showURLString = seasonId.contains("#")
            ? String(seasonId.split(separator: "#").first ?? Substring(showId))
            : showId
        let seasonNumber = Int(seasonId.components(separatedBy: "#season-").last ?? "") ?? 1
        guard let pageURL = HTTPClient.absoluteURL(showURLString, base: baseURL) else {
            throw ProviderError.invalidURL
        }
        let html = try await HTTPClient.getHTML(url: pageURL, referer: baseURL, desktopUA: true, allowLenientTLS: true)
        let doc = try SwiftSoup.parse(html, baseURL.absoluteString)

        if let serialDoc = await getSerialDocument(doc) {
            let eps = try parseSerialEpisodes(showURL: pageURL.absoluteString, seasonNumber: seasonNumber, serialDoc: serialDoc)
            if !eps.isEmpty { return eps }
        }

        return try parseAccordionEpisodes(showURL: pageURL.absoluteString, seasonNumber: seasonNumber, doc: doc)
    }

    func streams(showId: String, seasonId: String?, episodeId: String?, detail: ShowDetail?) async throws -> [StreamSource] {
        if let episodeId, episodeId.contains("#s") {
            return try await episodeStreams(episodeId: episodeId)
        }

        guard let pageURL = HTTPClient.absoluteURL(showId, base: baseURL) else { throw ProviderError.invalidURL }
        let html = try await HTTPClient.getHTML(url: pageURL, referer: baseURL, desktopUA: true, allowLenientTLS: true)
        let doc = try SwiftSoup.parse(html, baseURL.absoluteString)
        return try await movieStreams(from: doc)
    }

    // MARK: - Home / search helpers

    private func parseSidebarLinks(_ section: Element, kind: MediaItem.Kind) throws -> [MediaItem] {
        try section.select("div.listing > a").array().compactMap { a in
            let href = try a.attr("href").trimmingCharacters(in: .whitespacesAndNewlines)
            guard !href.isEmpty, let abs = HTTPClient.absoluteURL(href, base: baseURL) else { return nil }
            let title = (try a.selectFirst("figcaption.hidden")?.text()
                .ifBlank(try a.selectFirst("h4.movie-title")?.text()) ?? "")
                .trimmingCharacters(in: .whitespacesAndNewlines)
            guard !title.isEmpty else { return nil }
            let poster = HTTPClient.absoluteURL(try a.selectFirst("img")?.attr("data-src"), base: baseURL)
            return MediaItem(id: abs.absoluteString, title: title, posterURL: poster, kind: kind, providerHint: self.id)
        }
    }

    private func parseGridItem(_ el: Element, kind: MediaItem.Kind) throws -> MediaItem? {
        let title = try el.selectFirst("h3.line-clamp-2.text-sm.mt-1.font-light.leading-snug, h3.line-clamp-2")?.text()
            .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        let href = try el.selectFirst("a.block.relative[href], a[href]")?.attr("href")
            .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        guard !title.isEmpty, !href.isEmpty, let abs = HTTPClient.absoluteURL(href, base: baseURL) else { return nil }
        let poster = HTTPClient.absoluteURL(try el.selectFirst("img")?.attr("data-src"), base: baseURL)
        return MediaItem(id: abs.absoluteString, title: title, posterURL: poster, kind: kind, providerHint: self.id)
    }

    private func searchSitemap(query: String) async throws -> [MediaItem] {
        let entries = try await sitemapEntries()
        let tokens = searchTokens(query)
        guard !tokens.isEmpty else { return [] }
        let matching = entries.filter { entry in
            tokens.allSatisfy { entry.searchableSlug.contains($0) }
        }.prefix(12)

        var items: [MediaItem] = []
        for entry in matching {
            guard let pageURL = URL(string: entry.url) else { continue }
            guard let html = try? await HTTPClient.getHTML(url: pageURL, referer: baseURL, desktopUA: true, allowLenientTLS: true),
                  let doc = try? SwiftSoup.parse(html, pageURL.absoluteString),
                  let item = try? parseSearchResult(url: entry.url, doc: doc) else { continue }
            items.append(item)
        }
        return items
    }

    private func searchCatalogue(query: String) async throws -> [MediaItem] {
        var items: [MediaItem] = []
        var seen = Set<String>()
        for page in 1...2 {
            let moviePath = page == 1 ? "filme1/" : "filme1/page/\(page)/"
            let seriesPath = page == 1 ? "serien/" : "serien/page/\(page)/"
            if let movieURL = URL(string: moviePath, relativeTo: baseURL)?.absoluteURL,
               let html = try? await HTTPClient.getHTML(url: movieURL, referer: baseURL, desktopUA: true, allowLenientTLS: true),
               let doc = try? SwiftSoup.parse(html, baseURL.absoluteString) {
                for el in try doc.select("div.listing.grid[id=dle-content] div.item.relative.mt-3").array() {
                    let title = try el.selectFirst("h3.line-clamp-2")?.text() ?? ""
                    guard title.localizedCaseInsensitiveContains(query),
                          let item = try parseGridItem(el, kind: .movie),
                          seen.insert(item.id).inserted else { continue }
                    items.append(item)
                }
            }
            if let seriesURL = URL(string: seriesPath, relativeTo: baseURL)?.absoluteURL,
               let html = try? await HTTPClient.getHTML(url: seriesURL, referer: baseURL, desktopUA: true, allowLenientTLS: true),
               let doc = try? SwiftSoup.parse(html, baseURL.absoluteString) {
                for el in try doc.select("div.listing.grid[id=dle-content] div.item.relative.mt-3").array() {
                    let title = try el.selectFirst("h3.line-clamp-2")?.text() ?? ""
                    guard title.localizedCaseInsensitiveContains(query),
                          let item = try parseGridItem(el, kind: .tvShow),
                          seen.insert(item.id).inserted else { continue }
                    items.append(item)
                }
            }
        }
        return items
    }

    private func parseSearchResult(url: String, doc: Document) throws -> MediaItem? {
        var title = try doc.selectFirst("h1.font-bold")?.text().trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        title = title.replacingOccurrences(of: #"\s*hdfilme\s*$"#, with: "", options: [.regularExpression, .caseInsensitive])
            .trimmingCharacters(in: .whitespacesAndNewlines)
        if title.isEmpty {
            title = try doc.selectFirst("meta[property=og:title]")?.attr("content")
                .components(separatedBy: " Stream").first?
                .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
        }
        guard !title.isEmpty else { return nil }
        let poster = HTTPClient.absoluteURL(
            try doc.selectFirst("figure.inline-block img")?.attr("data-src"),
            base: baseURL
        )
        return MediaItem(
            id: url,
            title: title,
            posterURL: poster,
            kind: isTvShowDocument(doc) ? .tvShow : .movie,
            providerHint: self.id
        )
    }

    private func searchTokens(_ value: String) -> [String] {
        value.lowercased()
            .replacingOccurrences(of: #"^\d+-"#, with: "", options: .regularExpression)
            .replacingOccurrences(of: #"-stream(?:ing)?-stream\.html$"#, with: "", options: .regularExpression)
            .split(whereSeparator: { !$0.isLetter && !$0.isNumber })
            .map(String.init)
            .filter { !$0.isEmpty }
    }

    private func sitemapEntries() async throws -> [SitemapEntry] {
        if let cached = Self.sitemapBox.entries { return cached }
        var all: [SitemapEntry] = []
        for name in ["news_pages.xml", "news_pages2.xml"] {
            guard let url = URL(string: name, relativeTo: baseURL)?.absoluteURL else { continue }
            guard let data = try? await HTTPClient.getJSON(
                url: url,
                headers: [
                    "User-Agent": HTTPClient.desktopUserAgent,
                    "Accept": "application/xml,text/xml,*/*",
                ]
            ),
                  let xml = String(data: data, encoding: .utf8) else { continue }
            all.append(contentsOf: parseSitemapXML(xml))
        }
        var seen = Set<String>()
        let unique = all.filter { seen.insert($0.url).inserted }
        Self.sitemapBox.entries = unique
        return unique
    }

    private func parseSitemapXML(_ xml: String) -> [SitemapEntry] {
        var entries: [SitemapEntry] = []
        let pattern = #"<loc>\s*([^<]+)\s*</loc>"#
        guard let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive]) else { return [] }
        let range = NSRange(xml.startIndex..., in: xml)
        regex.enumerateMatches(in: xml, options: [], range: range) { match, _, _ in
            guard let match, match.numberOfRanges > 1,
                  let cap = Range(match.range(at: 1), in: xml) else { return }
            let url = String(xml[cap]).trimmingCharacters(in: .whitespacesAndNewlines)
            guard !url.isEmpty else { return }
            let encodedSlug = url.split(separator: "/").last.map(String.init) ?? url
            let slug = encodedSlug.removingPercentEncoding ?? encodedSlug
            entries.append(SitemapEntry(url: url, searchableSlug: slug.lowercased()))
        }
        return entries
    }

    // MARK: - Detail / episodes

    private func isTvShowDocument(_ doc: Document) -> Bool {
        (try? doc.selectFirst("a[href*=themoviedb.org/tv/], .info a[href$=/serien/], #serial_iframe, div#se-accordion")) != nil
    }

    private func extractOverview(_ doc: Document) throws -> String? {
        guard let p = try doc.selectFirst("div.font-extralight.prose.max-w-none p") else { return nil }
        var text = try p.text().trimmingCharacters(in: .whitespacesAndNewlines)
        if let ref = text.range(of: "Referenzen von") {
            text = String(text[..<ref.lowerBound]).trimmingCharacters(in: .whitespacesAndNewlines)
        }
        return text.isEmpty ? nil : text
    }

    private func extractYear(_ doc: Document) throws -> String? {
        for span in try doc.select("div.border-b.border-gray-700.font-extralight span").array() {
            let text = try span.text().trimmingCharacters(in: .whitespacesAndNewlines)
            if text.range(of: #"^\d{4}$"#, options: .regularExpression) != nil { return text }
        }
        return nil
    }

    private func extractGenres(_ doc: Document) throws -> [String] {
        guard let firstSpan = try doc.selectFirst("div.border-b.border-gray-700.font-extralight span") else { return [] }
        return try firstSpan.select("a").array().compactMap { a in
            let name = try a.text().trimmingCharacters(in: .whitespacesAndNewlines)
            return name.isEmpty ? nil : name
        }
    }

    private func extractCast(_ doc: Document) throws -> [String] {
        try doc.select("ul.space-y-1 li a[href*=/xfsearch/actors/]").array().compactMap { a in
            let name = try a.text().trimmingCharacters(in: .whitespacesAndNewlines)
            return (name.isEmpty || name == "N/A") ? nil : name
        }
    }

    private func resolveSeasons(showId: String, doc: Document) async throws -> [SeasonInfo] {
        if let serialDoc = await getSerialDocument(doc) {
            var seasons: [SeasonInfo] = []
            for serialSeason in try serialDoc.select("._season-eps").array() {
                guard let number = try serialSeasonNumber(serialSeason, serialDoc: serialDoc) else { continue }
                seasons.append(SeasonInfo(id: "\(showId)#season-\(number)", number: number, title: "Staffel \(number)"))
            }
            if !seasons.isEmpty { return seasons.sorted { $0.number < $1.number } }
        }

        var seasons: [SeasonInfo] = []
        for spoiler in try doc.select("div#se-accordion div.su-spoiler").array() {
            let seasonTitle = try spoiler.selectFirst("div.su-spoiler-title")?.text()
                .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            guard let number = Self.staffelRegex.firstMatch(in: seasonTitle)?.firstCaptured.flatMap(Int.init) else { continue }
            seasons.append(SeasonInfo(id: "\(showId)#season-\(number)", number: number, title: "Staffel \(number)"))
        }
        return seasons.sorted { $0.number < $1.number }
    }

    private func parseSerialEpisodes(showURL: String, seasonNumber: Int, serialDoc: Document) throws -> [EpisodeInfo] {
        guard let season = try serialDoc.select("._season-eps").array().first(where: { el in
            (try? serialSeasonNumber(el, serialDoc: serialDoc)) == seasonNumber
        }) else { return [] }

        var episodes: [EpisodeInfo] = []
        for element in try season.select("._ep").array() {
        let episodeNumber: Int? = {
            if let raw = try? element.selectFirst("._ep-n")?.text().trimmingCharacters(in: .whitespacesAndNewlines),
               let n = Int(raw) {
                return n
            }
            if let label = try? element.attr("data-label"),
               let n = Self.epLabelRegex.firstMatch(in: label)?.firstCaptured.flatMap(Int.init) {
                return n
            }
            return nil
        }()
        guard let episodeNumber else { continue }
            let siteTitle = try element.selectFirst("._ep-t")?.text().trimmingCharacters(in: .whitespacesAndNewlines)
            episodes.append(
                EpisodeInfo(
                    id: "\(showURL)#s\(seasonNumber)e\(episodeNumber)",
                    number: episodeNumber,
                    title: (siteTitle?.isEmpty == false ? siteTitle! : "Episode \(episodeNumber)")
                )
            )
        }
        return episodes
            .reduce(into: [Int: EpisodeInfo]()) { $0[$1.number] = $1 }
            .values
            .sorted { $0.number < $1.number }
    }

    private func parseAccordionEpisodes(showURL: String, seasonNumber: Int, doc: Document) throws -> [EpisodeInfo] {
        var episodes: [EpisodeInfo] = []
        for spoiler in try doc.select("div#se-accordion div.su-spoiler").array() {
            let seasonTitle = try spoiler.selectFirst("div.su-spoiler-title")?.text()
                .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            guard let current = Self.staffelRegex.firstMatch(in: seasonTitle)?.firstCaptured.flatMap(Int.init),
                  current == seasonNumber else { continue }
            let content = try spoiler.selectFirst("div.su-spoiler-content")?.html() ?? ""
            let lines = content.components(separatedBy: "<br>")
            let episodeRegex = try NSRegularExpression(pattern: #"(\d+)x(\d+)\s+Episode\s+\d+"#)
            for line in lines {
                let range = NSRange(line.startIndex..., in: line)
                guard let match = episodeRegex.firstMatch(in: line, options: [], range: range),
                      match.numberOfRanges > 2,
                      let epRange = Range(match.range(at: 2), in: line),
                      let epNumber = Int(line[epRange]) else { continue }
                episodes.append(
                    EpisodeInfo(id: "\(showURL)#s\(seasonNumber)e\(epNumber)", number: epNumber, title: "Episode \(epNumber)")
                )
            }
        }
        return episodes
            .reduce(into: [Int: EpisodeInfo]()) { $0[$1.number] = $1 }
            .values
            .sorted { $0.number < $1.number }
    }

    private func serialSeasonNumber(_ season: Element, serialDoc: Document) throws -> Int? {
        let seasonId = try season.attr("data-season")
        if let tab = try serialDoc.selectFirst("._stab[data-season=\(seasonId)]")?.text(),
           let n = Self.sTabRegex.firstMatch(in: tab)?.firstCaptured.flatMap(Int.init) {
            return n
        }
        if let label = try season.selectFirst("._ep[data-label]")?.attr("data-label"),
           let n = Self.sLabelRegex.firstMatch(in: label)?.firstCaptured.flatMap(Int.init) {
            return n
        }
        return nil
    }

    private func getSerialDocument(_ doc: Document) async -> Document? {
        guard let imdbId = extractImdbId(doc) else { return nil }
        let numeric = imdbId.replacingOccurrences(of: "tt", with: "")
        if let url = URL(string: "https://meinecloud.click/serial/\(numeric)"),
           let html = try? await HTTPClient.getHTML(url: url, referer: baseURL, desktopUA: true, allowLenientTLS: true),
           let parsed = try? SwiftSoup.parse(html, url.absoluteString) {
            return parsed
        }
        if let checkURL = URL(string: "https://meinecloud.click/serials.php?task=check&id_imdb=\(imdbId)"),
           let data = try? await HTTPClient.getJSON(url: checkURL, headers: ["User-Agent": HTTPClient.desktopUserAgent]),
           let text = String(data: data, encoding: .utf8),
           let player = Self.playerURLRegex.firstMatch(in: text)?.firstCaptured?
            .replacingOccurrences(of: "\\/", with: "/"),
           let playerURL = URL(string: player),
           let html = try? await HTTPClient.getHTML(url: playerURL, referer: baseURL, desktopUA: true, allowLenientTLS: true) {
            return try? SwiftSoup.parse(html, playerURL.absoluteString)
        }
        return nil
    }

    private func extractImdbId(_ doc: Document) -> String? {
        let scripts = (try? doc.select("script").array().map { try $0.data() }.joined(separator: "\n")) ?? ""
        return Self.imdbVarRegex.firstMatch(in: scripts)?.firstCaptured
    }

    // MARK: - Streams

    private func movieStreams(from doc: Document) async throws -> [StreamSource] {
        let iframeCandidates = try doc.select(
            "iframe[src*=meinecloud.click], iframe[src*=meinecloud], iframe[data-src*=meinecloud], " +
            "iframe[src*=devideosrc], iframe[data-src*=devideosrc], " +
            "iframe[src*=cloud], iframe.player-iframe, #player iframe, .player iframe, " +
            "iframe[src], iframe[data-src]"
        ).array().compactMap { el -> String? in
            let src = try el.attr("src").ifBlank(try el.attr("data-src"))
            return src.isEmpty ? nil : src
        }
        let uniqueIframes = Array(NSOrderedSet(array: iframeCandidates)) as? [String] ?? iframeCandidates

        guard !uniqueIframes.isEmpty else {
            throw ProviderError.parseFailed("Embed iframe not found")
        }

        var mirrors: [String: StreamSource] = [:]
        var lastEmbed: URL?
        for iframeSrc in uniqueIframes {
            if iframeSrc.localizedCaseInsensitiveContains("youtube.com")
                || iframeSrc.localizedCaseInsensitiveContains("youtu.be") {
                continue
            }
            guard let embedURL = HTTPClient.absoluteURL(iframeSrc, base: baseURL) else { continue }
            lastEmbed = embedURL
            guard let embedHTML = try? await HTTPClient.getHTML(url: embedURL, referer: baseURL, desktopUA: true, allowLenientTLS: true),
                  let embedDoc = try? SwiftSoup.parse(embedHTML, embedURL.absoluteString) else { continue }

            for li in try embedDoc.select(
                "ul._player-mirrors li[data-link], ul._source_list li[data-link], li[data-link], .mirror-list li[data-link], a[data-link]"
            ).array() {
                if li.hasClass("fullhd") { continue }
                let text = try li.text()
                if text.localizedCaseInsensitiveContains("4K Server") { continue }
                let rawLink = try li.attr("data-link").trimmingCharacters(in: .whitespacesAndNewlines)
                    .ifBlank(try li.attr("href").trimmingCharacters(in: .whitespacesAndNewlines))
                guard !rawLink.isEmpty, let decoded = decodeEmbedDataLink(rawLink),
                      let normalized = HTTPClient.absoluteURL(decoded, base: embedURL) else { continue }
                let nameText = li.ownText().ifBlank(try li.text()).trimmingCharacters(in: .whitespacesAndNewlines)
                let name = nameText.isEmpty ? hostName(normalized) : nameText
                if mirrors[normalized.absoluteString] == nil {
                    mirrors[normalized.absoluteString] = StreamSource(
                        id: "hdf-\(mirrors.count)",
                        name: name,
                        url: normalized,
                        headers: defaultHeaders,
                        resolveKind: .followRedirect
                    )
                }
            }
            if !mirrors.isEmpty { break }
        }

        if !mirrors.isEmpty { return Array(mirrors.values) }
        if let fallback = lastEmbed ?? HTTPClient.absoluteURL(uniqueIframes[0], base: baseURL) {
            return [
                StreamSource(
                    id: "hdf-embed",
                    name: "Embed",
                    url: fallback,
                    headers: defaultHeaders,
                    resolveKind: .followRedirect
                ),
            ]
        }
        throw ProviderError.parseFailed("Keine Streams gefunden")
    }

    private func episodeStreams(episodeId: String) async throws -> [StreamSource] {
        let showURLString = episodeId.split(separator: "#").first.map(String.init) ?? episodeId
        let marker = episodeId.split(separator: "#").last.map(String.init) ?? ""
        let seasonNum = Int(marker.dropFirst().prefix(while: { $0.isNumber })) ?? 0
        let epNum = Int(marker.split(separator: "e").last.map(String.init) ?? "") ?? 0
        guard seasonNum > 0, epNum > 0,
              let pageURL = HTTPClient.absoluteURL(showURLString, base: baseURL) else {
            throw ProviderError.invalidURL
        }
        let html = try await HTTPClient.getHTML(url: pageURL, referer: baseURL, desktopUA: true, allowLenientTLS: true)
        let doc = try SwiftSoup.parse(html, baseURL.absoluteString)

        if let serialDoc = await getSerialDocument(doc),
           let season = try serialDoc.select("._season-eps").array().first(where: {
               (try? serialSeasonNumber($0, serialDoc: serialDoc)) == seasonNum
           }),
           let episode = try season.select("._ep").array().first(where: { el in
               let n = (try? el.selectFirst("._ep-n")?.text().trimmingCharacters(in: .whitespacesAndNewlines))
                   .flatMap(Int.init)
               return n == epNum
           }) {
            let raw = try episode.attr("data-link").trimmingCharacters(in: .whitespacesAndNewlines)
            if let decoded = decodeEmbedDataLink(raw) ?? (raw.isEmpty ? nil : raw),
               let url = HTTPClient.absoluteURL(decoded, base: baseURL) {
                return [
                    StreamSource(
                        id: "hdf-ep",
                        name: hostName(url),
                        url: url,
                        headers: defaultHeaders,
                        resolveKind: .followRedirect
                    ),
                ]
            }
        }

        var sources: [StreamSource] = []
        for spoiler in try doc.select("div#se-accordion div.su-spoiler").array() {
            let seasonTitle = try spoiler.selectFirst("div.su-spoiler-title")?.text()
                .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            guard let current = Self.staffelRegex.firstMatch(in: seasonTitle)?.firstCaptured.flatMap(Int.init),
                  current == seasonNum else { continue }
            guard let content = try spoiler.selectFirst("div.su-spoiler-content") else { continue }
            let htmlContent = try content.html()
            let episodeRegex = try NSRegularExpression(pattern: "\(seasonNum)x\(epNum)\\s+Episode\\s+\\d+")
            for line in htmlContent.components(separatedBy: "<br>") {
                let range = NSRange(line.startIndex..., in: line)
                guard episodeRegex.firstMatch(in: line, options: [], range: range) != nil else { continue }
                let fragment = try SwiftSoup.parseBodyFragment(line)
                for link in try fragment.select("a[href]").array() {
                    let serverName = try link.text().trimmingCharacters(in: .whitespacesAndNewlines)
                    let serverURL = try link.attr("href").trimmingCharacters(in: .whitespacesAndNewlines)
                    if serverURL.contains("/engine/player.php") { continue }
                    if serverName.localizedCaseInsensitiveContains("Player HD") { continue }
                    if serverName.localizedCaseInsensitiveContains("4K") { continue }
                    guard !serverURL.isEmpty, let url = HTTPClient.absoluteURL(serverURL, base: baseURL) else { continue }
                    sources.append(
                        StreamSource(
                            id: "hdf-ep-\(sources.count)",
                            name: serverName.ifBlank("Server"),
                            url: url,
                            headers: defaultHeaders,
                            resolveKind: .followRedirect
                        )
                    )
                }
            }
        }
        return sources.uniqued(by: \.url)
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

    private func hostName(_ url: URL) -> String {
        let host = (url.host() ?? "").replacingOccurrences(of: "www.", with: "")
        let first = host.split(separator: ".").first.map(String.init) ?? "Server"
        return first.prefix(1).uppercased() + first.dropFirst()
    }

    private var defaultHeaders: [String: String] {
        [
            "User-Agent": HTTPClient.desktopUserAgent,
            "Referer": baseURL.absoluteString,
        ]
    }

    private static let staffelRegex = try! NSRegularExpression(pattern: #"Staffel\s+(\d+)"#, options: [.caseInsensitive])
    private static let sTabRegex = try! NSRegularExpression(pattern: #"S(\d+)"#, options: [.caseInsensitive])
    private static let sLabelRegex = try! NSRegularExpression(pattern: #"S(\d+)\s*E\d+"#, options: [.caseInsensitive])
    private static let epLabelRegex = try! NSRegularExpression(pattern: #"S\d+\s*E(\d+)"#, options: [.caseInsensitive])
    private static let imdbVarRegex = try! NSRegularExpression(pattern: #"var\s+imdb\s*=\s*['"](tt\d+)['"]"#)
    private static let playerURLRegex = try! NSRegularExpression(pattern: #""player_url"\s*:\s*"([^"]+)""#)
}

private struct SitemapEntry {
    let url: String
    let searchableSlug: String
}

private final class SitemapBox: @unchecked Sendable {
    var entries: [SitemapEntry]?
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
