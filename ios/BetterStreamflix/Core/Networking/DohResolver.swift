import Foundation

/// DNS-over-HTTPS aligned with Android `DnsResolver` (Cloudflare DoH, IPv4 preferred).
/// Used so ISP-poisoned system DNS cannot blackhole `api.themoviedb.org` / scrape hosts.
enum DohResolver {
    static let cloudflareDoH = URL(string: "https://cloudflare-dns.com/dns-query")!
    static let googleDoH = URL(string: "https://dns.google/resolve")!

    private static let cacheBox = CacheBox()

    static func ipv4(for hostname: String) async throws -> String {
        let host = hostname.lowercased()
        if let cached = cacheBox.get(host) {
            return cached
        }

        let ip: String
        if let resolved = try await lookupIPv4(host: host, endpoint: cloudflareDoH) {
            ip = resolved
        } else if let resolved = try await lookupIPv4(host: host, endpoint: googleDoH) {
            ip = resolved
        } else {
            throw ProviderError.parseFailed("DoH could not resolve \(host)")
        }
        cacheBox.set(host, ip: ip)
        return ip
    }

    /// Hosts that Android routes through DoH / are commonly ISP-blackholed.
    static func shouldUseDoH(for host: String?) -> Bool {
        guard let host = host?.lowercased() else { return false }
        return host.contains("themoviedb.org")
            || host.contains("tmdb.org")
            || host.contains("image.tmdb.org")
            || host.contains("speedracelight.com")
            || host.contains("enc-dec.app")
            || host.contains("filmpalast.to")
            || host.contains("serienstream.")
            || host.contains("aniworld.to")
    }

    private static func lookupIPv4(host: String, endpoint: URL) async throws -> String? {
        var components = URLComponents(url: endpoint, resolvingAgainstBaseURL: false)!
        components.queryItems = [
            URLQueryItem(name: "name", value: host),
            URLQueryItem(name: "type", value: "A"),
        ]
        guard let url = components.url else { return nil }

        var request = URLRequest(url: url)
        request.setValue("application/dns-json", forHTTPHeaderField: "Accept")
        request.timeoutInterval = 10

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let http = response as? HTTPURLResponse, (200...299).contains(http.statusCode) else {
            return nil
        }
        guard
            let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
            let answers = json["Answer"] as? [[String: Any]]
        else {
            return nil
        }

        for answer in answers {
            guard let type = answer["type"] as? Int, type == 1,
                  let data = answer["data"] as? String,
                  isIPv4(data) else { continue }
            return data
        }
        return nil
    }

    private static func isIPv4(_ value: String) -> Bool {
        let parts = value.split(separator: ".")
        guard parts.count == 4 else { return false }
        return parts.allSatisfy { part in
            guard let n = Int(part) else { return false }
            return (0...255).contains(n)
        }
    }
}

private final class CacheBox: @unchecked Sendable {
    private var cache: [String: (ip: String, expiry: Date)] = [:]
    private let lock = NSLock()

    func get(_ host: String) -> String? {
        lock.lock()
        defer { lock.unlock() }
        guard let cached = cache[host], cached.expiry > Date() else { return nil }
        return cached.ip
    }

    func set(_ host: String, ip: String) {
        lock.lock()
        cache[host] = (ip, Date().addingTimeInterval(300))
        lock.unlock()
    }
}
