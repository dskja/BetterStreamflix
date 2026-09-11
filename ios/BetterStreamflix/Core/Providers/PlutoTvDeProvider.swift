import Foundation

/// Port of Android `PlutoTvDeProvider` — IPTV/M3U playlist grouped by `group-title`.
/// Streams are direct M3U8/HTTP URLs encoded into Base64 channel IDs (Android `M3uChannelIdCodec`).
struct PlutoTvDeProvider: CatalogProvider {
    let id = "plutotv-de"
    let name = "Pluto TV De"
    let language = "de"
    let baseURL = URL(string: "https://raw.githubusercontent.com/")!

    private static let playlistURL = URL(
        string: "https://raw.githubusercontent.com/BuddyChewChew/app-m3u-generator/main/playlists/plutotv_de.m3u"
    )!
    private static let cacheDuration: TimeInterval = 30 * 60
    private static let cacheBox = ChannelCacheBox()

    func home() async throws -> [CategoryRow] {
        let channels = try await allChannels()
        var rows: [CategoryRow] = []

        let grouped = Dictionary(grouping: channels.filter { !($0.group?.isEmpty ?? true) }) { $0.group! }
        for groupName in grouped.keys.sorted() {
            let list = (grouped[groupName] ?? [])
                .uniqued(by: \.name)
                .prefix(25)
                .map { $0.asMediaItem(providerID: id) }
            if !list.isEmpty {
                rows.append(CategoryRow(id: groupName, title: groupName, items: Array(list)))
            }
        }

        let ungrouped = channels.filter { $0.group?.isEmpty ?? true }.uniqued(by: \.name).prefix(25)
        if !ungrouped.isEmpty {
            rows.append(
                CategoryRow(
                    id: "general",
                    title: "General",
                    items: ungrouped.map { $0.asMediaItem(providerID: id) }
                )
            )
        }

        rows.append(
            CategoryRow(
                id: "support",
                title: "Soporte y Ayuda",
                items: [
                    infoItem(id: "creador-info"),
                    infoItem(id: "apoyo-nando"),
                ]
            )
        )
        return rows
    }

    func search(query: String) async throws -> [MediaItem] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return [] }
        let channels = try await allChannels()
        return channels
            .filter {
                $0.name.localizedCaseInsensitiveContains(trimmed)
                    || ($0.group?.localizedCaseInsensitiveContains(trimmed) ?? false)
            }
            .uniqued(by: \.name)
            .prefix(80)
            .map { $0.asMediaItem(providerID: id) }
    }

    func detail(id: String, kind: MediaItem.Kind) async throws -> ShowDetail {
        if id == "creador-info" || id == "apoyo-nando" {
            let item = infoItem(id: id)
            return ShowDetail(
                id: id,
                title: item.title,
                overview: infoOverview(id: id),
                posterURL: item.posterURL,
                bannerURL: item.bannerURL,
                seasons: [],
                kind: .tvShow
            )
        }
        let payload = M3uChannelIdCodec.decode(id)
        let logo = payload.logo.isEmpty ? nil : URL(string: payload.logo)
        return ShowDetail(
            id: id,
            title: payload.name,
            overview: "LIVE · Pluto TV\n\(payload.name)\n\nLive channel from the Pluto TV DE playlist.",
            posterURL: logo,
            bannerURL: logo,
            seasons: [],
            kind: .tvShow
        )
    }

    func episodes(showId: String, seasonId: String) async throws -> [EpisodeInfo] {
        if showId == "creador-info" || showId == "apoyo-nando" { return [] }
        return [EpisodeInfo(id: showId, number: 1, title: "Live stream")]
    }

    func streams(showId: String, seasonId: String?, episodeId: String?, detail: ShowDetail?) async throws -> [StreamSource] {
        let target = episodeId ?? showId
        if target == "creador-info" || target == "apoyo-nando" {
            throw ProviderError.unsupported
        }
        let payload = M3uChannelIdCodec.decode(target)
        guard let url = URL(string: payload.url), !payload.url.isEmpty else {
            throw ProviderError.parseFailed("Pluto TV: invalid stream URL")
        }
        var headers = M3uChannelIdCodec.playbackHeaders(target)
        if headers["User-Agent"] == nil {
            headers["User-Agent"] = HTTPClient.desktopUserAgent
        }
        return [
            StreamSource(
                id: "pluto-direct",
                name: "Stream Directo",
                url: url,
                headers: headers,
                resolveKind: .direct
            ),
        ]
    }

    // MARK: - M3U

    private func allChannels() async throws -> [M3UChannel] {
        if let cached = Self.cacheBox.snapshot(),
           Date().timeIntervalSince(cached.fetchedAt) < Self.cacheDuration {
            return cached.channels
        }
        let data = try await HTTPClient.getJSON(
            url: Self.playlistURL,
            headers: [
                "User-Agent": HTTPClient.desktopUserAgent,
                "Accept": "*/*",
            ]
        )
        guard let body = String(data: data, encoding: .utf8), !body.isEmpty else {
            if let cached = Self.cacheBox.snapshot() { return cached.channels }
            throw ProviderError.emptyResponse
        }
        let channels = parseM3U(body)
        Self.cacheBox.store(channels)
        return channels
    }

    private func parseM3U(_ raw: String) -> [M3UChannel] {
        var channels: [M3UChannel] = []
        var curName = ""
        var curLogo = ""
        var curGroup = ""
        var curUA: String?
        var curRef: String?
        var curOrigin: String?

        for line in raw.split(whereSeparator: \.isNewline).map(String.init) {
            let t = line.trimmingCharacters(in: .whitespacesAndNewlines)
            if t.hasPrefix("#EXTINF") {
                curName = t.split(separator: ",").last.map(String.init)?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                curLogo = attr(t, key: "tvg-logo") ?? ""
                curGroup = attr(t, key: "group-title") ?? ""
                curUA = attr(t, key: "http-user-agent")
                curRef = attr(t, key: "http-referrer")
                curOrigin = attr(t, key: "http-origin")
            } else if t.hasPrefix("#EXTVLCOPT:") {
                if t.contains("http-user-agent=") {
                    curUA = t.components(separatedBy: "http-user-agent=").last?.trimmingCharacters(in: .whitespacesAndNewlines)
                } else if t.contains("http-referrer=") {
                    curRef = t.components(separatedBy: "http-referrer=").last?.trimmingCharacters(in: .whitespacesAndNewlines)
                } else if t.contains("http-origin=") {
                    curOrigin = t.components(separatedBy: "http-origin=").last?.trimmingCharacters(in: .whitespacesAndNewlines)
                }
            } else if t.hasPrefix("http"), !curName.isEmpty {
                channels.append(
                    M3UChannel(
                        name: curName,
                        url: t,
                        logo: curLogo.isEmpty ? nil : curLogo,
                        group: curGroup.isEmpty ? nil : curGroup,
                        userAgent: curUA,
                        referrer: curRef,
                        origin: curOrigin
                    )
                )
                curName = ""; curLogo = ""; curGroup = ""
                curUA = nil; curRef = nil; curOrigin = nil
            }
        }
        return channels
    }

    private func attr(_ line: String, key: String) -> String? {
        let pattern = #"\#(key)="([^"]+)""#
        guard let regex = try? NSRegularExpression(pattern: pattern),
              let match = regex.firstMatch(in: line, range: NSRange(line.startIndex..., in: line)),
              match.numberOfRanges > 1,
              let range = Range(match.range(at: 1), in: line) else { return nil }
        return String(line[range])
    }

    private func infoItem(id: String) -> MediaItem {
        let isReport = id == "creador-info"
        return MediaItem(
            id: id,
            title: isReport ? "Reportar problemas" : "Apoya al Proveedor",
            posterURL: URL(string: isReport
                ? "https://i.ibb.co/dsknGBHT/Imagen-de-Whats-App-2025-09-06-a-las-19-00-50-e8e5bcaa.jpg"
                : "https://i.ibb.co/B5gKLkqS/nuevo-formato-2-K-202604112205.jpg"),
            bannerURL: URL(string: isReport
                ? "https://i.ibb.co/dsknGBHT/Imagen-de-Whats-App-2025-09-06-a-las-19-00-50-e8e5bcaa.jpg"
                : "https://i.ibb.co/B5gKLkqS/nuevo-formato-2-K-202604112205.jpg"),
            kind: .tvShow,
            providerHint: self.id
        )
    }

    private func infoOverview(id: String) -> String {
        if id == "creador-info" {
            return "Si algún canal no funciona o encuentras errores en el proveedor, por favor repórtalo en nuestro grupo oficial de Telegram."
        }
        return "Si te gusta nuestro contenido y quieres ayudarnos a mantener los servidores activos, puedes realizar una donación voluntaria. ¡Gracias por tu apoyo!"
    }
}

// MARK: - M3U helpers

private struct M3UChannel {
    let name: String
    let url: String
    let logo: String?
    let group: String?
    let userAgent: String?
    let referrer: String?
    let origin: String?

    func asMediaItem(providerID: String) -> MediaItem {
        let id = M3uChannelIdCodec.encode(
            url: url,
            name: name,
            logo: logo,
            userAgent: userAgent,
            referrer: referrer,
            origin: origin
        )
        return MediaItem(
            id: id,
            title: name,
            posterURL: logo.flatMap(URL.init(string:)),
            bannerURL: logo.flatMap(URL.init(string:)),
            kind: .tvShow,
            providerHint: providerID,
            isLive: true
        )
    }
}

/// Mirrors Android `M3uChannelIdCodec` (unit separator fields + Base64).
private enum M3uChannelIdCodec {
    private static let sep = "\u{001F}"

    struct Payload {
        var url: String
        var name: String
        var logo: String = ""
        var userAgent: String?
        var referrer: String?
        var origin: String?
    }

    static func encode(
        url: String,
        name: String,
        logo: String?,
        userAgent: String?,
        referrer: String?,
        origin: String?
    ) -> String {
        let raw = [url, name, logo ?? "", userAgent ?? "", referrer ?? "", origin ?? ""].joined(separator: sep)
        return Data(raw.utf8).base64EncodedString()
    }

    static func decode(_ id: String) -> Payload {
        guard !id.isEmpty,
              let data = Data(base64Encoded: id),
              let raw = String(data: data, encoding: .utf8) else {
            return Payload(url: id, name: "Unknown")
        }
        if raw.contains(sep) {
            let parts = raw.components(separatedBy: sep)
            return Payload(
                url: parts[safe: 0]?.nilIfBlank ?? id,
                name: parts[safe: 1]?.nilIfBlank ?? "Unknown",
                logo: parts[safe: 2] ?? "",
                userAgent: parts[safe: 3]?.nilIfBlank,
                referrer: parts[safe: 4]?.nilIfBlank,
                origin: parts[safe: 5]?.nilIfBlank
            )
        }
        // Legacy pipe format (best-effort)
        let parts = raw.split(separator: "|", omittingEmptySubsequences: false).map(String.init)
        return Payload(
            url: parts.first?.nilIfBlank ?? id,
            name: parts.count > 1 ? parts[1] : "Unknown",
            logo: parts.count > 2 ? parts[2] : ""
        )
    }

    static func playbackHeaders(_ id: String) -> [String: String] {
        let payload = decode(id)
        var headers: [String: String] = [:]
        if let ua = payload.userAgent { headers["User-Agent"] = ua }
        if let ref = payload.referrer { headers["Referer"] = ref }
        if let origin = payload.origin { headers["Origin"] = origin }
        return headers
    }
}

private final class ChannelCacheBox: @unchecked Sendable {
    struct Snapshot {
        let channels: [M3UChannel]
        let fetchedAt: Date
    }

    private let lock = NSLock()
    private var snapshotValue: Snapshot?

    func snapshot() -> Snapshot? {
        lock.lock()
        defer { lock.unlock() }
        return snapshotValue
    }

    func store(_ channels: [M3UChannel]) {
        lock.lock()
        snapshotValue = Snapshot(channels: channels, fetchedAt: Date())
        lock.unlock()
    }
}

private extension Array {
    func uniqued<T: Hashable>(by keyPath: KeyPath<Element, T>) -> [Element] {
        var seen = Set<T>()
        return filter { seen.insert($0[keyPath: keyPath]).inserted }
    }
}

private extension Array where Element == String {
    subscript(safe index: Int) -> String? {
        indices.contains(index) ? self[index] : nil
    }
}

private extension String {
    var nilIfBlank: String? {
        let t = trimmingCharacters(in: .whitespacesAndNewlines)
        return t.isEmpty ? nil : t
    }
}
