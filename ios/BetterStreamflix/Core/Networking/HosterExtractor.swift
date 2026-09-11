import Foundation
import SwiftSoup

/// Minimal hoster extractors ported from Android so AVPlayer gets real media URLs
/// instead of HTML embed pages (VOE / Streamtape / Dood / Vidoza / MixDrop + generic).
enum HosterExtractor {
    static func extract(from url: URL, headers: [String: String] = [:]) async throws -> URL {
        if StreamResolver.looksLikeDirectMedia(url) { return url }

        let host = (url.host() ?? "").lowercased()
        let absolute = url.absoluteString
        let normalized = normalizeVOEURL(url)

        if isVOE(host) || isVOE((normalized.host() ?? "").lowercased()) {
            return try await extractVOE(url: normalized, headers: headers)
        }
        if isStreamtape(host) {
            return try await extractStreamtape(url: url, headers: headers)
        }
        if isDood(host) {
            return try await extractDood(url: url, headers: headers)
        }
        if isVidoza(host) {
            return try await extractVidoza(url: url, headers: headers)
        }
        if isMixDrop(host) {
            return try await extractMixDrop(url: url, headers: headers)
        }
        if isVixcloud(host) {
            return try await extractVixcloud(url: url, headers: headers)
        }

        // Generic: follow redirects then scrape m3u8/mp4 from HTML.
        let final = try await HTTPClient.followRedirects(url: url, headers: headers)
        if StreamResolver.looksLikeDirectMedia(final) { return final }
        let html = try await HTTPClient.getHTML(
            url: final,
            referer: url,
            desktopUA: true,
            allowLenientTLS: true
        )
        if let media = StreamResolver.extractMediaURL(from: html, base: final) {
            return media
        }
        throw ProviderError.parseFailed("No playable media found on \(host.isEmpty ? absolute : host)")
    }

    // MARK: - Host detection

    private static func isVOE(_ host: String) -> Bool {
        let aliases = [
            "voe.sx", "jilliandescribecompany.com", "mikaylaarealike.com",
            "christopheruntilpoint.com", "walterprettytheir.com", "crystaltreatmenteast.com",
            "lauradaydo.com", "lancewhosedifficult.com", "dianaavoidthey.com",
            "jefferycontrolmodel.com", "charlestoughrace.com", "richardquestionbuilding.com",
            "jessicayeahcatch.com", "juliewomanwish.com", "rebeccapracticeloss.com",
            "johnbeyondnation.com", "shannontechnicalthere.com", "bethanyleadingstatus.com",
            "donaldlineargarden.com", "voe-network.com", "voe.sy", "voe.st"
        ]
        return aliases.contains(where: { host == $0 || host.hasSuffix(".\($0)") })
            || host.contains("voe")
    }

    private static func isStreamtape(_ host: String) -> Bool {
        host.contains("streamtape") || host.contains("streamta.site")
    }

    private static func isDood(_ host: String) -> Bool {
        [
            "dood.la", "dood.li", "dood.ws", "dood.so", "dood.to", "dood.cx",
            "dsvplay.com", "myvidplay.com", "playmogo.com", "do7go.com",
            "d000d.com", "vide0.net"
        ].contains(where: { host == $0 || host.hasSuffix(".\($0)") })
            || host.contains("dood")
    }

    private static func isVidoza(_ host: String) -> Bool {
        host.contains("vidoza") || host.contains("videzz")
    }

    private static func isMixDrop(_ host: String) -> Bool {
        host.contains("mixdrop") || host.contains("mxdrop") || host.contains("mixdroop")
            || host.contains("dr0pstream") || host.contains("dropstream")
            || host.hasPrefix("md") && host.contains(".")
    }

    
    private static func isVixcloud(_ host: String) -> Bool {
        host.contains("vixcloud") || host.contains("vixsrc") || host.contains("vixcloud.co")
    }

    private static func normalizeVOEURL(_ url: URL) -> URL {
        guard let host = url.host()?.lowercased(), isVOE(host) else { return url }
        let parts = url.path.split(separator: "/").map(String.init)
        guard let id = parts.last, !id.isEmpty else { return url }
        if url.path.contains("/e/") || url.path.contains("/d/") {
            if host != "voe.sx" {
                return URL(string: "https://voe.sx\(url.path)") ?? url
            }
            return url
        }
        // Short form https://voe.sx/<id>
        if parts.count == 1 {
            return URL(string: "https://voe.sx/e/\(id)") ?? url
        }
        return url
    }

    
    // MARK: - Vixcloud (StreamingCommunity)

    private static func extractVixcloud(url: URL, headers: [String: String]) async throws -> URL {
        var reqHeaders = headers
        if reqHeaders["Referer"] == nil {
            reqHeaders["Referer"] = "https://\(url.host() ?? "vixcloud.co")/"
        }
        if reqHeaders["User-Agent"] == nil {
            reqHeaders["User-Agent"] = HTTPClient.desktopUserAgent
        }
        let html = try await HTTPClient.getHTML(
            url: url,
            referer: URL(string: reqHeaders["Referer"] ?? url.absoluteString),
            desktopUA: true,
            allowLenientTLS: true
        )
        // window.video = { id: 123, ... }
        let videoID = firstMatch(in: html, pattern: #"window\.video\s*=\s*\{[^}]*?id\s*:\s*(\d+)"#)
            ?? firstMatch(in: html, pattern: #"window\.video\s*=\s*\{[^}]*?"id"\s*:\s*(\d+)"#)
            ?? firstMatch(in: html, pattern: #""id"\s*:\s*(\d+)"#)
        guard let videoID, !videoID.isEmpty else {
            if let media = StreamResolver.extractMediaURL(from: html, base: url) {
                return media
            }
            throw ProviderError.parseFailed("Vixcloud: window.video id missing")
        }
        let token = firstMatch(in: html, pattern: #"token\s*:\s*"([^"]+)""#)
            ?? firstMatch(in: html, pattern: #""token"\s*:\s*"([^"]+)""#)
        let expires = firstMatch(in: html, pattern: #"expires\s*:\s*"([^"]+)""#)
            ?? firstMatch(in: html, pattern: #""expires"\s*:\s*"([^"]+)""#)
        let hasB = html.contains("b=1")
        var comps = URLComponents(string: "https://\(url.host() ?? "vixcloud.co")/playlist/\(videoID)")!
        var items: [URLQueryItem] = []
        if let token, !token.isEmpty { items.append(URLQueryItem(name: "token", value: token)) }
        if let expires, !expires.isEmpty { items.append(URLQueryItem(name: "expires", value: expires)) }
        if hasB { items.append(URLQueryItem(name: "b", value: "1")) }
        comps.queryItems = items.isEmpty ? nil : items
        guard let playlist = comps.url else {
            throw ProviderError.parseFailed("Vixcloud: playlist URL invalid")
        }
        return playlist
    }

    // MARK: - VOE (DecryptHelper F7)

    private static func extractVOE(url: URL, headers: [String: String]) async throws -> URL {
        let html = try await HTTPClient.getHTML(
            url: url,
            referer: url,
            desktopUA: true,
            allowLenientTLS: true
        )

        // Android: VOE HTML often points at a rotating alias domain — refetch same path there.
        let pageURL: URL
        // Only follow bounce hosts that are known VOE aliases (never the first random https:// on the page).
        if let bounceHost = firstMatch(in: html, pattern: #"https://([a-zA-Z0-9.-]+)/"#),
           bounceHost.lowercased() != (url.host() ?? "").lowercased(),
           isVOE(bounceHost.lowercased()) {
            var comps = URLComponents(url: url, resolvingAgainstBaseURL: false)
            comps?.host = bounceHost
            comps?.scheme = "https"
            pageURL = comps?.url ?? url
        } else {
            pageURL = url
        }

        let pageHTML: String
        if pageURL == url {
            pageHTML = html
        } else {
            pageHTML = try await HTTPClient.getHTML(
                url: pageURL,
                referer: url,
                desktopUA: true,
                allowLenientTLS: true
            )
        }

        let encoded = firstMatch(in: pageHTML, pattern: #"<script\s+type="application/json">(.*?)</script>"#)
            ?? firstMatch(in: pageHTML, pattern: #"<script type='application/json'>(.*?)</script>"#)
        guard let encoded, !encoded.isEmpty else {
            if let media = StreamResolver.extractMediaURL(from: pageHTML, base: pageURL) {
                return media
            }
            throw ProviderError.parseFailed("VOE: encoded payload missing")
        }

        let json = VOEDecrypt.decrypt(encoded)
        guard let source = json["source"] as? String, let media = URL(string: source), !source.isEmpty else {
            throw ProviderError.parseFailed("VOE: no source after decrypt")
        }
        return media
    }

    // MARK: - Streamtape

    private static func extractStreamtape(url: URL, headers: [String: String]) async throws -> URL {
        let html = try await HTTPClient.getHTML(
            url: url,
            referer: url,
            desktopUA: true,
            allowLenientTLS: true
        )
        let pattern =
            #"document\.getElementById\('botlink'\)\.innerHTML\s*=\s*'([^']+)'\s*\+\s*\('([^']+)'\)\.substring\((\d+)\)"#
        guard let match = regexGroups(in: html, pattern: pattern), match.count >= 3,
              let start = Int(match[2]) else {
            if let media = StreamResolver.extractMediaURL(from: html, base: url) { return media }
            throw ProviderError.parseFailed("Streamtape: botlink not found")
        }
        let params = match[1]
        let clean = params.count > start ? String(params.dropFirst(start)) : params
        let id = firstMatch(in: clean, pattern: #"id=([^&]+)"#) ?? ""
        let expires = firstMatch(in: clean, pattern: #"expires=([^&]+)"#) ?? ""
        let ip = firstMatch(in: clean, pattern: #"ip=([^&]+)"#) ?? ""
        let token = firstMatch(in: clean, pattern: #"token=([^&]+)"#) ?? ""
        guard !id.isEmpty, !token.isEmpty else {
            throw ProviderError.parseFailed("Streamtape: missing video params")
        }
        let host = url.host() ?? "streamtape.com"
        let getVideo = "https://\(host)/get_video?id=\(id)&expires=\(expires)&ip=\(ip)&token=\(token)&stream=1"
        guard let getURL = URL(string: getVideo) else { throw ProviderError.invalidURL }
        return try await HTTPClient.followRedirects(url: getURL, headers: [
            "User-Agent": HTTPClient.desktopUserAgent,
            "Referer": url.absoluteString
        ])
    }

    // MARK: - DoodStream

    private static func extractDood(url: URL, headers: [String: String]) async throws -> URL {
        let embed = URL(string: url.absoluteString.replacingOccurrences(of: "/d/", with: "/e/")) ?? url
        let html = try await HTTPClient.getHTML(
            url: embed,
            referer: url,
            desktopUA: true,
            allowLenientTLS: true
        )
        guard let md5Path = firstMatch(in: html, pattern: #"(/pass_md5/[^'"]+)"#) else {
            throw ProviderError.parseFailed("DoodStream: pass_md5 missing")
        }
        let base = "\(embed.scheme ?? "https")://\(embed.host() ?? "")"
        guard let md5URL = URL(string: base + md5Path) else { throw ProviderError.invalidURL }
        let prefix = try await HTTPClient.getText(url: md5URL, headers: [
            "User-Agent": HTTPClient.desktopUserAgent,
            "Referer": embed.absoluteString
        ]).trimmingCharacters(in: .whitespacesAndNewlines)
        let token = md5Path.split(separator: "/").last.map(String.init) ?? ""
        let hash = String((0..<10).map { _ in
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".randomElement()!
        })
        let final = "\(prefix)\(hash)?token=\(token)"
        guard let media = URL(string: final) else { throw ProviderError.invalidURL }
        return media
    }

    // MARK: - Vidoza

    private static func extractVidoza(url: URL, headers: [String: String]) async throws -> URL {
        let html = try await HTTPClient.getHTML(
            url: url,
            referer: url,
            desktopUA: true,
            allowLenientTLS: true
        )
        if let doc = try? SwiftSoup.parse(html),
           let src = try? doc.selectFirst("source[src], video[src]")?.attr("src"),
           let media = HTTPClient.absoluteURL(src, base: url) {
            return media
        }
        if let media = StreamResolver.extractMediaURL(from: html, base: url) {
            return media
        }
        throw ProviderError.parseFailed("Vidoza: source missing")
    }

    // MARK: - MixDrop (P.A.C.K.E.R. unpack → wurl)

    private static func extractMixDrop(url: URL, headers: [String: String]) async throws -> URL {
        var path = url.absoluteString
            .replacingOccurrences(of: "/f/", with: "/e/")
            .replacingOccurrences(of: ".club/", with: ".ag/")
        if let trimmed = firstMatch(in: path, pattern: #"^(https?://[^/]+/e/[^/?#]+)"#) {
            path = trimmed
        }
        guard let embed = URL(string: path) else { throw ProviderError.invalidURL }
        let html = try await HTTPClient.getHTML(
            url: embed,
            referer: url,
            desktopUA: true,
            allowLenientTLS: true
        )
        let packed = firstMatch(in: html, pattern: #"(eval\(function\(p,a,c,k,e,d\)[\s\S]*?)</script>"#)
        let script = packed.flatMap { JsUnpacker.unpack($0) } ?? html
        guard let sourceUrl = firstMatch(in: script, pattern: #"wurl\s*=\s*"(.*?)""#) else {
            if let media = StreamResolver.extractMediaURL(from: html, base: embed) { return media }
            throw ProviderError.parseFailed("MixDrop: wurl missing")
        }
        let final: String
        if sourceUrl.hasPrefix("//") {
            final = "https:\(sourceUrl)"
        } else if sourceUrl.hasPrefix("http") {
            final = sourceUrl
        } else {
            final = "https://\(sourceUrl)"
        }
        guard let media = URL(string: final) else { throw ProviderError.invalidURL }
        return media
    }

    // MARK: - Regex helpers

    private static func firstMatch(in text: String, pattern: String) -> String? {
        guard let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive, .dotMatchesLineSeparators]) else {
            return nil
        }
        let range = NSRange(text.startIndex..., in: text)
        guard let match = regex.firstMatch(in: text, options: [], range: range) else { return nil }
        if match.numberOfRanges > 1, let capture = Range(match.range(at: 1), in: text) {
            return String(text[capture])
        }
        if let full = Range(match.range(at: 0), in: text) {
            return String(text[full])
        }
        return nil
    }

    private static func regexGroups(in text: String, pattern: String) -> [String]? {
        guard let regex = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive, .dotMatchesLineSeparators]) else {
            return nil
        }
        let range = NSRange(text.startIndex..., in: text)
        guard let match = regex.firstMatch(in: text, options: [], range: range) else { return nil }
        var groups: [String] = []
        for i in 1..<match.numberOfRanges {
            guard let r = Range(match.range(at: i), in: text) else { return nil }
            groups.append(String(text[r]))
        }
        return groups
    }
}

/// VOE F7 decrypt (Android DecryptHelper parity).
enum VOEDecrypt {
    static func decrypt(_ encoded: String) -> [String: Any] {
        let step1 = rot13(encoded)
        let step2 = replacePatterns(step1)
        let step3 = step2.replacingOccurrences(of: "_", with: "")
        guard let data4 = Data(base64Encoded: step3),
              let step4 = String(data: data4, encoding: .utf8) else { return [:] }
        let step5 = charShift(step4, by: 3)
        let step6 = String(step5.reversed())
        guard let data7 = Data(base64Encoded: step6),
              let jsonText = String(data: data7, encoding: .utf8),
              let obj = try? JSONSerialization.jsonObject(with: Data(jsonText.utf8)) as? [String: Any] else {
            return [:]
        }
        return obj
    }

    private static func rot13(_ input: String) -> String {
        String(input.map { c in
            switch c {
            case "A"..."Z":
                return Character(UnicodeScalar((Int(c.asciiValue!) - 65 + 13) % 26 + 65)!)
            case "a"..."z":
                return Character(UnicodeScalar((Int(c.asciiValue!) - 97 + 13) % 26 + 97)!)
            default:
                return c
            }
        })
    }

    private static func replacePatterns(_ input: String) -> String {
        ["@$", "^^", "~@", "%?", "*~", "!!", "#&"].reduce(input) { partial, pattern in
            partial.replacingOccurrences(of: pattern, with: "_")
        }
    }

    private static func charShift(_ input: String, by shift: Int) -> String {
        String(input.unicodeScalars.map { Character(UnicodeScalar(UInt32($0.value) &- UInt32(shift)) ?? $0) })
    }
}

/// Minimal Dean Edwards P.A.C.K.E.R. unpacker (MixDrop).
enum JsUnpacker {
    static func unpack(_ packedJS: String) -> String? {
        let pattern = #"\}\s*\('(.*)',\s*(.*?),\s*(\d+),\s*'(.*?)'\.split\('\|'\)"#
        guard let regex = try? NSRegularExpression(pattern: pattern, options: [.dotMatchesLineSeparators]),
              let match = regex.firstMatch(in: packedJS, range: NSRange(packedJS.startIndex..., in: packedJS)),
              match.numberOfRanges >= 5,
              let payloadRange = Range(match.range(at: 1), in: packedJS),
              let radixRange = Range(match.range(at: 2), in: packedJS),
              let countRange = Range(match.range(at: 3), in: packedJS),
              let symRange = Range(match.range(at: 4), in: packedJS) else {
            return nil
        }

        let payload = String(packedJS[payloadRange]).replacingOccurrences(of: "\\'", with: "'")
        let radix = Int(packedJS[radixRange]) ?? 36
        let count = Int(packedJS[countRange]) ?? 0
        let symtab = String(packedJS[symRange]).split(separator: "|", omittingEmptySubsequences: false).map(String.init)
        guard symtab.count == count || count == 0 else { return nil }

        var decoded = payload
        let wordRegex = try? NSRegularExpression(pattern: #"\b\w+\b"#)
        let nsPayload = payload as NSString
        let matches = wordRegex?.matches(in: payload, range: NSRange(location: 0, length: nsPayload.length)) ?? []
        var replaceOffset = 0
        for m in matches {
            let word = nsPayload.substring(with: m.range)
            guard let x = Int(word, radix: radix), x >= 0, x < symtab.count else { continue }
            let value = symtab[x]
            guard !value.isEmpty else { continue }
            let start = decoded.index(decoded.startIndex, offsetBy: m.range.location + replaceOffset)
            let end = decoded.index(start, offsetBy: m.range.length)
            decoded.replaceSubrange(start..<end, with: value)
            replaceOffset += value.count - word.count
        }
        return decoded
    }
}
