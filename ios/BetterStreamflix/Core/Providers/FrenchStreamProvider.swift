import Foundation
import SwiftSoup

/// Port of Android `FrenchStreamProvider` — solid home/search/detail/streams subset (VFV1 layout).
/// Domain mirrors change often; tries portal `fstream.info` then falls back to `fs23.lol`.
struct FrenchStreamProvider: CatalogProvider {
    let id = "frenchstream"
    let name = "FrenchStream"
    let language = "fr"
    private let portalURL = URL(string: "https://fstream.info/")!
    private let defaultMirror = URL(string: "https://fs23.lol/")!

    var baseURL: URL { Self.baseBox.url ?? defaultMirror }

    private static let baseBox = FrenchStreamBaseBox()

    func home() async throws -> [CategoryRow] {
        let base = try await ensureBase()
        let html = try await fetchPage(url: base, cookie: "dle_skin=VFV1; fsschal=1")
        let doc = try SwiftSoup.parse(html, base.absoluteString)
        var rows: [CategoryRow] = []

        let pages = try doc.select("div.pages.clearfix").array()
        let labels = ["Nouveautés Films", "Nouveautés Séries", "Ajouts de la Commu", "BOX OFFICE"]
        for (idx, page) in pages.prefix(4).enumerated() {
            let kind: MediaItem.Kind = idx == 1 ? .tvShow : .movie
            let items = try parseShorts(page.select("div.short").array(), kind: kind, base: base)
            if !items.isEmpty {
                rows.append(CategoryRow(id: "page-\(idx)", title: labels[idx], items: items))
            }
        }
        return rows
    }

    func search(query: String) async throws -> [MediaItem] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return [] }
        let base = try await ensureBase()
        guard let url = HTTPClient.apiURL(base: base, path: "engine/ajax/search.php") else {
            throw ProviderError.invalidURL
        }
        let data = try await HTTPClient.postForm(
            url: url,
            fields: ["query": trimmed, "page": "1"],
            headers: [
                "User-Agent": HTTPClient.desktopUserAgent,
                "Referer": base.absoluteString,
                "Cookie": "dle_skin=VFV1; fsschal=1",
            ]
        )
        guard let html = String(data: data, encoding: .utf8) else { return [] }
        let doc = try SwiftSoup.parse(html, base.absoluteString)
        return try doc.select("div.search-item").array().compactMap { el -> MediaItem? in
            let onclick = try el.attr("onclick")
            // Android: substringAfter("/").substringBefore("'") then used as id; prefer leaf slug.
            let pathPart: String = {
                if let q1 = onclick.range(of: "'"),
                   let after = onclick[q1.upperBound...].split(separator: "'").first {
                    return String(after)
                }
                return ""
            }()
            let resolvedID = pathPart.split(separator: "/").last.map(String.init) ?? ""
            guard !resolvedID.isEmpty else { return nil }
            let title = try el.selectFirst("div.search-title")?.text()
                .replacingOccurrences(of: "\\'", with: "'")
                .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            guard !title.isEmpty else { return nil }
            let poster = HTTPClient.absoluteURL(try el.selectFirst("img")?.attr("src"), base: base)
            let kind: MediaItem.Kind =
                pathPart.contains("-saison-") || pathPart.contains("s-tv/") || title.contains(" - Saison ")
                ? .tvShow : .movie
            return MediaItem(
                id: resolvedID,
                title: title,
                posterURL: poster,
                kind: kind,
                providerHint: self.id
            )
        }
    }

    func detail(id: String, kind: MediaItem.Kind) async throws -> ShowDetail {
        let base = try await ensureBase()
        guard let pageURL = itemURL(id: id, base: base) else { throw ProviderError.invalidURL }
        let cookie = kind == .tvShow ? "dle_skin=VFV25; fsschal=1" : "dle_skin=VFV1; fsschal=1"
        let html = try await fetchPage(url: pageURL, cookie: cookie)
        let doc = try SwiftSoup.parse(html, base.absoluteString)

        let titleRaw = try doc.selectFirst("meta[property=og:title]")?.attr("content")
            .trimmingCharacters(in: .whitespacesAndNewlines)
            ?? doc.selectFirst("h1")?.text().trimmingCharacters(in: .whitespacesAndNewlines)
            ?? id
        let title = titleRaw.components(separatedBy: " - Saison").first?
            .trimmingCharacters(in: .whitespacesAndNewlines) ?? titleRaw

        let overview = try (
            doc.selectFirst("div#s-desc")?.text()
                ?? doc.selectFirst("div.fdesc > p")?.text()
        )?.trimmingCharacters(in: .whitespacesAndNewlines)

        let newsID = id.components(separatedBy: "newsid=").last ?? id
        let filmMeta = try? await fetchFilmData(itemID: newsID, base: base)
        let posterFromMeta = HTTPClient.absoluteURL(filmMeta?.affiche, base: base)
        let posterFromOg = HTTPClient.absoluteURL(
            try doc.selectFirst("meta[property=og:image]")?.attr("content"),
            base: base
        )
        let poster = posterFromMeta ?? posterFromOg
        let banner = HTTPClient.absoluteURL(filmMeta?.affiche2, base: base) ?? poster
        let year = try doc.selectFirst("span.release_date, span.release")?.text()
            .components(separatedBy: CharacterSet.decimalDigits.inverted)
            .first(where: { $0.count == 4 })
        let genres = try doc.select("span.genres a").array().compactMap {
            let t = try $0.text().trimmingCharacters(in: .whitespacesAndNewlines)
            return t.isEmpty ? nil : t
        }

        let isSeries = kind == .tvShow || id.contains("-saison-") || id.contains("s-tv/") || titleRaw.contains("Saison")
        if isSeries {
            var seasons: [SeasonInfo] = []
            if let tagz = filmMeta?.tagz, !tagz.isEmpty,
               let seasonList = try? await fetchSeasons(tagz: tagz, base: base) {
                seasons = seasonList
            }
            if seasons.isEmpty {
                let seasonNumber = Int(titleRaw.components(separatedBy: "Saison ").last?
                    .trimmingCharacters(in: .whitespacesAndNewlines) ?? "") ?? 1
                seasons = [SeasonInfo(id: newsID, number: seasonNumber, title: "Saison \(seasonNumber)")]
            }
            return ShowDetail(
                id: id,
                title: title,
                overview: overview?.isEmpty == true ? nil : overview,
                posterURL: poster,
                bannerURL: banner,
                year: year,
                seasons: seasons,
                kind: .tvShow,
                genres: genres
            )
        }

        return ShowDetail(
            id: id,
            title: title,
            overview: overview?.isEmpty == true ? nil : overview,
            posterURL: poster,
            bannerURL: banner,
            year: year,
            seasons: [],
            kind: .movie,
            genres: genres
        )
    }

    func episodes(showId: String, seasonId: String) async throws -> [EpisodeInfo] {
        let base = try await ensureBase()
        let seasonKey = seasonId.contains("/") ? (seasonId.split(separator: "/").last.map(String.init) ?? seasonId) : seasonId
        let paths = ["engine/ajax/sx.php", "engine/ajax/sx.php"]
        var root: [String: Any] = [:]
        for path in ["engine/ajax/sx.php"] {
            guard let url = HTTPClient.apiURL(
                base: base,
                path: path,
                query: [URLQueryItem(name: "id", value: seasonKey)]
            ) else { continue }
            if let data = try? await HTTPClient.getJSON(
                url: url,
                headers: [
                    "User-Agent": HTTPClient.desktopUserAgent,
                    "Cookie": "dle_skin=VFV1",
                    "X-Requested-With": "XMLHttpRequest",
                    "Referer": base.absoluteString,
                    "Accept": "application/json",
                ]
            ),
               let parsed = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
               !parsed.isEmpty {
                root = parsed
                break
            }
        }
        if root.isEmpty {
            // Fallback: scrape episode links from the season/show page.
            return try await scrapeEpisodesFromPage(showId: showId, seasonId: seasonKey, base: base)
        }
        let info = root["info"] as? [String: [String: Any]] ?? [:]
        let vf = root["vf"] as? [String: Any] ?? [:]
        let vostfr = root["vostfr"] as? [String: Any] ?? [:]
        let vo = root["vo"] as? [String: Any] ?? [:]

        var episodes: [EpisodeInfo] = []
        var number = 1
        while vf["\(number)"] != nil || vostfr["\(number)"] != nil || vo["\(number)"] != nil {
            let meta = info["\(number)"]
            let title = (meta?["title"] as? String)?.replacingOccurrences(of: "\\'", with: "'")
                ?? "Episode \(number)"
            let overview = (meta?["synopsis"] as? String)?.replacingOccurrences(of: "\\'", with: "'")
            let poster = HTTPClient.absoluteURL(meta?["poster"] as? String, base: base)
            episodes.append(
                EpisodeInfo(
                    id: "\(seasonId)/\(number)",
                    number: number,
                    title: title,
                    overview: overview,
                    thumbnailURL: poster
                )
            )
            number += 1
            if number > 500 { break }
        }
        return episodes
    }


    private func scrapeEpisodesFromPage(showId: String, seasonId: String, base: URL) async throws -> [EpisodeInfo] {
        guard let pageURL = itemURL(id: showId, base: base) else { return [] }
        let html = try await fetchPage(url: pageURL, cookie: "dle_skin=VFV25; fsschal=1")
        let doc = try SwiftSoup.parse(html, base.absoluteString)
        var episodes: [EpisodeInfo] = []
        var seen = Set<Int>()
        for (idx, el) in try doc.select("div.fs-episode, a.fs-episode, .episode-list a, select.episodes option").array().enumerated() {
            let num = Int(try el.attr("data-episode"))
                ?? Int(try el.attr("value"))
                ?? (idx + 1)
            guard seen.insert(num).inserted else { continue }
            let title = try el.text().trimmingCharacters(in: .whitespacesAndNewlines)
            episodes.append(
                EpisodeInfo(
                    id: "\(seasonId)/\(num)",
                    number: num,
                    title: title.isEmpty ? "Episode \(num)" : title
                )
            )
        }
        return episodes
    }

    func streams(showId: String, seasonId: String?, episodeId: String?, detail: ShowDetail?) async throws -> [StreamSource] {
        let base = try await ensureBase()
        if let episodeId, episodeId.contains("/") {
            return try await episodeStreams(episodeId: episodeId, base: base)
        }
        return try await movieStreams(showId: showId, base: base)
    }

    // MARK: - Streams

    private func movieStreams(showId: String, base: URL) async throws -> [StreamSource] {
        let itemID = showId.components(separatedBy: "newsid=").last ?? showId
        guard let film = try? await fetchFilmData(itemID: itemID, base: base),
              let players = film.players else {
            throw ProviderError.parseFailed("FrenchStream: no film players")
        }
        let labels = ["vff": "TrueFrench", "vfq": "French", "vostfr": "VOSTFR", "vo": "VO"]
        let langOrder = ["vff", "vfq", "vostfr", "vo"]
        var sources: [StreamSource] = []
        var idx = 0
        for (provider, langMap) in players {
            var seen = Set<String>()
            let ordered = langMap.keys.sorted {
                let ai = langOrder.firstIndex(of: $0) ?? Int.max
                let bi = langOrder.firstIndex(of: $1) ?? Int.max
                return ai < bi
            }
            for lang in ordered where lang != "default" {
                guard let urlString = langMap[lang], !urlString.isEmpty,
                      seen.insert(urlString).inserted,
                      !ignoreSource(provider: provider, href: urlString),
                      let url = URL(string: urlString) else { continue }
                let langLabel = labels[lang] ?? lang
                sources.append(
                    StreamSource(
                        id: "fs-vid-\(idx)",
                        name: "\(provider.capitalized) (\(langLabel))",
                        url: url,
                        headers: defaultHeaders(base: base),
                        resolveKind: .followRedirect
                    )
                )
                idx += 1
            }
            if let def = langMap["default"], !def.isEmpty, seen.insert(def).inserted,
               !ignoreSource(provider: provider, href: def),
               let url = URL(string: def) {
                sources.append(
                    StreamSource(
                        id: "fs-vid-\(idx)",
                        name: provider.capitalized,
                        url: url,
                        headers: defaultHeaders(base: base),
                        resolveKind: .followRedirect
                    )
                )
                idx += 1
            }
        }
        if sources.isEmpty {
            throw ProviderError.parseFailed("FrenchStream: empty movie streams")
        }
        return sources
    }

    private func episodeStreams(episodeId: String, base: URL) async throws -> [StreamSource] {
        let parts = episodeId.split(separator: "/")
        guard parts.count >= 2 else { throw ProviderError.invalidURL }
        let seasonKey = String(parts[0])
        let epNumber = String(parts[1])
        guard let url = HTTPClient.apiURL(
            base: base,
            path: "engine/ajax/sx.php",
            query: [URLQueryItem(name: "id", value: seasonKey)]
        ) else {
            throw ProviderError.invalidURL
        }
        let data = try await HTTPClient.getJSON(
            url: url,
            headers: [
                "User-Agent": HTTPClient.desktopUserAgent,
                "Cookie": "dle_skin=VFV1",
                "X-Requested-With": "XMLHttpRequest",
                "Accept": "application/json",
            ]
        )
        let root = (try? JSONSerialization.jsonObject(with: data) as? [String: Any]) ?? [:]
        var sources: [StreamSource] = []
        let bundles: [(String, String)] = [("vf", "VF"), ("vostfr", "VOSTFR"), ("vo", "VO")]
        for (key, label) in bundles {
            guard let map = root[key] as? [String: [String: String]],
                  let providers = map[epNumber] else { continue }
            for (provider, urlString) in providers {
                guard !urlString.isEmpty, let streamURL = URL(string: urlString) else { continue }
                sources.append(
                    StreamSource(
                        id: "fs-\(key)-\(provider)",
                        name: "\(provider.capitalized) (\(label))",
                        url: streamURL,
                        headers: defaultHeaders(base: base),
                        resolveKind: .followRedirect
                    )
                )
            }
        }
        if sources.isEmpty {
            throw ProviderError.parseFailed("FrenchStream: no episode streams")
        }
        return sources
    }

    // MARK: - Helpers

    private func ensureBase() async throws -> URL {
        if let cached = Self.baseBox.url { return cached }
        if let mirror = try? await resolveMirror() {
            Self.baseBox.url = mirror
            return mirror
        }
        Self.baseBox.url = defaultMirror
        return defaultMirror
    }

    private func resolveMirror() async throws -> URL? {
        let html = try await HTTPClient.getHTML(url: portalURL, desktopUA: true)
        if let match = html.range(of: #"FS_MIRROR\s*=\s*["']([^"']+)["']"#, options: .regularExpression) {
            let snippet = String(html[match])
            if let urlMatch = snippet.range(of: #"https?://[^"']+"#, options: .regularExpression) {
                let raw = String(snippet[urlMatch])
                if let url = normalizeHTTP(raw) { return url }
            }
        }
        let doc = try SwiftSoup.parse(html)
        for a in try doc.select("div.container > div.url-card a[href], a#mainUrl[href], a.url-display[href]").array() {
            if let url = normalizeHTTP(try a.attr("href")) { return url }
        }
        return nil
    }

    private func normalizeHTTP(_ raw: String?) -> URL? {
        guard var value = raw?.trimmingCharacters(in: .whitespacesAndNewlines), !value.isEmpty else { return nil }
        guard value.hasPrefix("http://") || value.hasPrefix("https://") else { return nil }
        if !value.hasSuffix("/") { value += "/" }
        return URL(string: value)
    }

    private func fetchPage(url: URL, cookie: String) async throws -> String {
        if let data = try? await HTTPClient.getJSON(
            url: url,
            headers: [
                "User-Agent": HTTPClient.desktopUserAgent,
                "Accept": "text/html,application/xhtml+xml",
                "Cookie": cookie,
                "Referer": baseURL.absoluteString,
            ]
        ), let html = String(data: data, encoding: .utf8), !html.isEmpty {
            return html
        }
        return try await HTTPClient.getHTML(url: url, referer: baseURL, desktopUA: true)
    }

    private func parseShorts(_ elements: [Element], kind: MediaItem.Kind, base: URL) throws -> [MediaItem] {
        var items: [MediaItem] = []
        var seen = Set<String>()
        for el in elements {
            let href = try el.selectFirst("a.short-poster")?.attr("href")
                .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            let id = href.split(separator: "/").last.map(String.init) ?? ""
            guard !id.isEmpty, seen.insert(id).inserted else { continue }
            var title = try el.selectFirst("div.short-title")?.text()
                .trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
            if kind == .tvShow, let version = try el.selectFirst("span.film-version")?.text(), !version.isEmpty {
                title = [title, version].filter { !$0.isEmpty }.joined(separator: " - ")
            }
            guard !title.isEmpty else { continue }
            let poster = HTTPClient.absoluteURL(try el.selectFirst("img")?.attr("src"), base: base)
            items.append(
                MediaItem(id: id, title: title, posterURL: poster, kind: kind, providerHint: self.id)
            )
        }
        return items
    }

    private func itemURL(id: String, base: URL) -> URL? {
        if id.hasPrefix("http") { return URL(string: id) }
        if id.contains("/") { return HTTPClient.absoluteURL(id, base: base) }
        // Prefer films path; series slug still resolves via absolute if needed.
        return HTTPClient.absoluteURL(id, base: base)
            ?? HTTPClient.apiURL(base: base, path: id)
    }

    private struct FilmMeta {
        var affiche: String?
        var affiche2: String?
        var tagz: String?
        var players: [String: [String: String]]?
    }

    private func fetchFilmData(itemID: String, base: URL) async throws -> FilmMeta {
        guard let url = HTTPClient.apiURL(
            base: base,
            path: "engine/ajax/film_api.php",
            query: [URLQueryItem(name: "id", value: itemID)]
        ) else {
            throw ProviderError.invalidURL
        }
        let data = try await HTTPClient.getJSON(
            url: url,
            headers: [
                "User-Agent": HTTPClient.desktopUserAgent,
                "Cookie": "dle_skin=VFV1",
                "X-Requested-With": "XMLHttpRequest",
                "Accept": "application/json",
            ]
        )
        let root = try JSONSerialization.jsonObject(with: data) as? [String: Any] ?? [:]
        let meta = root["meta"] as? [String: Any]
        let players = root["players"] as? [String: [String: String]]
        return FilmMeta(
            affiche: meta?["affiche"] as? String,
            affiche2: meta?["affiche2"] as? String,
            tagz: meta?["tagz"] as? String,
            players: players
        )
    }

    private func fetchSeasons(tagz: String, base: URL) async throws -> [SeasonInfo] {
        guard let url = HTTPClient.apiURL(base: base, path: "engine/ajax/get_seasons.php") else {
            return []
        }
        let data = try await HTTPClient.postForm(
            url: url,
            fields: ["serie_tag": tagz],
            headers: [
                "User-Agent": HTTPClient.desktopUserAgent,
                "Cookie": "dle_skin=VFV1",
                "X-Requested-With": "XMLHttpRequest",
                "Referer": base.absoluteString,
            ]
        )
        guard let arr = try JSONSerialization.jsonObject(with: data) as? [[String: Any]] else { return [] }
        return arr.enumerated().compactMap { idx, season in
            // Android/API returns numeric ids — casting only as String produced "0","1",… and empty episodes.
            let sid: String = {
                if let s = season["id"] as? String, !s.isEmpty { return s }
                if let n = season["id"] as? Int { return String(n) }
                if let n = season["id"] as? NSNumber { return n.stringValue }
                return tagz // never fall back to bare index
            }()
            let title = (season["title"] as? String) ?? "Saison \(idx + 1)"
            let number = Int(title.components(separatedBy: "Saison ").last?
                .trimmingCharacters(in: .whitespacesAndNewlines) ?? "") ?? (idx + 1)
            let poster = HTTPClient.absoluteURL(season["affiche"] as? String, base: base)
            return SeasonInfo(id: sid, number: number, title: title, posterURL: poster)
        }.sorted { $0.number < $1.number }
    }

    private func ignoreSource(provider: String, href: String) -> Bool {
        provider.trimmingCharacters(in: .whitespacesAndNewlines).caseInsensitiveCompare("Dood.Stream") == .orderedSame
            && href.contains("/bigwar5/")
    }

    private func defaultHeaders(base: URL) -> [String: String] {
        [
            "User-Agent": HTTPClient.desktopUserAgent,
            "Referer": base.absoluteString,
        ]
    }
}

private final class FrenchStreamBaseBox: @unchecked Sendable {
    private let lock = NSLock()
    private var value: URL?

    var url: URL? {
        get {
            lock.lock()
            defer { lock.unlock() }
            return value
        }
        set {
            lock.lock()
            value = newValue
            lock.unlock()
        }
    }
}
