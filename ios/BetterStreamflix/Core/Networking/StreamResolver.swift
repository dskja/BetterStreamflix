import Foundation
import SwiftSoup

enum StreamResolver {
    /// Tries sources in order until one yields a playable URL.
    static func resolveFirst(
        _ sources: [StreamSource],
        excluding excludedIDs: Set<String> = []
    ) async throws -> (source: StreamSource, url: URL) {
        var lastError: Error = ProviderError.emptyResponse
        for source in sources where !excludedIDs.contains(source.id) {
            if source.resolveKind == .serienstreamGate {
                // Gate needs WebView — skip in auto-fallback.
                continue
            }
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
            return source.url
        case .videasy:
            return try await VideasyExtractor.resolve(source.url)
        case .followRedirect:
            let final = try await HTTPClient.followRedirects(url: source.url, headers: source.headers)
            if looksLikeDirectMedia(final) { return final }
            if let html = try? await HTTPClient.getHTML(url: final, desktopUA: true),
               let extracted = extractMediaURL(from: html, base: final) {
                return extracted
            }
            return final
        case .serienstreamGate:
            // Caller should open ChallengeWebView; best-effort follow here.
            let final = try await HTTPClient.followRedirects(url: source.url, headers: source.headers)
            if isSerienStreamHost(final) || final.absoluteString.contains("/r?") {
                throw ProviderError.streamGate(
                    "SerienStream verification required. Complete the challenge, then continue."
                )
            }
            return final
        }
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
