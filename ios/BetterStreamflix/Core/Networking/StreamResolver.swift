import Foundation
import SwiftSoup

enum StreamResolver {
    /// Prefer host scrapes, then surface SerienStream gates before burning time on Videasy.
    static func resolveFirst(
        _ sources: [StreamSource],
        excluding excludedIDs: Set<String> = []
    ) async throws -> (source: StreamSource, url: URL) {
        let filtered = sources.filter { !excludedIDs.contains($0.id) }
        var lastError: Error = ProviderError.emptyResponse

        let hostScrapes = filtered.filter {
            switch $0.resolveKind {
            case .direct, .followRedirect: return true
            case .serienstreamGate, .videasy: return false
            }
        }
        let gates = filtered.filter { $0.resolveKind == .serienstreamGate }
        let videasy = filtered.filter { $0.resolveKind == .videasy }

        for source in hostScrapes {
            do {
                let url = try await resolve(source)
                return (source, url)
            } catch {
                lastError = error
            }
        }

        // /r? always needs the in-app challenge WebView — surface it before Videasy timeouts.
        if let gate = gates.first {
            do {
                let url = try await resolve(gate)
                return (gate, url)
            } catch {
                throw ProviderError.streamGate(gate.url.absoluteString)
            }
        }

        for source in videasy {
            do {
                let url = try await resolve(source)
                return (source, url)
            } catch {
                lastError = error
            }
        }

        throw lastError
    }

    static func resolve(_ source: StreamSource) async throws -> URL {
        switch source.resolveKind {
        case .direct:
            if looksLikeDirectMedia(source.url) { return source.url }
            return try await HosterExtractor.extract(from: source.url, headers: source.headers)

        case .videasy:
            return try await VideasyExtractor.resolve(source.url)

        case .followRedirect:
            let final = try await HTTPClient.followRedirects(url: source.url, headers: source.headers)
            if looksLikeDirectMedia(final) { return final }
            if isSerienStreamHost(final) || final.absoluteString.contains("/r?") {
                throw ProviderError.streamGate(source.url.absoluteString)
            }
            return try await HosterExtractor.extract(from: final, headers: source.headers)

        case .serienstreamGate:
            // Turnstile / ALTCHA pages never HTTP-redirect to the hoster.
            if source.url.absoluteString.contains("/r?") {
                throw ProviderError.streamGate(source.url.absoluteString)
            }
            let final = try await HTTPClient.followRedirects(url: source.url, headers: source.headers)
            if isSerienStreamHost(final) || final.absoluteString.contains("/r?") {
                throw ProviderError.streamGate(source.url.absoluteString)
            }
            if looksLikeDirectMedia(final) { return final }
            return try await HosterExtractor.extract(from: final, headers: source.headers)
        }
    }

    /// Resolve a post-challenge hoster URL into playable media.
    static func resolveHoster(_ url: URL, headers: [String: String] = [:]) async throws -> URL {
        if looksLikeDirectMedia(url) { return url }
        return try await HosterExtractor.extract(from: url, headers: headers)
    }

    static func isSerienStreamHost(_ url: URL) -> Bool {
        let host = url.host()?.lowercased() ?? ""
        // Match Android: serienstream.to / .cx (and legacy s.to hoster redirects).
        return host.contains("serienstream")
            || host == "s.to"
            || host.hasSuffix(".s.to")
    }

    static func looksLikeDirectMedia(_ url: URL) -> Bool {
        let path = url.path.lowercased()
        let abs = url.absoluteString.lowercased()
        return path.hasSuffix(".m3u8")
            || path.hasSuffix(".mp4")
            || path.hasSuffix(".mkv")
            || path.hasSuffix(".mpd")
            || abs.contains(".m3u8")
            || abs.contains("get_video")
    }

    static func extractMediaURL(from html: String, base: URL) -> URL? {
        let patterns = [
            #"https?://[^"'\\\s<>]+\.m3u8[^"'\\\s<>]*"#,
            #"https?://[^"'\\\s<>]+\.mp4[^"'\\\s<>]*"#,
        ]
        for pattern in patterns {
            if let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive]),
               let match = regex.firstMatch(in: html, range: NSRange(html.startIndex..., in: html)),
               let range = Range(match.range, in: html) {
                let raw = String(html[range]).replacingOccurrences(of: "\\/", with: "/")
                if let absolute = HTTPClient.absoluteURL(raw, base: base) {
                    return absolute
                }
            }
        }
        if let doc = try? SwiftSoup.parse(html, base.absoluteString),
           let source = try? doc.selectFirst("video source[src], source[src], video[src]") {
            let src = (try? source.attr("src")) ?? ""
            return HTTPClient.absoluteURL(src, base: base)
        }
        return nil
    }
}
