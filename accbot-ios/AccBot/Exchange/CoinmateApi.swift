import Foundation

/// Coinmate API implementation.
/// Czech exchange supporting CZK and EUR.
/// Note: Coinmate does not have a sandbox environment.
final class CoinmateApi: ExchangeApi {
    let exchange = Exchange.coinmate

    private let credentials: ExchangeCredentials
    private let isSandbox: Bool
    private let client: NetworkClient
    private let baseUrl: String
    private let clientId: String

    /// Coinmate taker fee: 0.35%
    private let takerFeeRate: Decimal = Decimal(string: "0.0035")!

    init?(credentials: ExchangeCredentials, isSandbox: Bool, client: NetworkClient) {
        guard let id = credentials.clientId else {
            assertionFailure("Coinmate requires clientId in credentials")
            return nil
        }
        self.credentials = credentials
        self.isSandbox = isSandbox
        self.client = client
        self.baseUrl = ExchangeConfig.baseUrl(for: .coinmate, isSandbox: isSandbox)
        self.clientId = id
    }

    func marketBuy(crypto: String, fiat: String, fiatAmount: Decimal) async -> DcaResult {
        do {
            let pair = "\(crypto)_\(fiat)"
            let nonce = CryptoUtils.currentTimestampMs()
            let signature = createSignature(nonce: nonce)

            let body: [String: String] = [
                "clientId": clientId,
                "publicKey": credentials.apiKey,
                "nonce": "\(nonce)",
                "signature": signature,
                "currencyPair": pair,
                "total": formatDecimal(fiatAmount, scale: 2),
            ]

            let (data, _) = try await client.postForm(url: "\(baseUrl)/buyInstant", body: body)
            let json = try parseJson(data)

            if json["error"] as? Bool == true {
                let errorMessage = json["errorMessage"] as? String ?? "Unknown error"
                return .error(message: errorMessage, retryable: false)
            }

            let orderId = "\(json["data"] ?? "")"

            // Query tradeHistory for real fill details
            let tradeDetails = await getTradeDetailsByOrderId(orderId, currencyPair: pair)

            let fee: Decimal
            let cryptoAmount: Decimal
            let fillingPrice: Decimal

            if let details = tradeDetails {
                cryptoAmount = details.totalAmount
                fee = details.totalFee
                fillingPrice = details.weightedAvgPrice
            } else {
                // Trade details not found — return as pending so the exact
                // amounts can be resolved later instead of guessing.
                guard let currentPrice = await getCurrentPrice(crypto: crypto, fiat: fiat),
                      currentPrice > 0 else {
                    return .success(Transaction(
                        planId: 0,
                        exchange: .coinmate,
                        crypto: crypto,
                        fiat: fiat,
                        fiatAmount: fiatAmount,
                        cryptoAmount: 0,
                        price: 0,
                        fee: 0,
                        feeAsset: fiat,
                        status: .pending,
                        exchangeOrderId: orderId
                    ))
                }
                fee = roundDecimal(fiatAmount * takerFeeRate, scale: 2)
                let netFiatAmount = fiatAmount - fee
                cryptoAmount = roundDecimal(netFiatAmount / currentPrice, scale: 8)
                fillingPrice = currentPrice
            }

            return .success(Transaction(
                planId: 0,
                exchange: .coinmate,
                crypto: crypto,
                fiat: fiat,
                fiatAmount: fiatAmount,
                cryptoAmount: cryptoAmount,
                price: fillingPrice,
                fee: fee,
                feeAsset: fiat,
                status: .completed,
                exchangeOrderId: orderId
            ))
        } catch let error as NetworkError where error.isRetryable {
            return .error(message: error.localizedDescription, retryable: true)
        } catch {
            return .error(message: error.localizedDescription, retryable: false)
        }
    }

    func getBalance(currency: String) async -> Decimal? {
        do {
            let nonce = CryptoUtils.currentTimestampMs()
            let signature = createSignature(nonce: nonce)

            let body: [String: String] = [
                "clientId": clientId,
                "publicKey": credentials.apiKey,
                "nonce": "\(nonce)",
                "signature": signature,
            ]

            let (data, _) = try await client.postForm(url: "\(baseUrl)/balances", body: body)
            let json = try parseJson(data)

            if json["error"] as? Bool == true { return nil }

            guard let dataObj = json["data"] as? [String: Any],
                  let currencyData = dataObj[currency] as? [String: Any]
            else { return nil }

            return jsonDecimalOptional(currencyData, key: "available")
        } catch {
            return nil
        }
    }

    func getCurrentPrice(crypto: String, fiat: String) async -> Decimal? {
        do {
            let pair = "\(crypto)_\(fiat)"
            let (data, _) = try await client.get(url: "\(baseUrl)/ticker?currencyPair=\(pair)")
            let json = try parseJson(data)

            if json["error"] as? Bool == true { return nil }

            guard let dataObj = json["data"] as? [String: Any]
            else { return nil }

            return jsonDecimalOptional(dataObj, key: "last")
        } catch {
            return nil
        }
    }

    func withdraw(crypto: String, amount: Decimal, address: String) async throws -> String {
        let nonce = CryptoUtils.currentTimestampMs()
        let signature = createSignature(nonce: nonce)

        let body: [String: String] = [
            "clientId": clientId,
            "publicKey": credentials.apiKey,
            "nonce": "\(nonce)",
            "signature": signature,
            "coinName": crypto,
            "amount": "\(amount)",
            "address": address,
        ]

        let endpoint = crypto.lowercased()
        let (data, _) = try await client.postForm(url: "\(baseUrl)/\(endpoint)Withdrawal", body: body)
        let json = try parseJson(data)

        if json["error"] as? Bool == true {
            let errorMessage = json["errorMessage"] as? String ?? "Withdrawal failed"
            throw ExchangeError.apiError(errorMessage)
        }

        guard let dataObj = json["data"] as? [String: Any] else {
            throw ExchangeError.apiError("Invalid response")
        }
        return dataObj["id"] as? String ?? ""
    }

    func getWithdrawalFee(crypto: String) async -> Decimal? {
        switch crypto {
        case "BTC": return Decimal(string: "0.0001")
        case "ETH": return Decimal(string: "0.001")
        case "LTC": return Decimal(string: "0.001")
        default: return nil
        }
    }

    func validateCredentials() async throws -> Bool {
        let nonce = CryptoUtils.currentTimestampMs()
        let signature = createSignature(nonce: nonce)

        let body: [String: String] = [
            "clientId": clientId,
            "publicKey": credentials.apiKey,
            "nonce": "\(nonce)",
            "signature": signature,
        ]

        let (data, _) = try await client.postForm(url: "\(baseUrl)/balances", body: body)
        let json = try parseJson(data)

        if json["error"] as? Bool == true {
            let errorMessage = json["errorMessage"] as? String ?? "Unknown error"
            throw ExchangeError.apiError(errorMessage)
        }

        return json["data"] != nil
    }

    func getOrderStatus(orderId: String) async -> Transaction? {
        nil
    }

    func getTradeHistory(crypto: String, fiat: String, since: Date?, limit: Int) async throws -> TradeHistoryPage {
        let pair = "\(crypto)_\(fiat)"
        let nonce = CryptoUtils.currentTimestampMs()
        let signature = createSignature(nonce: nonce)

        var body: [String: String] = [
            "clientId": clientId,
            "publicKey": credentials.apiKey,
            "nonce": "\(nonce)",
            "signature": signature,
            "currencyPair": pair,
            "limit": "\(limit)",
            "sort": "ASC",
        ]

        if let since = since {
            let sinceMs = Int64(since.timeIntervalSince1970 * 1000) + 1
            body["timestampFrom"] = "\(sinceMs)"
        }

        let (data, _) = try await client.postForm(url: "\(baseUrl)/tradeHistory", body: body)
        let json = try parseJson(data)

        if json["error"] as? Bool == true {
            let msg = json["errorMessage"] as? String ?? "Failed to fetch trade history"
            throw ExchangeError.apiError(msg)
        }

        guard let dataArray = json["data"] as? [[String: Any]] else {
            throw ExchangeError.apiError("No data in response")
        }

        var trades: [HistoricalTrade] = []
        for trade in dataArray {
            let tradeType = jsonString(trade, key: "type")
            let side = tradeType == "BUY" ? "BUY" : "SELL"
            let amount = jsonDecimal(trade, key: "amount")
            let price = jsonDecimal(trade, key: "price")
            let fee = jsonDecimal(trade, key: "fee")
            let timestampMs = jsonInt64(trade, key: "createdTimestamp")

            let orderId: String = {
                let oid = jsonString(trade, key: "orderId")
                return oid.isEmpty ? jsonString(trade, key: "transactionId") : oid
            }()

            trades.append(HistoricalTrade(
                orderId: orderId,
                timestamp: Date(timeIntervalSince1970: Double(timestampMs) / 1000.0),
                crypto: crypto,
                fiat: fiat,
                cryptoAmount: amount,
                fiatAmount: roundDecimal(amount * price, scale: 2),
                price: price,
                fee: fee,
                feeAsset: fiat,
                side: side
            ))
        }

        return TradeHistoryPage(trades: trades, hasMore: trades.count >= limit)
    }

    // MARK: - Private

    private struct TradeDetails {
        let totalAmount: Decimal
        let totalFee: Decimal
        let weightedAvgPrice: Decimal
    }

    private func getTradeDetailsByOrderId(_ orderId: String, currencyPair: String) async -> TradeDetails? {
        for attempt in 0...1 {
            if attempt > 0 {
                try? await Task.sleep(nanoseconds: 1_000_000_000) // 1s
            }

            do {
                let nonce = CryptoUtils.currentTimestampMs()
                let signature = createSignature(nonce: nonce)

                let body: [String: String] = [
                    "clientId": clientId,
                    "publicKey": credentials.apiKey,
                    "nonce": "\(nonce)",
                    "signature": signature,
                    "currencyPair": currencyPair,
                    "limit": "20",
                    "sort": "DESC",
                ]

                let (data, _) = try await client.postForm(url: "\(baseUrl)/tradeHistory", body: body)
                let json = try parseJson(data)

                if json["error"] as? Bool == true { continue }
                guard let dataArray = json["data"] as? [[String: Any]] else { continue }

                var totalAmount: Decimal = 0
                var totalFee: Decimal = 0
                var totalCost: Decimal = 0
                var found = false

                for trade in dataArray {
                    let tradeOrderId = jsonString(trade, key: "orderId")
                    if tradeOrderId == orderId {
                        found = true
                        let amount = jsonDecimal(trade, key: "amount")
                        let price = jsonDecimal(trade, key: "price")
                        let fee = jsonDecimal(trade, key: "fee")
                        totalAmount += amount
                        totalFee += fee
                        totalCost += amount * price
                    }
                }

                if found && totalAmount > 0 {
                    return TradeDetails(
                        totalAmount: totalAmount,
                        totalFee: roundDecimal(totalFee, scale: 2),
                        weightedAvgPrice: roundDecimal(totalCost / totalAmount, scale: 2)
                    )
                }
            } catch {
                continue
            }
        }
        return nil
    }

    private func createSignature(nonce: Int64) -> String {
        let message = "\(nonce)\(clientId)\(credentials.apiKey)"
        return CryptoUtils.hmacSha256Hex(message: message, secret: credentials.apiSecret).uppercased()
    }

}
