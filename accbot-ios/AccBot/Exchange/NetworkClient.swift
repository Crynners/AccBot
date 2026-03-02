import Foundation

/// Thin async wrapper over URLSession with 30-second timeout.
/// Handles GET/POST with custom headers, form-encoded and JSON bodies.
final class NetworkClient {
    private let session: URLSession

    init(session: URLSession? = nil) {
        if let session = session {
            self.session = session
        } else {
            let config = URLSessionConfiguration.default
            config.timeoutIntervalForRequest = 30
            config.timeoutIntervalForResource = 60
            self.session = URLSession(configuration: config)
        }
    }

    // MARK: - GET

    func get(
        url: String,
        headers: [String: String] = [:]
    ) async throws -> (Data, HTTPURLResponse) {
        guard let url = URL(string: url) else {
            throw NetworkError.invalidUrl(url)
        }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        for (key, value) in headers {
            request.setValue(value, forHTTPHeaderField: key)
        }

        return try await execute(request)
    }

    // MARK: - POST (JSON body)

    func postJson(
        url: String,
        body: [String: Any],
        headers: [String: String] = [:]
    ) async throws -> (Data, HTTPURLResponse) {
        guard let url = URL(string: url) else {
            throw NetworkError.invalidUrl(url)
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: body)

        for (key, value) in headers {
            request.setValue(value, forHTTPHeaderField: key)
        }

        return try await execute(request)
    }

    // MARK: - POST (form-encoded body)

    func postForm(
        url: String,
        body: [String: String],
        headers: [String: String] = [:]
    ) async throws -> (Data, HTTPURLResponse) {
        guard let url = URL(string: url) else {
            throw NetworkError.invalidUrl(url)
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")

        let formBody = body.map { key, value in
            "\(urlEncode(key))=\(urlEncode(value))"
        }.joined(separator: "&")
        request.httpBody = formBody.data(using: .utf8)

        for (key, value) in headers {
            request.setValue(value, forHTTPHeaderField: key)
        }

        return try await execute(request)
    }

    // MARK: - POST (raw JSON body string)

    /// Post with a pre-constructed JSON body string.
    /// Use this when the exact body string matters for signature computation (e.g. KuCoin, Coinbase).
    func postJsonRaw(
        url: String,
        body: String,
        headers: [String: String] = [:]
    ) async throws -> (Data, HTTPURLResponse) {
        guard let url = URL(string: url) else {
            throw NetworkError.invalidUrl(url)
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = body.data(using: .utf8)

        for (key, value) in headers {
            request.setValue(value, forHTTPHeaderField: key)
        }

        return try await execute(request)
    }

    // MARK: - POST (raw form-encoded body string)

    /// Post with a pre-constructed form body string.
    /// Use this when the exact body string matters for signature computation (e.g. Kraken).
    func postFormRaw(
        url: String,
        body: String,
        headers: [String: String] = [:]
    ) async throws -> (Data, HTTPURLResponse) {
        guard let url = URL(string: url) else {
            throw NetworkError.invalidUrl(url)
        }

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        request.httpBody = body.data(using: .utf8)

        for (key, value) in headers {
            request.setValue(value, forHTTPHeaderField: key)
        }

        return try await execute(request)
    }

    // MARK: - DELETE

    func delete(
        url: String,
        headers: [String: String] = [:]
    ) async throws -> (Data, HTTPURLResponse) {
        guard let url = URL(string: url) else {
            throw NetworkError.invalidUrl(url)
        }

        var request = URLRequest(url: url)
        request.httpMethod = "DELETE"
        for (key, value) in headers {
            request.setValue(value, forHTTPHeaderField: key)
        }

        return try await execute(request)
    }

    // MARK: - Private

    private func execute(_ request: URLRequest) async throws -> (Data, HTTPURLResponse) {
        let (data, response) = try await session.data(for: request)

        guard let httpResponse = response as? HTTPURLResponse else {
            throw NetworkError.invalidResponse
        }

        guard (200...299).contains(httpResponse.statusCode) else {
            let body = String(data: data, encoding: .utf8) ?? ""
            throw NetworkError.httpError(statusCode: httpResponse.statusCode, body: body)
        }

        return (data, httpResponse)
    }

    private func urlEncode(_ string: String) -> String {
        var allowed = CharacterSet.urlQueryAllowed
        allowed.remove(charactersIn: "&=+")
        return string.addingPercentEncoding(withAllowedCharacters: allowed) ?? string
    }
}

// MARK: - Network Errors

enum NetworkError: LocalizedError {
    case invalidUrl(String)
    case invalidResponse
    case httpError(statusCode: Int, body: String)
    case decodingError(String)

    var errorDescription: String? {
        switch self {
        case .invalidUrl(let url):
            return "Invalid URL: \(url)"
        case .invalidResponse:
            return "Invalid server response"
        case .httpError(let code, let body):
            return "HTTP \(code): \(body.prefix(200))"
        case .decodingError(let detail):
            return "Decoding error: \(detail)"
        }
    }

    var isRetryable: Bool {
        switch self {
        case .httpError(let code, _):
            return code >= 500 || code == 429
        case .invalidResponse:
            return true
        default:
            return false
        }
    }
}
