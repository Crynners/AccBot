import Foundation

/// Binance API implementation.
/// Supports sandbox mode via testnet.binance.vision.
final class BinanceApi: ExchangeApi {
    let exchange = Exchange.binance

    private let credentials: ExchangeCredentials
    private let client: NetworkClient
    private let baseUrl: String

    /// Offset in ms: serverTime - localTime
    private var timeOffset: Int64 = 0
    private var timeSynced = false

    /// Actor-based guard to ensure only one time sync runs at a time (NSLock is unsafe across await).
    private actor TimeSyncGuard {
        private var synced = false

        func markSynced() { synced = true }
        func isSynced() -> Bool { synced }
    }
    private let syncGuard = TimeSyncGuard()

    init(credentials: ExchangeCredentials, baseUrl: String, client: NetworkClient) {
        self.credentials = credentials
        self.client = client
        self.baseUrl = baseUrl
    }

    // MARK: - Time Sync

    private func ensureTimeSynced() async {
        let alreadySynced = await syncGuard.isSynced()
        guard !alreadySynced else { return }
        await syncServerTime()
        if timeOffset == 0 {
            try? await Task.sleep(nanoseconds: 500_000_000)
            await syncServerTime()
        }
        timeSynced = true
        await syncGuard.markSynced()
    }

    private func syncServerTime() async {
        do {
            let localBefore = CryptoUtils.currentTimestampMs()
            let (data, _) = try await client.get(url: "\(baseUrl)/api/v3/time")
            let localAfter = CryptoUtils.currentTimestampMs()

            let json = try JSONSerialization.jsonObject(with: data) as? [String: Any]
            guard let serverTime = json?["serverTime"] as? Int64 else { return }
            let localTime = (localBefore + localAfter) / 2
            timeOffset = serverTime - localTime
        } catch {
            // Time sync failed, will use local time
        }
    }

    private func serverTimestamp() -> Int64 {
        CryptoUtils.currentTimestampMs() + timeOffset
    }

    // MARK: - ExchangeApi

    func marketBuy(crypto: String, fiat: String, fiatAmount: Decimal) async -> DcaResult {
        do {
            await ensureTimeSynced()
            let symbol = "\(crypto)\(fiat)"
            let timestamp = serverTimestamp()

            let params = "symbol=\(symbol)&side=BUY&type=MARKET&quoteOrderQty=\(formatDecimal(fiatAmount, scale: 2))&timestamp=\(timestamp)&recvWindow=60000"
            let signature = CryptoUtils.hmacSha256Hex(message: params, secret: credentials.apiSecret)
            let signedUrl = "\(baseUrl)/api/v3/order?\(params)&signature=\(signature)"

            let (data, _) = try await client.postJson(
                url: signedUrl,
                body: [:],
                headers: ["X-MBX-APIKEY": credentials.apiKey]
            )
            let json = try parseJson(data)

            if let code = json["code"] as? Int {
                let msg = json["msg"] as? String ?? "Error code \(code)"
                return orderError(code: code, message: msg)
            }

            return .success(orderTransaction(json, crypto: crypto, fiat: fiat, fills: json["fills"] as? [[String: Any]] ?? []))
        } catch let error as NetworkError {
            if case .httpError(let status, let body) = error, (400..<500).contains(status),
               let json = try? parseJson(Data(body.utf8)), let code = json["code"] as? Int {
                return orderError(code: code, message: json["msg"] as? String ?? "Error code \(code)")
            }
            return .error(message: error.localizedDescription, retryable: error.isRetryable)
        } catch {
            return .error(message: error.localizedDescription, retryable: false)
        }
    }

    func getOrderStatus(orderId: String, crypto: String, fiat: String) async -> Transaction? {
        guard let id = Int64(orderId), id > 0 else { return nil }
        do {
            await ensureTimeSynced()
            let symbol = "\(crypto)\(fiat)"
            let parameters = "symbol=\(symbol)&orderId=\(id)&timestamp=\(serverTimestamp())&recvWindow=60000"
            let signature = CryptoUtils.hmacSha256Hex(message: parameters, secret: credentials.apiSecret)
            let headers = ["X-MBX-APIKEY": credentials.apiKey]
            let (data, _) = try await client.get(url: "\(baseUrl)/api/v3/order?\(parameters)&signature=\(signature)", headers: headers)
            let order = try parseJson(data)
            guard jsonInt64(order, key: "orderId") == id,
                  jsonString(order, key: "symbol") == symbol,
                  jsonString(order, key: "side") == "BUY" else { return nil }

            var fills: [[String: Any]] = []
            let status = orderStatus(order)
            if status == .completed || status == .partial {
                let tradeParams = "symbol=\(symbol)&orderId=\(id)&limit=1000&timestamp=\(serverTimestamp())&recvWindow=60000"
                let tradeSignature = CryptoUtils.hmacSha256Hex(message: tradeParams, secret: credentials.apiSecret)
                let (tradeData, _) = try await client.get(
                    url: "\(baseUrl)/api/v3/myTrades?\(tradeParams)&signature=\(tradeSignature)", headers: headers
                )
                guard let trades = try JSONSerialization.jsonObject(with: tradeData) as? [[String: Any]],
                      !trades.isEmpty else { return nil }
                fills = trades
            }
            return orderTransaction(order, crypto: crypto, fiat: fiat, fills: fills)
        } catch {
            return nil
        }
    }

    private func orderError(code: Int, message: String) -> DcaResult {
        switch code {
        case -1013, -1021, -1022, -1121, -2010, -2014, -2015:
            return .rejected(message: message)
        default:
            return .error(message: message, retryable: false)
        }
    }

    private func orderStatus(_ json: [String: Any]) -> TransactionStatus {
        switch jsonString(json, key: "status") {
        case "FILLED": return .completed
        case "CANCELED", "EXPIRED", "EXPIRED_IN_MATCH", "REJECTED":
            guard let quantity = jsonDecimalOptional(json, key: "executedQty") else { return .pending }
            return quantity > 0 ? .partial : .failed
        default: return .pending
        }
    }

    private func orderTransaction(_ json: [String: Any], crypto: String, fiat: String, fills: [[String: Any]]) -> Transaction {
        let quantity = jsonDecimal(json, key: "executedQty")
        let cost = jsonDecimal(json, key: "cummulativeQuoteQty")
        let status = orderStatus(json)
        let orderTime = max(jsonInt64(json, key: "transactTime"), jsonInt64(json, key: "time"))
        let orderId = jsonInt64(json, key: "orderId")
        return Transaction(
            planId: 0, exchange: .binance, crypto: crypto, fiat: fiat,
            fiatAmount: cost, cryptoAmount: quantity, price: quantity > 0 ? cost / quantity : 0,
            fee: fills.reduce(0) { $0 + jsonDecimal($1, key: "commission") },
            feeAsset: fills.first.map { jsonString($0, key: "commissionAsset") } ?? "", status: status,
            exchangeOrderId: orderId > 0 ? String(orderId) : nil,
            errorMessage: status == .failed ? String(localized: "The order ended without a fill.") : nil,
            executedAt: Date(timeIntervalSince1970: Double(orderTime) / 1000)
        )
    }

    func getBalance(currency: String) async -> Decimal? {
        do {
            await ensureTimeSynced()
            let timestamp = serverTimestamp()
            let params = "timestamp=\(timestamp)&recvWindow=60000"
            let signature = CryptoUtils.hmacSha256Hex(message: params, secret: credentials.apiSecret)

            let (data, _) = try await client.get(
                url: "\(baseUrl)/api/v3/account?\(params)&signature=\(signature)",
                headers: ["X-MBX-APIKEY": credentials.apiKey]
            )
            let json = try parseJson(data)

            if json["code"] != nil { return nil }

            guard let balances = json["balances"] as? [[String: Any]] else { return nil }
            for balance in balances {
                if jsonString(balance, key: "asset") == currency {
                    return jsonDecimalOptional(balance, key: "free")
                }
            }
            return nil
        } catch {
            return nil
        }
    }

    func getCurrentPrice(crypto: String, fiat: String) async -> Decimal? {
        do {
            let symbol = "\(crypto)\(fiat)"
            let (data, _) = try await client.get(url: "\(baseUrl)/api/v3/ticker/price?symbol=\(symbol)")
            let json = try parseJson(data)
            return jsonDecimalOptional(json, key: "price")
        } catch {
            return nil
        }
    }

    func withdraw(crypto: String, amount: Decimal, address: String) async throws -> String {
        await ensureTimeSynced()
        let timestamp = serverTimestamp()
        let params = "coin=\(crypto)&address=\(address)&amount=\(amount)&timestamp=\(timestamp)&recvWindow=60000"
        let signature = CryptoUtils.hmacSha256Hex(message: params, secret: credentials.apiSecret)

        let (data, _) = try await client.postJson(
            url: "\(baseUrl)/sapi/v1/capital/withdraw/apply?\(params)&signature=\(signature)",
            body: [:],
            headers: ["X-MBX-APIKEY": credentials.apiKey]
        )
        let json = try parseJson(data)

        if let code = json["code"] {
            let msg = json["msg"] as? String ?? "Error \(code)"
            throw ExchangeError.apiError(msg)
        }

        return json["id"] as? String ?? ""
    }

    func getWithdrawalFee(crypto: String) async -> Decimal? {
        do {
            await ensureTimeSynced()
            let timestamp = serverTimestamp()
            let params = "timestamp=\(timestamp)&recvWindow=60000"
            let signature = CryptoUtils.hmacSha256Hex(message: params, secret: credentials.apiSecret)

            let (data, _) = try await client.get(
                url: "\(baseUrl)/sapi/v1/capital/config/getall?\(params)&signature=\(signature)",
                headers: ["X-MBX-APIKEY": credentials.apiKey]
            )
            guard let jsonArray = try JSONSerialization.jsonObject(with: data) as? [[String: Any]] else { return nil }

            for coin in jsonArray {
                if coin["coin"] as? String == crypto {
                    guard let networks = coin["networkList"] as? [[String: Any]],
                          let first = networks.first,
                          let feeStr = first["withdrawFee"] as? String
                    else { return nil }
                    return Decimal(string: feeStr)
                }
            }
            return nil
        } catch {
            return nil
        }
    }

    func validateCredentials() async throws -> Bool {
        await ensureTimeSynced()
        let timestamp = serverTimestamp()
        let params = "timestamp=\(timestamp)&recvWindow=60000"
        let signature = CryptoUtils.hmacSha256Hex(message: params, secret: credentials.apiSecret)

        let (data, _) = try await client.get(
            url: "\(baseUrl)/api/v3/account?\(params)&signature=\(signature)",
            headers: ["X-MBX-APIKEY": credentials.apiKey]
        )
        let json = try parseJson(data)

        if let code = json["code"] {
            let msg = json["msg"] as? String ?? "Error \(code)"
            throw ExchangeError.apiError(msg)
        }
        return true
    }

    /// Production reports key restrictions; the testnet only reports account-level flags.
    /// A restriction flag that is missing counts as granted.
    func getKeyPermissions() async throws -> ApiKeyPermissions? {
        await ensureTimeSynced()
        let params = "timestamp=\(serverTimestamp())&recvWindow=60000"
        let signature = CryptoUtils.hmacSha256Hex(message: params, secret: credentials.apiSecret)
        let isTestnet = baseUrl == ExchangeConfig.binanceSandboxUrl
        let path = isTestnet ? "/api/v3/account" : "/sapi/v1/account/apiRestrictions"
        let (data, _) = try await client.get(
            url: "\(baseUrl)\(path)?\(params)&signature=\(signature)",
            headers: ["X-MBX-APIKEY": credentials.apiKey]
        )
        let json = try parseJson(data)
        if let code = json["code"] {
            throw ExchangeError.apiError(json["msg"] as? String ?? "Error \(code)")
        }
        func granted(_ key: String) -> Bool { (json[key] as? Bool) ?? true }
        if isTestnet {
            return ApiKeyPermissions(canTrade: granted("canTrade"), canWithdraw: granted("canWithdraw"))
        }
        return ApiKeyPermissions(
            canTrade: ["enableSpotAndMarginTrading", "enableMargin", "enableFutures",
                       "enableVanillaOptions", "enablePortfolioMarginTrading"].contains(where: granted),
            canWithdraw: ["enableWithdrawals", "enableInternalTransfer", "permitsUniversalTransfer"].contains(where: granted)
        )
    }

    func getTradeHistory(crypto: String, fiat: String, since: Date?, limit: Int) async throws -> TradeHistoryPage {
        await ensureTimeSynced()
        let symbol = "\(crypto)\(fiat)"
        let timestamp = serverTimestamp()

        var params = "symbol=\(symbol)&limit=\(limit)"
        if let since = since {
            let sinceMs = Int64(since.timeIntervalSince1970 * 1000) + 1
            params += "&startTime=\(sinceMs)"
        }
        params += "&timestamp=\(timestamp)&recvWindow=60000"

        let signature = CryptoUtils.hmacSha256Hex(message: params, secret: credentials.apiSecret)

        let (data, _) = try await client.get(
            url: "\(baseUrl)/api/v3/myTrades?\(params)&signature=\(signature)",
            headers: ["X-MBX-APIKEY": credentials.apiKey]
        )

        // Check for error object response
        if let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
           json["code"] != nil {
            let msg = json["msg"] as? String ?? "Failed to fetch trade history"
            throw ExchangeError.apiError(msg)
        }

        guard let jsonArray = try JSONSerialization.jsonObject(with: data) as? [[String: Any]] else {
            throw ExchangeError.apiError("Invalid response format")
        }

        var trades: [HistoricalTrade] = []
        for trade in jsonArray {
            let isBuyer = trade["isBuyer"] as? Bool ?? false
            let qty = jsonDecimal(trade, key: "qty")
            let price = jsonDecimal(trade, key: "price")
            let quoteQty = jsonDecimal(trade, key: "quoteQty")
            let commission = jsonDecimal(trade, key: "commission")
            let commissionAsset = jsonString(trade, key: "commissionAsset")
            let tradeTime = jsonInt64(trade, key: "time")

            trades.append(HistoricalTrade(
                orderId: "\(trade["id"] ?? "")",
                timestamp: Date(timeIntervalSince1970: Double(tradeTime) / 1000.0),
                crypto: crypto,
                fiat: fiat,
                cryptoAmount: qty,
                fiatAmount: quoteQty,
                price: price,
                fee: commission,
                feeAsset: commissionAsset,
                side: isBuyer ? "BUY" : "SELL"
            ))
        }

        return TradeHistoryPage(trades: trades, hasMore: trades.count >= limit)
    }

}
