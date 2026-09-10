import Foundation

enum HTTPClient {
    static let userAgent =
        "Mozilla/5.0 (iPhone; CPU iPhone OS 26_0 like Mac OS X) AppleWebKit/605.1.15 " +
        "(KHTML, like Gecko) Version/26.0 Mobile/15E148 Safari/604.1"

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

    /// Used only as last resort for scrape hosts with broken intermediate certs / captive DNS.
    private static let lenientSession: URLSession = {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 30
        return URLSession(configuration: config, delegate: LenientTLSDelegate.shared, delegateQueue: nil)
    }()

    static func getHTML(
        url: URL,
        referer: URL? = nil,
        desktopUA: Bool = false,
        allowLenientTLS: Bool = false
    ) async throws -> String {
        do {
            return try await fetchHTML(url: url, referer: referer, desktopUA: desktopUA, session: session)
        } catch {
            guard allowLenientTLS, isTLSFailure(error) else { throw error }
            return try await fetchHTML(url: url, referer: referer, desktopUA: desktopUA, session: lenientSession)
        }
    }

    /// Try multiple absolute base URLs until one returns HTML.
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
                let html = try await getHTML(url: url, referer: referer ?? base, desktopUA: desktopUA, allowLenientTLS: true)
                return (html, base)
            } catch {
                lastError = error
            }
        }
        throw lastError
    }

    static func getJSON(url: URL, headers: [String: String] = [:]) async throws -> Data {
        var request = URLRequest(url: url)
        request.setValue(desktopUserAgent, forHTTPHeaderField: "User-Agent")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        for (key, value) in headers {
            request.setValue(value, forHTTPHeaderField: key)
        }
        let (data, response) = try await session.data(for: request)
        if let http = response as? HTTPURLResponse, !(200...299).contains(http.statusCode) {
            throw ProviderError.http(http.statusCode)
        }
        return data
    }

    static func postJSON(url: URL, body: Data, headers: [String: String] = [:]) async throws -> Data {
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.httpBody = body
        request.setValue(desktopUserAgent, forHTTPHeaderField: "User-Agent")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        for (key, value) in headers {
            request.setValue(value, forHTTPHeaderField: key)
        }
        let (data, response) = try await session.data(for: request)
        if let http = response as? HTTPURLResponse, !(200...299).contains(http.statusCode) {
            throw ProviderError.http(http.statusCode)
        }
        return data
    }

    static func followRedirects(url: URL, headers: [String: String] = [:]) async throws -> URL {
        var request = URLRequest(url: url)
        request.setValue(desktopUserAgent, forHTTPHeaderField: "User-Agent")
        for (key, value) in headers {
            request.setValue(value, forHTTPHeaderField: key)
        }
        do {
            let (_, response) = try await session.data(for: request)
            return response.url ?? url
        } catch {
            guard isTLSFailure(error) else { throw error }
            let (_, response) = try await lenientSession.data(for: request)
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

    private static func fetchHTML(
        url: URL,
        referer: URL?,
        desktopUA: Bool,
        session: URLSession
    ) async throws -> String {
        var request = URLRequest(url: url)
        request.setValue(desktopUA ? desktopUserAgent : userAgent, forHTTPHeaderField: "User-Agent")
        request.setValue("text/html,application/xhtml+xml", forHTTPHeaderField: "Accept")
        if let referer {
            request.setValue(referer.absoluteString, forHTTPHeaderField: "Referer")
        }
        let (data, response) = try await session.data(for: request)
        if let http = response as? HTTPURLResponse, !(200...299).contains(http.statusCode) {
            throw ProviderError.http(http.statusCode)
        }
        guard let html = String(data: data, encoding: .utf8), !html.isEmpty else {
            throw ProviderError.emptyResponse
        }
        return html
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
            ].contains(ns.code)
        }
        return false
    }
}

private final class LenientTLSDelegate: NSObject, URLSessionDelegate, @unchecked Sendable {
    static let shared = LenientTLSDelegate()

    func urlSession(
        _ session: URLSession,
        didReceive challenge: URLAuthenticationChallenge,
        completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void
    ) {
        if challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust,
           let trust = challenge.protectionSpace.serverTrust {
            completionHandler(.useCredential, URLCredential(trust: trust))
        } else {
            completionHandler(.performDefaultHandling, nil)
        }
    }
}
