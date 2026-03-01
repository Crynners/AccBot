import Foundation

/// CoinGecko + Fear & Greed Index API service.
/// Uses actor isolation for thread-safe cache access (called concurrently via withTaskGroup).
actor MarketDataService {
    private let client: NetworkClient
    private let coingeckoBase = "https://api.coingecko.com/api/v3"
    private let fearGreedBase = "https://api.alternative.me/fng"

    /// Cache ATH prices for 1 hour
    private var athCache: [String: (price: Decimal, fetchedAt: Date)] = [:]
    private let athCacheDuration: TimeInterval = 3600

    /// Cache Fear & Greed for 30 minutes
    private var fearGreedCache: (index: Int, fetchedAt: Date)?
    private let fgCacheDuration: TimeInterval = 1800

    init(client: NetworkClient) {
        self.client = client
    }

    // MARK: - CoinGecko Coin IDs

    private func coinGeckoId(for crypto: String) -> String {
        switch crypto.uppercased() {
        case "BTC": return "bitcoin"
        case "ETH": return "ethereum"
        case "SOL": return "solana"
        case "ADA": return "cardano"
        case "DOT": return "polkadot"
        case "LTC": return "litecoin"
        default: return crypto.lowercased()
        }
    }

    // MARK: - Current Price

    func getCurrentPrice(crypto: String, fiat: String) async -> Decimal? {
        do {
            let id = coinGeckoId(for: crypto)
            let fiatLower = fiat.lowercased()
            let (data, _) = try await client.get(
                url: "\(coingeckoBase)/simple/price?ids=\(id)&vs_currencies=\(fiatLower)"
            )
            guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let coinData = json[id] as? [String: Any],
                  let price = coinData[fiatLower] as? NSNumber
            else { return nil }
            return price.decimalValue
        } catch {
            return nil
        }
    }

    // MARK: - All-Time High

    func getAllTimeHigh(crypto: String, fiat: String) async -> Decimal? {
        let cacheKey = "\(crypto)_\(fiat)"

        // Check cache
        if let cached = athCache[cacheKey],
           Date().timeIntervalSince(cached.fetchedAt) < athCacheDuration {
            return cached.price
        }

        do {
            let id = coinGeckoId(for: crypto)
            let fiatLower = fiat.lowercased()
            let (data, _) = try await client.get(
                url: "\(coingeckoBase)/coins/\(id)?localization=false&tickers=false&community_data=false&developer_data=false"
            )
            guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let marketData = json["market_data"] as? [String: Any],
                  let athData = marketData["ath"] as? [String: Any],
                  let athValue = athData[fiatLower] as? NSNumber
            else { return nil }

            let price = athValue.decimalValue
            athCache[cacheKey] = (price: price, fetchedAt: Date())
            return price
        } catch {
            return nil
        }
    }

    // MARK: - Fear & Greed Index

    func getFearGreedIndex() async -> Int? {
        // Check cache
        if let cached = fearGreedCache,
           Date().timeIntervalSince(cached.fetchedAt) < fgCacheDuration {
            return cached.index
        }

        do {
            let (data, _) = try await client.get(url: "\(fearGreedBase)/?limit=1")
            guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let dataArray = json["data"] as? [[String: Any]],
                  let first = dataArray.first,
                  let valueStr = first["value"] as? String,
                  let value = Int(valueStr)
            else { return nil }

            fearGreedCache = (index: value, fetchedAt: Date())
            return value
        } catch {
            return nil
        }
    }

    // MARK: - Historical Daily Prices (for charts)

    func getDailyPrices(crypto: String, fiat: String, days: Int) async -> [(date: Date, price: Decimal)]? {
        do {
            let id = coinGeckoId(for: crypto)
            let fiatLower = fiat.lowercased()
            let (data, _) = try await client.get(
                url: "\(coingeckoBase)/coins/\(id)/market_chart?vs_currency=\(fiatLower)&days=\(days)&interval=daily"
            )
            guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let prices = json["prices"] as? [[Any]]
            else { return nil }

            return prices.compactMap { entry -> (date: Date, price: Decimal)? in
                guard entry.count >= 2,
                      let timestampMs = entry[0] as? Double,
                      let priceNum = entry[1] as? NSNumber
                else { return nil }
                return (
                    date: Date(timeIntervalSince1970: timestampMs / 1000),
                    price: priceNum.decimalValue
                )
            }
        } catch {
            return nil
        }
    }
}
