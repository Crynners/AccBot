import Foundation
import os

/// Two-phase daily price sync:
/// 1. Forward sync (CoinGecko): fills gap between latest cached date and today
/// 2. Historical backfill (CoinGecko market_chart/range): fetches older data backwards
///
/// Historical prices are immutable — once fetched, they never need re-fetching.
final class SyncDailyPricesUseCase {
    private let transactionDao: TransactionDao
    private let dailyPriceDao: DailyPriceDao
    private let marketDataService: MarketDataService
    private let logger = Logger(subsystem: "com.accbot.dca", category: "SyncDailyPrices")

    private let rateLimitDelay: UInt64 = 1_500_000_000 // 1.5s in nanoseconds

    init(transactionDao: TransactionDao, dailyPriceDao: DailyPriceDao, marketDataService: MarketDataService) {
        self.transactionDao = transactionDao
        self.dailyPriceDao = dailyPriceDao
        self.marketDataService = marketDataService
    }

    /// Sync daily prices for all crypto/fiat pairs with completed transactions.
    /// - Returns: number of pairs synced successfully
    func sync() async -> Int {
        do {
            let holdings = try transactionDao.getHoldingsByPair()
            guard !holdings.isEmpty else { return 0 }

            let todayEpoch = Self.epochDay(from: Date())
            var syncedCount = 0

            for holding in holdings {
                do {
                    let crypto = holding.crypto
                    let fiat = holding.fiat

                    // Compute desired start date from earliest transaction
                    let desiredStartDay: Int64
                    if let earliestDate = try transactionDao.getEarliestTransactionDate(crypto: crypto, fiat: fiat) {
                        let earliestDay = Self.epochDay(from: earliestDate)
                        let daysBack = max(30, min(3650, todayEpoch - earliestDay))
                        desiredStartDay = todayEpoch - daysBack
                    } else {
                        desiredStartDay = todayEpoch - 365
                    }

                    // Phase 1: Forward sync (always, fast)
                    let latestDay = try dailyPriceDao.getLatestDay(crypto: crypto, fiat: fiat)

                    if latestDay == nil {
                        // Brand new pair — bootstrap with last 365 days
                        logger.info("[\(crypto)/\(fiat)] Bootstrap: fetching last 365 days")
                        if let prices = await marketDataService.getDailyPrices(crypto: crypto, fiat: fiat, days: 365) {
                            try insertPrices(crypto: crypto, fiat: fiat, prices: prices)
                            logger.info("[\(crypto)/\(fiat)] Bootstrap: stored \(prices.count) days")
                        }
                        try await Task.sleep(nanoseconds: rateLimitDelay)
                    } else if let latest = latestDay, latest < todayEpoch - 1 {
                        // Fill forward gap
                        let gapDays = Int(todayEpoch - latest) + 1
                        logger.info("[\(crypto)/\(fiat)] Forward sync: fetching \(gapDays) days gap")
                        if let prices = await marketDataService.getDailyPrices(crypto: crypto, fiat: fiat, days: gapDays) {
                            try insertPrices(crypto: crypto, fiat: fiat, prices: prices)
                            logger.info("[\(crypto)/\(fiat)] Forward sync: stored \(prices.count) days")
                        }
                        try await Task.sleep(nanoseconds: rateLimitDelay)
                    }

                    // Phase 2: Historical backfill (one-time, backwards)
                    if let earliestCachedDay = try dailyPriceDao.getEarliestDay(crypto: crypto, fiat: fiat),
                       earliestCachedDay > desiredStartDay + 1 {
                        logger.info("[\(crypto)/\(fiat)] Backfill needed: earliest cached=\(earliestCachedDay), desired=\(desiredStartDay)")

                        var chunkEndDay = earliestCachedDay - 1
                        while chunkEndDay >= desiredStartDay {
                            let daysNeeded = Int(chunkEndDay - desiredStartDay) + 1
                            let limit = min(daysNeeded, 2000)

                            logger.info("[\(crypto)/\(fiat)] Backfill chunk: \(limit) days ending at \(chunkEndDay)")
                            if let prices = await marketDataService.getDailyPrices(crypto: crypto, fiat: fiat, days: limit) {
                                guard !prices.isEmpty else {
                                    logger.warning("[\(crypto)/\(fiat)] Backfill chunk: no data, stopping")
                                    break
                                }
                                try insertPrices(crypto: crypto, fiat: fiat, prices: prices)
                                let earliestFetched = prices.map { Self.epochDay(from: $0.date) }.min()!
                                chunkEndDay = earliestFetched - 1
                            } else {
                                break
                            }

                            try await Task.sleep(nanoseconds: rateLimitDelay)
                        }
                    }

                    syncedCount += 1
                } catch {
                    logger.error("Error syncing prices for \(holding.crypto)/\(holding.fiat): \(error.localizedDescription)")
                }
            }

            return syncedCount
        } catch {
            logger.error("Failed to get holdings: \(error.localizedDescription)")
            return 0
        }
    }

    private func insertPrices(crypto: String, fiat: String, prices: [(date: Date, price: Decimal)]) throws {
        let records = prices.map { (crypto: crypto, fiat: fiat, day: Self.epochDay(from: $0.date), price: $0.price) }
        try dailyPriceDao.insertBatch(records)
    }

    private static func epochDay(from date: Date) -> Int64 {
        Int64(date.timeIntervalSince1970 / 86400)
    }
}
