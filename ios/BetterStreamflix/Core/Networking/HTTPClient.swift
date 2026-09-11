import Foundation
import Security

enum HTTPClient {
    /// Matches Android `NetworkClient.USER_AGENT` (best Cloudflare / DDoS-Guard compatibility).
    static let userAgent =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/131.0.0.0 Mobile Safari/537.36"

    /// Desktop Chrome — used when a scrape host expects it.
    static let desktopUserAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/137.0.0.0 Safari/537.36"

    private static let session: URLSession = {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 30
        config.httpCookieAcceptPolicy = .always
        config.httpShouldSetCookies = true
        config.httpAdditionalHeaders = [
            "Accept-Language": "de-DE,de;q=0.9,en-US;q=0.8,en;q=0.7",
        ]
        return URLSession(configuration: config)
    }()

    /// Trust-all session — mirrors Android `NetworkClient.trustAll` / unsafe OkHttp clients.
    private static let lenientSession: URLSession = {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 30
        config.httpCookieAcceptPolicy = .always
        config.httpShouldSetCookies = true
        config.httpAdditionalHeaders = [
            "Accept-Language": "de-DE,de;q=0.9,en-US;q=0.8,en;q=0.7",
        ]
        return URLSession(
            configuration: config,
            delegate: LenientTLSDelegate.shared,
            delegateQueue: .main
        )
    }()

    static func getHTML(
        url: URL,
        referer: URL? = nil,
        desktopUA: Bool = false,
        allowLenientTLS: Bool = false
    ) async throws -> String {
        do {
            return try await fetchHTML(
                url: url,
                referer: referer,
                desktopUA: desktopUA,
                session: session
            )
        } catch {
            // Some WAFs return 403/404 for one UA family — flip once before TLS fallback.
            if isRetryableHTTP(error),
               let html = try? await fetchHTML(
                   url: url,
                   referer: referer ?? url,
                   desktopUA: !desktopUA,
                   session: session
               ) {
                return html
            }
            guard allowLenientTLS || isTLSFailure(error) || isNetworkFailure(error) || isRetryableHTTP(error) else {
                throw error
            }
            return try await fetchHTML(
                url: url,
                referer: referer ?? url,
                desktopUA: desktopUA,
                session: lenientSession
            )
        }
    }

    /// Try multiple absolute base URLs until one returns HTML (Android-style mirror failover).
    static func getHTML(
        path: String,
        bases: [URL],
        referer: URL? = nil,
        desktopUA: Bool = false
    ) async throws -> (html: String, base: URL) {
        var lastError: Error = ProviderError.emptyResponse
        for base in bases {
            let url = path.isEmpty ? base : (URL(string: path, relativeTo: base)?.absoluteURL ?? base)
            do {
                let html = try await getHTML(
                    url: url,
                    referer: referer ?? base,
                    desktopUA: desktopUA,
                    allowLenientTLS: true
                )
                return (html, base)
            } catch {
                lastError = error
            }
        }
        throw lastError
    }

    static func getJSON(url: URL, headers: [String: String] = [:]) async throws -> Data {
        do {
            return try await fetchData(
                url: url,
                method: "GET",
                headers: headers,
                body: nil,
                session: session
            )
        } catch {
            guard isTLSFailure(error) || isNetworkFailure(error) || isRetryableHTTP(error) else { throw error }
            return try await fetchData(
                url: url,
                method: "GET",
                headers: headers,
                body: nil,
                session: lenientSession
            )
        }
    }

    /// Form POST used by Android DLE / MEGAKino search forms.
    static func postForm(
        url: URL,
        fields: [String: String],
        headers: [String: String] = [:],
        allowLenientTLS: Bool = false
    ) async throws -> Data {
        let body = fields
            .map { key, value in
                "\(formEncode(key))=\(formEncode(value))"
            }
            .joined(separator: "&")
            .data(using: .utf8)
        let merged = headers.merging([
            "Content-Type": "application/x-www-form-urlencoded",
            "Accept": "text/html,application/xhtml+xml,application/json",
        ]) { _, new in new }
        do {
            return try await fetchData(
                url: url,
                method: "POST",
                headers: merged,
                body: body,
                session: session
            )
        } catch {
            guard allowLenientTLS || isTLSFailure(error) || isNetworkFailure(error) || isRetryableHTTP(error) else {
                throw error
            }
            return try await fetchData(
                url: url,
                method: "POST",
                headers: merged,
                body: body,
                session: lenientSession
            )
        }
    }

    static func postJSON(url: URL, body: Data, headers: [String: String] = [:]) async throws -> Data {
        let merged = headers.merging([
            "Content-Type": "application/json",
            "Accept": "application/json",
        ]) { _, new in new }
        do {
            return try await fetchData(
                url: url,
                method: "POST",
                headers: merged,
                body: body,
                session: session
            )
        } catch {
            guard isTLSFailure(error) || isNetworkFailure(error) || isRetryableHTTP(error) else { throw error }
            return try await fetchData(
                url: url,
                method: "POST",
                headers: merged,
                body: body,
                session: lenientSession
            )
        }
    }

    static func followRedirects(url: URL, headers: [String: String] = [:]) async throws -> URL {
        var request = makeRequest(url: url, method: "GET", headers: browserHeaders(merging: headers), body: nil)
        do {
            let (_, response) = try await session.data(for: request)
            return response.url ?? url
        } catch {
            guard isTLSFailure(error) || isNetworkFailure(error) else { throw error }
            var retry = makeRequest(url: url, method: "GET", headers: browserHeaders(merging: headers), body: nil)
            let (_, response) = try await lenientSession.data(for: retry)
            return response.url ?? url
        }
    }

    static func absoluteURL(_ value: String?, base: URL) -> URL? {
        guard var value, !value.isEmpty else { return nil }
        value = value.trimmingCharacters(in: .whitespacesAndNewlines)
        if value.hasPrefix("http://") || value.hasPrefix("https://") {
            return URL(string: value)
        }
        if value.hasPrefix("//") {
            return URL(string: "https:\(value)")
        }
        return URL(string: value, relativeTo: base)?.absoluteURL
    }

    /// Build API URLs without encoding `/` inside the path (Foundation `appendingPathComponent` would).
    static func apiURL(base: URL, path: String, query: [URLQueryItem] = []) -> URL? {
        let trimmed = path.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        let joined = base.absoluteString.hasSuffix("/")
            ? base.absoluteString + trimmed
            : base.absoluteString + "/" + trimmed
        var components = URLComponents(string: joined)
        if !query.isEmpty {
            components?.queryItems = query
        }
        return components?.url
    }

    // MARK: - Internals

    private static func fetchHTML(
        url: URL,
        referer: URL?,
        desktopUA: Bool,
        session: URLSession
    ) async throws -> String {
        var headers = browserHeaders(
            desktopUA: desktopUA,
            merging: [
                "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
            ]
        )
        if let referer {
            headers["Referer"] = referer.absoluteString
            headers["Sec-Fetch-Site"] = "same-origin"
        }
        let data = try await fetchData(
            url: url,
            method: "GET",
            headers: headers,
            body: nil,
            session: session
        )
        guard let html = String(data: data, encoding: .utf8), !html.isEmpty else {
            throw ProviderError.emptyResponse
        }
        if looksLikeBotChallenge(html) {
            throw ProviderError.streamGate(
                "Bot-Schutz blockiert \(url.host() ?? url.absoluteString). Bitte später erneut versuchen."
            )
        }
        return html
    }

    private static func fetchData(
        url: URL,
        method: String,
        headers: [String: String],
        body: Data?,
        session: URLSession
    ) async throws -> Data {
        var request = makeRequest(url: url, method: method, headers: headers, body: body)
        if request.value(forHTTPHeaderField: "User-Agent") == nil {
            request.setValue(userAgent, forHTTPHeaderField: "User-Agent")
        }
        if request.value(forHTTPHeaderField: "Accept") == nil {
            request.setValue("application/json,text/html,*/*", forHTTPHeaderField: "Accept")
        }
        let (data, response) = try await session.data(for: request)
        if let http = response as? HTTPURLResponse, !(200...299).contains(http.statusCode) {
            if let html = String(data: data, encoding: .utf8), looksLikeBotChallenge(html) {
                throw ProviderError.streamGate(
                    "Bot-Schutz (HTTP \(http.statusCode)) bei \(url.host() ?? url.absoluteString)"
                )
            }
            throw ProviderError.http(http.statusCode, url: url.absoluteString)
        }
        return data
    }

    private static func makeRequest(
        url: URL,
        method: String,
        headers: [String: String],
        body: Data?
    ) -> URLRequest {
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.httpBody = body
        for (key, value) in headers {
            request.setValue(value, forHTTPHeaderField: key)
        }
        return request
    }

    /// Browser-like defaults from Android `NetworkClient` interceptors.
    private static func browserHeaders(
        desktopUA: Bool = false,
        merging extra: [String: String] = [:]
    ) -> [String: String] {
        var headers: [String: String] = [
            "User-Agent": desktopUA ? desktopUserAgent : userAgent,
            "Accept-Language": "de-DE,de;q=0.9,en-US;q=0.8,en;q=0.7",
            "Upgrade-Insecure-Requests": "1",
            "Sec-Fetch-Dest": "document",
            "Sec-Fetch-Mode": "navigate",
            "Sec-Fetch-Site": "none",
            "Sec-Fetch-User": "?1",
        ]
        for (key, value) in extra {
            headers[key] = value
        }
        return headers
    }

    private static func formEncode(_ value: String) -> String {
        var allowed = CharacterSet.urlQueryAllowed
        allowed.remove(charactersIn: ":#[]@!$&'()*+,;=")
        return value.addingPercentEncoding(withAllowedCharacters: allowed) ?? value
    }

    private static func looksLikeBotChallenge(_ html: String) -> Bool {
        // Real interstitial pages are short. Do NOT match "ddos-guard" alone — valid
        // SerienStream/AniWorld pages embed that script name on every 200 OK response.
        guard html.count < 40_000 else { return false }
        let lower = html.lowercased()
        return lower.contains("cf-mitigated")
            || lower.contains("cf-browser-verification")
            || lower.contains("just a moment")
            || lower.contains("checking your browser")
            || lower.contains("challenge-platform")
            || lower.contains("_cf_chl")
            || (lower.contains("ddos-guard") && lower.contains("challenge"))
    }

    private static func isRetryableHTTP(_ error: Error) -> Bool {
        if case let ProviderError.http(code, _) = error {
            // Include 404: some bot walls answer with a fake Not Found for the wrong UA.
            return [403, 404, 429, 502, 503, 520, 521, 522, 523, 524].contains(code)
        }
        return false
    }

    private static func isTLSFailure(_ error: Error) -> Bool {
        let ns = error as NSError
        if ns.domain == NSURLErrorDomain {
            return [
                NSURLErrorServerCertificateUntrusted,
                NSURLErrorSecureConnectionFailed,
                NSURLErrorServerCertificateHasBadDate,
                NSURLErrorServerCertificateNotYetValid,
                NSURLErrorClientCertificateRejected,
                NSURLErrorClientCertificateRequired,
                NSURLErrorCannotFindHost,
                NSURLErrorDNSLookupFailed,
            ].contains(ns.code)
        }
        return false
    }

    private static func isNetworkFailure(_ error: Error) -> Bool {
        let ns = error as NSError
        guard ns.domain == NSURLErrorDomain else { return false }
        return [
            NSURLErrorTimedOut,
            NSURLErrorCannotConnectToHost,
            NSURLErrorNetworkConnectionLost,
            NSURLErrorNotConnectedToInternet,
        ].contains(ns.code)
    }
}

private final class LenientTLSDelegate: NSObject, URLSessionDelegate, @unchecked Sendable {
    static let shared = LenientTLSDelegate()

    func urlSession(
        _ session: URLSession,
        didReceive challenge: URLAuthenticationChallenge,
        completionHandler: @escaping @MainActor @Sendable (URLSession.AuthChallengeDisposition, URLCredential?) -> Void
    ) {
        let disposition: URLSession.AuthChallengeDisposition
        let credential: URLCredential?
        if challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust,
           let trust = challenge.protectionSpace.serverTrust {
            let exceptions = SecTrustCopyExceptions(trust)
            SecTrustSetExceptions(trust, exceptions)
            disposition = .useCredential
            credential = URLCredential(trust: trust)
        } else {
            disposition = .performDefaultHandling
            credential = nil
        }
        Task { @MainActor in
            completionHandler(disposition, credential)
        }
    }
}
