import Foundation

/// KuCoin API implementation.
/// Supports sandbox mode via openapi-sandbox.kucoin.com.
///
/// Auth: HMAC-SHA256 with base64-decoded secret, base64-encoded result.
/// Prehash: timestamp + method + path + body
/// Passphrase is also HMAC-signed with the same secret (KC-API-KEY-VERSION: 2).
/// Headers: KC-API-KEY, KC-API-SIGN, KC-API-TIMESTAMP, KC-API-PASSPHRASE, KC-API-KEY-VERSION.
final class KuCoinApi: ExchangeApi {
    let exchange = Exchange.kucoin

    private let credentials: ExchangeCredentials
    private let isSandbox: Bool
    private let client: NetworkClient
    private let baseUrl: String

    init(credentials: ExchangeCredentials, isSandbox: Bool, client: NetworkClient) {
        self.credentials = credentials
        self.isSandbox = isSandbox
        self.client = client
        self.baseUrl = ExchangeConfig.baseUrl(for: .kucoin, isSandbox: isSandbox)
    }

    // MARK: - Authentication

    /// Build authenticated headers for KuCoin API requests.
    /// Prehash: timestamp + method + requestPath + body
    /// Signature: HMAC-SHA256(base64decode(secret), prehash), base64-encoded
    /// Passphrase: HMAC-SHA256(base64decode(secret), passphrase), base64-encoded (v2)
    private func authHeaders(method: String, path: String, body: String = "") -> [String: String] {
        let timestamp = "\(CryptoUtils.currentTimestampMs())"
        let preSign = "\(timestamp)\(method)\(path)\(body)"
        let signature = CryptoUtils.hmacSha256Base64Secret(message: preSign, base64Secret: credentials.apiSecret)
        let passphrase = credentials.passphrase.map {
            CryptoUtils.hmacSha256Base64Secret(message: $0, base64Secret: credentials.apiSecret)
        } ?? ""

        return [
            "KC-API-KEY": credentials.apiKey,
            "KC-API-SIGN": signature,
            "KC-API-TIMESTAMP": timestamp,
            "KC-API-PASSPHRASE": passphrase,
            "KC-API-KEY-VERSION": "2"
        ]
    }

    // MARK: - ExchangeApi

    func marketBuy(crypto: String, fiat: String, fiatAmount: Decimal) async -> DcaResult {
        do {
            let symbol = "\(crypto)-\(fiat)"
            let clientOid = UUID().uuidString

            let bodyDict: [String: Any] = [
                "clientOid": clientOid,
                "side": "buy",
                "symbol": symbol,
                "type": "market",
                "funds": formatDecimal(fiatAmount, scale: 2)
            ]

            let bodyData = try JSONSerialization.data(withJSONObject: bodyDict)
            let bodyString = String(data: bodyData, encoding: .utf8) ?? ""

            let path = "/api/v1/orders"
            let headers = authHeaders(method: "POST", path: path, body: bodyString)

            let (data, _) = try await client.postJsonRaw(
                url: "\(baseUrl)\(path)",
                body: bodyString,
                headers: headers
            )
            let json = try parseJson(data)

            let code = json["code"] as? String ?? ""
            if code != "200000" {
                let errorMessage = json["msg"] as? String ?? "Unknown error"
                return .error(message: errorMessage, retryable: false)
            }

            guard let responseData = json["data"] as? [String: Any] else {
                return .error(message: "Missing data in response", retryable: false)
            }

            return .success(Transaction(
                planId: 0,
                exchange: .kucoin,
                crypto: crypto,
                fiat: fiat,
                fiatAmount: fiatAmount,
                cryptoAmount: 0,
                price: 0,
                fee: 0,
                status: .pending,
                exchangeOrderId: responseData["orderId"] as? String
            ))
        } catch let error as NetworkError where error.isRetryable {
            return .error(message: error.localizedDescription, retryable: true)
        } catch {
            return .error(message: error.localizedDescription, retryable: false)
        }
    }

    func getBalance(currency: String) async -> Decimal? {
        nil
    }

    func getCurrentPrice(crypto: String, fiat: String) async -> Decimal? {
        nil
    }

    func withdraw(crypto: String, amount: Decimal, address: String) async throws -> String {
        throw ExchangeError.unsupportedOperation("KuCoin withdrawal is not yet implemented")
    }

    func getWithdrawalFee(crypto: String) async -> Decimal? {
        nil
    }

    func validateCredentials() async throws -> Bool {
        let path = "/api/v1/accounts"
        let headers = authHeaders(method: "GET", path: path)

        let (data, _) = try await client.get(
            url: "\(baseUrl)\(path)",
            headers: headers
        )
        let json = try parseJson(data)
        return (json["code"] as? String) == "200000"
    }

}
