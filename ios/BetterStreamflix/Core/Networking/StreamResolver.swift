import Foundation
import SwiftSoup

enum StreamResolver {
    /// Best-effort: follow hoster redirects and extract a direct media URL when possible.
    static func resolve(_ source: StreamSource) async throws -> URL {
        if looksLikeDirectMedia(source.url) {
            return source.url
        }

        var request = URLRequest(url: source.url)
        request.setValue(HTTPClient.userAgent, forHTTPHeaderField: "User-Agent")
        for (key, value) in source.headers {
            request.setValue(value, forHTTPHeaderField: key)
        }

        let (data, response) = try await URLSession.shared.data(for: request)
        if let final = response.url, looksLikeDirectMedia(final) {
            return final
        }

        guard let html = String(data: data, encoding: .utf8), !html.isEmpty else {
            return source.url
        }

        if let extracted = extractMediaURL(from: html, base: response.url ?? source.url) {
            return extracted
        }
        return source.url
    }

    private static func looksLikeDirectMedia(_ url: URL) -> Bool {
        let path = url.path.lowercased()
        return path.hasSuffix(".m3u8")
            || path.hasSuffix(".mp4")
            || path.hasSuffix(".mkv")
            || path.hasSuffix(".mpd")
            || url.absoluteString.contains(".m3u8")
    }

    private static func extractMediaURL(from html: String, base: URL) -> URL? {
        let patterns = [
            #"https?://[^"'\\\s<>]+\.m3u8[^"'\\\s<>]*"#,
            #"https?://[^"'\\\s<>]+\.mp4[^"'\\\s<>]*"#,
            #"(?<=file["']?\s*[:=]\s*["'])[^"']+\.(?:m3u8|mp4)"#,
            #"(?<=src["']?\s*[:=]\s*["'])[^"']+\.(?:m3u8|mp4)"#,
        ]
        for pattern in patterns {
            if let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive]),
               let match = regex.firstMatch(in: html, range: NSRange(html.startIndex..., in: html)),
               let range = Range(match.range, in: html) {
                let raw = String(html[range])
                    .replacingOccurrences(of: "\\/", with: "/")
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
