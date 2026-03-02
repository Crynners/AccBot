import Foundation

/// Coinbase Advanced Trade API implementation.
/// Uses legacy API keys with HMAC-SHA256 authentication.
///
/// Production: https://api.coinbase.com
/// Sandbox: https://api-public.sandbox.exchange.coinbase.com
///
/// Auth headers: CB-ACCESS-KEY, CB-ACCESS-SIGN, CB-ACCESS-TIMESTAMP, CB-ACCESS-PASSPHRASE
/// Prehash: timestamp + method + requestPath + body
/// Signature: HMAC-SHA256 with base64-decoded secret, result base64-encoded.
final class CoinbaseApi: ExchangeApi {
    let exchange = Exchange.coinbase

    private let credentials: ExchangeCredentials
    private let isSandbox: Bool
    private let client: NetworkClient
    private let baseUrl: String

    init(credentials: ExchangeCredentials, isSandbox: Bool, client: NetworkClient) {
        self.credentials = credentials
        self.isSandbox = isSandbox
        self.client = client
        self.baseUrl = ExchangeConfig.baseUrl(for: .coinbase, isSandbox: isSandbox)
    }

    // MARK: - Authentication

    /// Create HMAC-SHA256 signature for Coinbase API authentication.
    /// Prehash string: timestamp + method + requestPath + body
    private func sign(timestamp: String, method: String, path: String, body: String = "") -> String {
        let prehash = "\(timestamp)\(method)\(path)\(body)"
        return CryptoUtils.hmacSha256Base64Secret(message: prehash, base64Secret: credentials.apiSecret)
    }

    /// Build authenticated headers for a GET request
    private func getHeaders(path: String) -> [String: String] {
        let timestamp = "\(CryptoUtils.currentTimestampSec())"
        let signature = sign(timestamp: timestamp, method: "GET", path: path)
        return [
            "CB-ACCESS-KEY": credentials.apiKey,
            "CB-ACCESS-SIGN": signature,
            "CB-ACCESS-TIMESTAMP": timestamp,
            "CB-ACCESS-PASSPHRASE": credentials.passphrase ?? ""
        ]
    }

    /// Build authenticated headers for a POST request
    private func postHeaders(path: String, body: String) -> [String: String] {
        let timestamp = "\(CryptoUtils.currentTimestampSec())"
        let signature = sign(timestamp: timestamp, method: "POST", path: path, body: body)
        return [
            "CB-ACCESS-KEY": credentials.apiKey,
            "CB-ACCESS-SIGN": signature,
            "CB-ACCESS-TIMESTAMP": timestamp,
            "CB-ACCESS-PASSPHRASE": credentials.passphrase ?? ""
        ]
    }

    // MARK: - ExchangeApi

    func validateCredentials() async throws -> Bool {
        let path = "/api/v3/brokerage/accounts"
        let headers = getHeaders(path: path)

        let (data, _) = try await client.get(url: "\(baseUrl)\(path)", headers: headers)
        // If we get here without throwing, credentials are valid (NetworkClient throws on non-2xx)
        // Still check for error message in body
        if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
           let message = json["message"] as? String, !message.isEmpty {
            throw ExchangeError.apiError(message)
        }
        return true
    }

    func marketBuy(crypto: String, fiat: String, fiatAmount: Decimal) async -> DcaResult {
        do {
            let productId = "\(crypto)-\(fiat)"
            let clientOrderId = UUID().uuidString

            let bodyDict: [String: Any] = [
                "client_order_id": clientOrderId,
                "product_id": productId,
                "side": "BUY",
                "order_configuration": [
                    "market_market_ioc": [
                        "quote_size": formatDecimal(fiatAmount, scale: 2)
                    ]
                ]
            ]

            let bodyData = try JSONSerialization.data(withJSONObject: bodyDict)
            let bodyString = String(data: bodyData, encoding: .utf8) ?? ""

            let path = "/api/v3/brokerage/orders"
            let headers = postHeaders(path: path, body: bodyString)

            let (data, _) = try await client.postJsonRaw(
                url: "\(baseUrl)\(path)",
                body: bodyString,
                headers: headers
            )
            let json = try parseJson(data)

            // Check for order placement failure
            if let errorResponse = json["error_response"] as? [String: Any] {
                let error = errorResponse["error"] as? String ?? "Order failed"
                let message = errorResponse["message"] as? String ?? error
                return .error(message: message, retryable: false)
            }

            if let successResponse = json["success_response"] as? [String: Any] {
                let orderId = successResponse["order_id"] as? String ?? ""

                // Try to get fill details by querying the order
                let fillDetails = await queryOrderFill(orderId: orderId)
                if let fill = fillDetails {
                    return .success(Transaction(
                        planId: 0,
                        exchange: .coinbase,
                        crypto: crypto,
                        fiat: fiat,
                        fiatAmount: fill.cost,
                        cryptoAmount: fill.filledSize,
                        price: fill.avgPrice,
                        fee: fill.fee,
                        status: .completed,
                        exchangeOrderId: orderId
                    ))
                }

                // Order placed but details not yet available
                return .success(Transaction(
                    planId: 0,
                    exchange: .coinbase,
                    crypto: crypto,
                    fiat: fiat,
                    fiatAmount: fiatAmount,
                    cryptoAmount: 0,
                    price: 0,
                    fee: 0,
                    status: .pending,
                    exchangeOrderId: orderId
                ))
            }

            return .error(message: "Unexpected response format", retryable: false)
        } catch let error as NetworkError {
            if case .httpError(let code, let body) = error {
                let isRetryable = code >= 500 || code == 429
                let errorMsg: String
                if let jsonBody = try? JSONSerialization.jsonObject(with: Data(body.utf8)) as? [String: Any],
                   let message = jsonBody["message"] as? String, !message.isEmpty {
                    errorMsg = message
                } else {
                    errorMsg = "HTTP \(code)"
                }
                return .error(message: errorMsg, retryable: isRetryable)
            }
            return .error(message: error.localizedDescription, retryable: error.isRetryable)
        } catch {
            return .error(message: error.localizedDescription, retryable: false)
        }
    }

    /// Query order to get fill details, retry up to 3 times
    private func queryOrderFill(orderId: String) async -> FillDetails? {
        for attempt in 0..<3 {
            do {
                if attempt > 0 {
                    try? await Task.sleep(nanoseconds: 1_000_000_000) // 1s
                }

                let path = "/api/v3/brokerage/orders/historical/\(orderId)"
                let headers = getHeaders(path: path)
                let (data, _) = try await client.get(url: "\(baseUrl)\(path)", headers: headers)
                let json = try parseJson(data)

                guard let order = json["order"] as? [String: Any] else { return nil }
                let status = order["status"] as? String ?? ""

                if status == "FILLED" {
                    let filledSize = jsonDecimal(order, key: "filled_size")
                    let avgPrice = jsonDecimal(order, key: "average_filled_price")
                    let totalFees = jsonDecimal(order, key: "total_fees")
                    let cost = (filledSize > 0 && avgPrice > 0)
                        ? roundDecimal(filledSize * avgPrice, scale: 8)
                        : Decimal.zero

                    return FillDetails(filledSize: filledSize, cost: cost, avgPrice: avgPrice, fee: totalFees)
                }
            } catch {
                // Continue retrying
            }
        }
        return nil
    }

    private struct FillDetails {
        let filledSize: Decimal
        let cost: Decimal
        let avgPrice: Decimal
        let fee: Decimal
    }

    func getOrderStatus(orderId: String) async -> Transaction? {
        do {
            let path = "/api/v3/brokerage/orders/historical/\(orderId)"
            let headers = getHeaders(path: path)
            let (data, _) = try await client.get(url: "\(baseUrl)\(path)", headers: headers)
            let json = try parseJson(data)

            guard let order = json["order"] as? [String: Any] else { return nil }
            let status = order["status"] as? String ?? ""

            if status == "FILLED" {
                let filledSize = jsonDecimal(order, key: "filled_size")
                let avgPrice = jsonDecimal(order, key: "average_filled_price")
                let totalFees = jsonDecimal(order, key: "total_fees")
                let cost = (filledSize > 0 && avgPrice > 0)
                    ? roundDecimal(filledSize * avgPrice, scale: 8)
                    : Decimal.zero

                return Transaction(
                    planId: 0,
                    exchange: .coinbase,
                    crypto: "",
                    fiat: "",
                    fiatAmount: cost,
                    cryptoAmount: filledSize,
                    price: avgPrice,
                    fee: totalFees,
                    status: .completed,
                    exchangeOrderId: orderId
                )
            }
            return nil
        } catch {
            return nil
        }
    }

    func getBalance(currency: String) async -> Decimal? {
        do {
            let path = "/api/v3/brokerage/accounts"
            let headers = getHeaders(path: path)
            let (data, _) = try await client.get(url: "\(baseUrl)\(path)", headers: headers)
            let json = try parseJson(data)

            guard let accounts = json["accounts"] as? [[String: Any]] else { return nil }

            for account in accounts {
                if jsonString(account, key: "currency") == currency {
                    guard let availableBalance = account["available_balance"] as? [String: Any]
                    else { return nil }
                    return jsonDecimalOptional(availableBalance, key: "value")
                }
            }
            return nil
        } catch {
            return nil
        }
    }

    func getCurrentPrice(crypto: String, fiat: String) async -> Decimal? {
        do {
            let productId = "\(crypto)-\(fiat)"
            let path = "/api/v3/brokerage/products/\(productId)"
            let headers = getHeaders(path: path)
            let (data, _) = try await client.get(url: "\(baseUrl)\(path)", headers: headers)
            let json = try parseJson(data)
            return jsonDecimalOptional(json, key: "price")
        } catch {
            return nil
        }
    }

    func getTradeHistory(crypto: String, fiat: String, since: Date?, limit: Int) async throws -> TradeHistoryPage {
        let productId = "\(crypto)-\(fiat)"
        let path = "/api/v3/brokerage/orders/historical/fills?product_id=\(productId)&limit=\(limit)"
        let headers = getHeaders(path: path)

        let (data, _) = try await client.get(url: "\(baseUrl)\(path)", headers: headers)
        let json = try parseJson(data)

        let fills = json["fills"] as? [[String: Any]] ?? []
        let cursor = json["cursor"] as? String ?? ""
        var trades: [HistoricalTrade] = []

        let iso8601Formatter = ISO8601DateFormatter()
        iso8601Formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]

        for fill in fills {
            let side = jsonString(fill, key: "side")
            if side != "BUY" { continue }

            let size = jsonDecimal(fill, key: "size")
            let price = jsonDecimal(fill, key: "price")
            let commission = jsonDecimal(fill, key: "commission")
            let tradeTime = jsonString(fill, key: "trade_time")

            let timestamp = iso8601Formatter.date(from: tradeTime) ?? Date()

            // Filter locally by sinceTimestamp
            if let since = since, timestamp <= since { continue }

            trades.append(HistoricalTrade(
                orderId: { let tid = jsonString(fill, key: "trade_id"); return tid.isEmpty ? jsonString(fill, key: "order_id") : tid }(),
                timestamp: timestamp,
                crypto: crypto,
                fiat: fiat,
                cryptoAmount: size,
                fiatAmount: roundDecimal(size * price, scale: 8),
                price: price,
                fee: commission,
                feeAsset: fiat,
                side: "BUY"
            ))
        }

        return TradeHistoryPage(
            trades: trades,
            hasMore: !cursor.isEmpty && fills.count >= limit
        )
    }

    func withdraw(crypto: String, amount: Decimal, address: String) async throws -> String {
        throw ExchangeError.unsupportedOperation(
            "Coinbase Advanced Trade API does not support withdrawals. " +
            "Please use the Coinbase app or website for withdrawals."
        )
    }

    func getWithdrawalFee(crypto: String) async -> Decimal? {
        nil
    }

}
