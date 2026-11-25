import Foundation

/// KuCoin API implementation.
///
/// Auth: HMAC-SHA256 with base64-decoded secret, base64-encoded result.
/// Prehash: timestamp + method + path + body
/// Passphrase is also HMAC-signed with the same secret (KC-API-KEY-VERSION: 2).
/// Headers: KC-API-KEY, KC-API-SIGN, KC-API-TIMESTAMP, KC-API-PASSPHRASE, KC-API-KEY-VERSION.
final class KuCoinApi: ExchangeApi {
    let exchange = Exchange.kucoin

    private let credentials: ExchangeCredentials
    private let client: NetworkClient
    private let baseUrl: String

    init(credentials: ExchangeCredentials, baseUrl: String, client: NetworkClient) {
        self.credentials = credentials
        self.client = client
        self.baseUrl = baseUrl
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

    func getOrderStatus(orderId: String, crypto: String, fiat: String) async -> Transaction? {
        guard !orderId.isEmpty,
              orderId.range(of: "[^A-Za-z0-9-]", options: .regularExpression) == nil else { return nil }

        do {
            let path = "/api/v1/orders/\(orderId)"
            let headers = authHeaders(method: "GET", path: path)
            let (data, _) = try await client.get(url: "\(baseUrl)\(path)", headers: headers)
            let json = try parseJson(data)
            guard jsonString(json, key: "code") == "200000",
                  let order = json["data"] as? [String: Any],
                  jsonString(order, key: "id") == orderId,
                  jsonString(order, key: "symbol") == "\(crypto)-\(fiat)",
                  jsonString(order, key: "side") == "buy",
                  (order["isActive"] as? Bool ?? order["active"] as? Bool) == false,
                  let quantity = jsonDecimalOptional(order, key: "dealSize"),
                  let cost = jsonDecimalOptional(order, key: "dealFunds") else { return nil }

            let status: TransactionStatus
            if quantity == 0 && cost == 0 {
                status = .failed
            } else if quantity > 0 && cost > 0 {
                status = order["cancelExist"] as? Bool == true ? .partial : .completed
            } else {
                return nil
            }
            return Transaction(
                planId: 0, exchange: .kucoin, crypto: crypto, fiat: fiat,
                fiatAmount: cost, cryptoAmount: quantity, price: quantity > 0 ? cost / quantity : 0,
                fee: jsonDecimal(order, key: "fee"), feeAsset: jsonString(order, key: "feeCurrency"),
                status: status, exchangeOrderId: orderId,
                errorMessage: status == .failed ? String(localized: "The order ended without a fill.") : nil,
                executedAt: Date(timeIntervalSince1970: Double(jsonInt64(order, key: "createdAt")) / 1000)
            )
        } catch {
            return nil
        }
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
