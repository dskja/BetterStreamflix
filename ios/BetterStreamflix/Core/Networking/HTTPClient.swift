import Foundation
import Security

enum HTTPClient {
    static let userAgent =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 26_0 like Mac OS X) AppleWebKit/605.1.15 " +
        "(KHTML, like Gecko) Version/26.0 Mobile/15E148 Safari/604.1"

    /// Same desktop UA family Android providers use for scrape hosts.
    static let desktopUserAgent =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
        "Chrome/137.0.0.0 Safari/537.36"

    private static let session: URLSession = {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 30
        config.httpAdditionalHeaders = [
            "Accept-Language": "de-DE,de;q=0.9,en;q=0.8",
        ]
        return URLSession(configuration: config)
    }()

    /// Trust-all session — mirrors Android `NetworkClient.trustAll` / unsafe OkHttp clients.
    private static let lenientSession: URLSession = {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 30
        config.httpAdditionalHeaders = [
            "Accept-Language": "de-DE,de;q=0.9,en;q=0.8",
        ]
        return URLSession(configuration: config, delegate: LenientTLSDelegate.shared, delegateQueue: nil)
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
            guard allowLenientTLS || isTLSFailure(error) || isNetworkFailure(error) else { throw error }
            return try await fetchHTML(
                url: url,
                referer: referer,
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
            guard isTLSFailure(error) || isNetworkFailure(error) else { throw error }
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
            guard allowLenientTLS || isTLSFailure(error) || isNetworkFailure(error) else { throw error }
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
            guard isTLSFailure(error) || isNetworkFailure(error) else { throw error }
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
        var request = makeRequest(url: url, method: "GET", headers: headers, body: nil)
        request.setValue(desktopUserAgent, forHTTPHeaderField: "User-Agent")
        do {
            let (_, response) = try await session.data(for: request)
            return response.url ?? url
        } catch {
            guard isTLSFailure(error) || isNetworkFailure(error) else { throw error }
            var retry = makeRequest(url: url, method: "GET", headers: headers, body: nil)
            retry.setValue(desktopUserAgent, forHTTPHeaderField: "User-Agent")
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

    private static func formEncode(_ value: String) -> String {
        var allowed = CharacterSet.urlQueryAllowed
        allowed.remove(charactersIn: ":#[]@!$&'()*+,;=")
        return value.addingPercentEncoding(withAllowedCharacters: allowed) ?? value
    }

    private static func fetchHTML(
        url: URL,
        referer: URL?,
        desktopUA: Bool,
        session: URLSession
    ) async throws -> String {
        var headers: [String: String] = [
            "User-Agent": desktopUA ? desktopUserAgent : userAgent,
            "Accept": "text/html,application/xhtml+xml",
        ]
        if let referer {
            headers["Referer"] = referer.absoluteString
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
            request.setValue(desktopUserAgent, forHTTPHeaderField: "User-Agent")
        }
        if request.value(forHTTPHeaderField: "Accept") == nil {
            request.setValue("application/json", forHTTPHeaderField: "Accept")
        }
        let (data, response) = try await session.data(for: request)
        if let http = response as? HTTPURLResponse, !(200...299).contains(http.statusCode) {
            throw ProviderError.http(http.statusCode)
        }
        return data
    }

    /// Keep the hostname in the URL (Android/OkHttp pattern). DoH is applied via `PrivacyContext`,
    /// not by rewriting the host to an IP — that breaks SNI and cert hostname checks on URLSession.
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
}

extension LenientTLSDelegate {
    func urlSession(
        _ session: URLSession,
        didReceive challenge: URLAuthenticationChallenge,
        completionHandler: @escaping @MainActor @Sendable (URLSession.AuthChallengeDisposition, URLCredential?) -> Void
    ) {
        guard challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust,
              let trust = challenge.protectionSpace.serverTrust else {
            completionHandler(.performDefaultHandling, nil)
            return
        }
        // Mirror Android hostnameVerifier { _, _ -> true } + empty TrustManager.
        let exceptions = SecTrustCopyExceptions(trust)
        SecTrustSetExceptions(trust, exceptions)
        completionHandler(.useCredential, URLCredential(trust: trust))
    }
}
