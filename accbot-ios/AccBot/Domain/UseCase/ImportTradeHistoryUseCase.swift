import Foundation
import os

enum ApiImportProgress {
    case fetching(page: Int, totalFetched: Int)
    case deduplicating(count: Int)
    case importing(newCount: Int)
    case complete(imported: Int, skipped: Int)
    case error(String)
}

/// Imports completed transactions from exchange API with pagination and deduplication.
final class ImportTradeHistoryUseCase {
    private let transactionDao: TransactionDao
    private let logger = Logger(subsystem: "com.accbot.dca", category: "ImportTradeHistory")

    init(transactionDao: TransactionDao) {
        self.transactionDao = transactionDao
    }

    /// Import trade history from exchange API using AsyncStream for progress reporting.
    func importFromApi(
        api: ExchangeApi,
        planId: Int64,
        crypto: String,
        fiat: String,
        exchange: Exchange,
        sinceDate: Date? = nil
    ) -> AsyncStream<ApiImportProgress> {
        AsyncStream { continuation in
            Task {
                do {
                    // Get latest timestamp for incremental import
                    let latestDate = try transactionDao.getLatestTransactionTimestamp(planId)

                    var allTrades: [HistoricalTrade] = []
                    var cursor = sinceDate ?? latestDate
                    var page = 0
                    let maxPages = 10_000

                    repeat {
                        page += 1
                        continuation.yield(.fetching(page: page, totalFetched: allTrades.count))

                        let result = try await api.getTradeHistory(
                            crypto: crypto,
                            fiat: fiat,
                            since: cursor,
                            limit: 100
                        )

                        let buyTrades = result.trades.filter { $0.side == "BUY" }
                        allTrades.append(contentsOf: buyTrades)

                        let previousCursor = cursor
                        if let latest = result.trades.map(\.timestamp).max() {
                            cursor = latest
                        }

                        // Break if cursor didn't advance (prevents infinite loop on buggy APIs)
                        if cursor == previousCursor && result.hasMore {
                            self.logger.warning("Cursor stagnation detected at page \(page), breaking")
                            break
                        }

                        if !result.hasMore || page >= maxPages { break }
                    } while true

                    // Filter by sinceDate if provided
                    if let sinceDate = sinceDate {
                        allTrades.removeAll { $0.timestamp < sinceDate }
                    }

                    if allTrades.isEmpty {
                        continuation.yield(.complete(imported: 0, skipped: 0))
                        continuation.finish()
                        return
                    }

                    // Dedup
                    continuation.yield(.deduplicating(count: allTrades.count))
                    let existingIds = Set(try transactionDao.getExchangeOrderIdsByPlan(planId))
                    let newTrades = allTrades.filter { !existingIds.contains($0.orderId) }

                    if newTrades.isEmpty {
                        continuation.yield(.complete(imported: 0, skipped: allTrades.count))
                        continuation.finish()
                        return
                    }

                    continuation.yield(.importing(newCount: newTrades.count))

                    let transactions = newTrades.map { trade in
                        Transaction(
                            planId: planId,
                            exchange: exchange,
                            crypto: trade.crypto,
                            fiat: trade.fiat,
                            fiatAmount: trade.fiatAmount,
                            cryptoAmount: trade.cryptoAmount,
                            price: trade.price,
                            fee: trade.fee,
                            feeAsset: trade.feeAsset,
                            status: .completed,
                            exchangeOrderId: trade.orderId,
                            executedAt: trade.timestamp
                        )
                    }

                    try transactionDao.insertBatch(transactions)

                    continuation.yield(.complete(
                        imported: newTrades.count,
                        skipped: allTrades.count - newTrades.count
                    ))
                } catch {
                    continuation.yield(.error(error.localizedDescription))
                }
                continuation.finish()
            }
        }
    }
}
