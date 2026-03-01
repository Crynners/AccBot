import Foundation

/// Kraken API implementation.
/// Note: Kraken only has futures demo, no spot sandbox.
///
/// Auth: HMAC-SHA512 with base64-decoded secret.
/// Message = path + SHA256(nonce + postData), result base64-encoded.
/// Headers: API-Key, API-Sign.
final class KrakenApi: ExchangeApi {
    let exchange = Exchange.kraken

    private let credentials: ExchangeCredentials
    private let isSandbox: Bool
    private let client: NetworkClient
    private let baseUrl: String

    init(credentials: ExchangeCredentials, isSandbox: Bool, client: NetworkClient) {
        self.credentials = credentials
        self.isSandbox = isSandbox
        self.client = client
        self.baseUrl = ExchangeConfig.baseUrl(for: .kraken, isSandbox: isSandbox)
    }

    // MARK: - Symbol Mapping

    /// Map standard crypto/fiat codes to Kraken pair format
    private func mapPair(crypto: String, fiat: String) -> String {
        let krakenCrypto = crypto == "BTC" ? "XBT" : crypto
        return "\(krakenCrypto)\(fiat)"
    }

    /// Map Kraken asset codes back to standard codes
    private func mapAssetCode(_ krakenCode: String) -> String {
        switch krakenCode {
        case "ZEUR": return "EUR"
        case "ZUSD": return "USD"
        case "ZGBP": return "GBP"
        case "ZCAD": return "CAD"
        case "ZJPY": return "JPY"
        case "XXBT": return "BTC"
        case "XETH": return "ETH"
        case "XLTC": return "LTC"
        case "XXRP": return "XRP"
        case "XXLM": return "XLM"
        default: return krakenCode
        }
    }

    /// Map standard crypto code to Kraken asset code for withdrawals
    private func mapToKrakenAsset(_ crypto: String) -> String {
        crypto == "BTC" ? "XBT" : crypto
    }

    // MARK: - Authentication

    /// Create Kraken API signature.
    /// signature = HMAC-SHA512(base64decode(secret), path + SHA256(nonce + postData))
    private func createSignature(path: String, nonce: Int64, postData: String) -> String {
        let message = "\(nonce)\(postData)"
        let hash = CryptoUtils.sha256(Data(message.utf8))

        // Combine path bytes + SHA256 hash bytes
        var signatureMessage = Data(path.utf8)
        signatureMessage.append(hash)

        return CryptoUtils.hmacSha512Base64Secret(message: signatureMessage, base64Secret: credentials.apiSecret)
    }

    /// Execute an authenticated POST request to Kraken private API.
    /// Returns the raw response Data.
    /// Uses postFormRaw to ensure the exact body string matches the signature computation.
    private func executePrivateRequest(path: String, extraParams: String = "") async throws -> Data {
        let nonce = CryptoUtils.currentTimestampMs() * 1000
        let postData = extraParams.isEmpty ? "nonce=\(nonce)" : "nonce=\(nonce)&\(extraParams)"
        let signature = createSignature(path: path, nonce: nonce, postData: postData)

        let (data, _) = try await client.postFormRaw(
            url: "\(baseUrl)\(path)",
            body: postData,
            headers: [
                "API-Key": credentials.apiKey,
                "API-Sign": signature
            ]
        )
        return data
    }

    /// Check Kraken JSON response for errors. Returns error string or nil.
    private func checkKrakenErrors(_ json: [String: Any]) -> String? {
        guard let errors = json["error"] as? [String], !errors.isEmpty else {
            return nil
        }
        return errors[0]
    }

    // MARK: - ExchangeApi

    func marketBuy(crypto: String, fiat: String, fiatAmount: Decimal) async -> DcaResult {
        do {
            let pair = mapPair(crypto: crypto, fiat: fiat)
            let params = "ordertype=market&type=buy&pair=\(pair)&oflags=viqc&volume=\(decimalToPlainString(fiatAmount))"

            let data = try await executePrivateRequest(path: "/0/private/AddOrder", extraParams: params)
            let json = try parseJson(data)

            if let error = checkKrakenErrors(json) {
                let isRetryable = error.contains("EService:Unavailable") || error.contains("EGeneral:Temporary")
                return .error(message: error, retryable: isRetryable)
            }

            guard let result = json["result"] as? [String: Any] else {
                return .error(message: "Missing result in response", retryable: false)
            }

            let txIds = result["txid"] as? [String]
            let txId = txIds?.first

            // Query order to get fill details
            if let txId = txId {
                let fillDetails = await queryOrderFill(txId: txId)
                if let fill = fillDetails {
                    return .success(Transaction(
                        planId: 0,
                        exchange: .kraken,
                        crypto: crypto,
                        fiat: fiat,
                        fiatAmount: fill.cost,
                        cryptoAmount: fill.volume,
                        price: fill.price,
                        fee: fill.fee,
                        status: .completed,
                        exchangeOrderId: txId
                    ))
                }
            }

            // Fallback: order placed but fill details not yet available
            return .success(Transaction(
                planId: 0,
                exchange: .kraken,
                crypto: crypto,
                fiat: fiat,
                fiatAmount: fiatAmount,
                cryptoAmount: 0,
                price: 0,
                fee: 0,
                status: .pending,
                exchangeOrderId: txId
            ))
        } catch let error as NetworkError where error.isRetryable {
            return .error(message: error.localizedDescription, retryable: true)
        } catch {
            return .error(message: error.localizedDescription, retryable: false)
        }
    }

    /// Query order fill details with retry (market orders fill quickly)
    private func queryOrderFill(txId: String) async -> OrderFillDetails? {
        for attempt in 0..<3 {
            do {
                if attempt > 0 {
                    try? await Task.sleep(nanoseconds: 1_000_000_000) // 1s
                }

                let data = try await executePrivateRequest(path: "/0/private/QueryOrders", extraParams: "txid=\(txId)&trades=true")
                let json = try parseJson(data)

                if checkKrakenErrors(json) != nil { return nil }

                guard let result = json["result"] as? [String: Any],
                      let order = result[txId] as? [String: Any]
                else { return nil }

                let status = order["status"] as? String ?? ""

                if status == "closed" {
                    let volExec = jsonDecimal(order, key: "vol_exec")
                    let cost = jsonDecimal(order, key: "cost")
                    let fee = jsonDecimal(order, key: "fee")
                    let price = volExec > 0
                        ? roundDecimal(cost / volExec, scale: 8)
                        : Decimal.zero

                    return OrderFillDetails(volume: volExec, cost: cost, fee: fee, price: price)
                }
            } catch {
                // Continue retrying
            }
        }
        return nil
    }

    private struct OrderFillDetails {
        let volume: Decimal
        let cost: Decimal
        let fee: Decimal
        let price: Decimal
    }

    func getOrderStatus(orderId: String) async -> Transaction? {
        do {
            let data = try await executePrivateRequest(path: "/0/private/QueryOrders", extraParams: "txid=\(orderId)&trades=true")
            let json = try parseJson(data)

            if checkKrakenErrors(json) != nil { return nil }

            guard let result = json["result"] as? [String: Any],
                  let order = result[orderId] as? [String: Any]
            else { return nil }

            let status = order["status"] as? String ?? ""

            if status == "closed" {
                let volExec = jsonDecimal(order, key: "vol_exec")
                let cost = jsonDecimal(order, key: "cost")
                let fee = jsonDecimal(order, key: "fee")
                let price = volExec > 0
                    ? roundDecimal(cost / volExec, scale: 8)
                    : Decimal.zero

                return Transaction(
                    planId: 0,
                    exchange: .kraken,
                    crypto: "",
                    fiat: "",
                    fiatAmount: cost,
                    cryptoAmount: volExec,
                    price: price,
                    fee: fee,
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
            let data = try await executePrivateRequest(path: "/0/private/Balance")
            let json = try parseJson(data)

            if checkKrakenErrors(json) != nil { return nil }

            guard let result = json["result"] as? [String: Any] else { return nil }

            // Try direct match first, then try Kraken-prefixed codes
            for (key, _) in result {
                if mapAssetCode(key) == currency || key == currency {
                    return jsonDecimalOptional(result, key: key)
                }
            }
            return nil
        } catch {
            return nil
        }
    }

    func getCurrentPrice(crypto: String, fiat: String) async -> Decimal? {
        do {
            let pair = mapPair(crypto: crypto, fiat: fiat)
            let (data, _) = try await client.get(url: "\(baseUrl)/0/public/Ticker?pair=\(pair)")
            let json = try parseJson(data)

            if checkKrakenErrors(json) != nil { return nil }

            guard let result = json["result"] as? [String: Any] else { return nil }

            // Kraken returns the pair key which may differ from input
            guard let pairKey = result.keys.first,
                  let ticker = result[pairKey] as? [String: Any],
                  let lastTrade = ticker["c"] as? [String],
                  let lastPrice = lastTrade.first
            else { return nil }

            return Decimal(string: lastPrice)
        } catch {
            return nil
        }
    }

    func getTradeHistory(crypto: String, fiat: String, since: Date?, limit: Int) async throws -> TradeHistoryPage {
        let pair = mapPair(crypto: crypto, fiat: fiat)
        var params = "pair=\(pair)"
        if let since = since {
            let sinceEpoch = Int64(since.timeIntervalSince1970) + 1
            params += "&start=\(sinceEpoch)"
        }

        let data = try await executePrivateRequest(path: "/0/private/TradesHistory", extraParams: params)
        let json = try parseJson(data)

        if let error = checkKrakenErrors(json) {
            throw ExchangeError.apiError(error)
        }

        guard let result = json["result"] as? [String: Any],
              let tradesObj = result["trades"] as? [String: Any]
        else {
            throw ExchangeError.apiError("Invalid response format")
        }

        var trades: [HistoricalTrade] = []

        for (tradeId, tradeValue) in tradesObj {
            guard let trade = tradeValue as? [String: Any] else { continue }

            let type = jsonString(trade, key: "type")
            if type != "buy" { continue }

            let vol = jsonDecimal(trade, key: "vol")
            let cost = jsonDecimal(trade, key: "cost")
            let fee = jsonDecimal(trade, key: "fee")
            let price = jsonDecimal(trade, key: "price")
            let time = jsonDouble(trade, key: "time")

            let ordertxid = jsonString(trade, key: "ordertxid")
            trades.append(HistoricalTrade(
                orderId: ordertxid.isEmpty ? tradeId : ordertxid,
                timestamp: Date(timeIntervalSince1970: time),
                crypto: crypto,
                fiat: fiat,
                cryptoAmount: vol,
                fiatAmount: cost,
                price: price,
                fee: fee,
                feeAsset: fiat,
                side: "BUY"
            ))
        }

        // Sort by timestamp ascending
        trades.sort { $0.timestamp < $1.timestamp }

        return TradeHistoryPage(trades: trades, hasMore: trades.count >= limit)
    }

    func withdraw(crypto: String, amount: Decimal, address: String) async throws -> String {
        let asset = mapToKrakenAsset(crypto)
        let params = "asset=\(asset)&key=\(address)&amount=\(decimalToPlainString(amount))"

        let data = try await executePrivateRequest(path: "/0/private/Withdraw", extraParams: params)
        let json = try parseJson(data)

        if let error = checkKrakenErrors(json) {
            // Provide helpful message if Kraken rejects raw address
            if error.contains("EFunding:Unknown withdraw key") {
                throw ExchangeError.apiError(
                    "Kraken requires a pre-configured withdrawal address. " +
                    "Please add this address in your Kraken account settings first."
                )
            }
            throw ExchangeError.apiError(error)
        }

        guard let result = json["result"] as? [String: Any] else {
            throw ExchangeError.apiError("Invalid response")
        }
        return result["refid"] as? String ?? ""
    }

    func getWithdrawalFee(crypto: String) async -> Decimal? {
        nil
    }

    func validateCredentials() async throws -> Bool {
        let data = try await executePrivateRequest(path: "/0/private/Balance")
        let json = try parseJson(data)

        if let error = checkKrakenErrors(json) {
            throw ExchangeError.apiError(error)
        }
        return true
    }

}
