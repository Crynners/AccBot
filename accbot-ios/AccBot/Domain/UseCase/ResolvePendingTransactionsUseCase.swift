import Foundation
import os

/// Resolves PENDING transactions by querying exchange APIs for fill details.
///
/// When exchanges return PENDING status (fill details not available within the initial polling window),
/// the transaction is saved with cryptoAmount=0. This use case finds those transactions
/// and queries the exchange for actual fill details, updating them to COMPLETED.
final class ResolvePendingTransactionsUseCase {
    private let database: DcaDatabase
    private let credentialsStore: CredentialsStore
    private let exchangeApiFactory: ExchangeApiFactory
    private let userPreferences: UserPreferences
    private let logger = Logger(subsystem: "com.accbot.dca", category: "ResolvePendingTx")

    init(
        database: DcaDatabase,
        credentialsStore: CredentialsStore,
        exchangeApiFactory: ExchangeApiFactory,
        userPreferences: UserPreferences
    ) {
        self.database = database
        self.credentialsStore = credentialsStore
        self.exchangeApiFactory = exchangeApiFactory
        self.userPreferences = userPreferences
    }

    /// Resolve all pending transactions. Returns the number of resolved transactions.
    @discardableResult
    func invoke() async -> Int {
        do {
            let pendingTxs = try database.transactionDao.getPendingTransactions()
                .filter { $0.exchangeOrderId != nil }

            guard !pendingTxs.isEmpty else { return 0 }

            let isSandbox = userPreferences.isSandboxMode()
            var resolvedCount = 0

            for tx in pendingTxs {
                guard let orderId = tx.exchangeOrderId else { continue }
                guard let credentials = credentialsStore.get(for: tx.exchange, isSandbox: isSandbox) else { continue }

                do {
                    let api = exchangeApiFactory.create(credentials: credentials)
                    guard let filled = await api.getOrderStatus(orderId: orderId) else { continue }

                    let updated = Transaction(
                        id: tx.id,
                        planId: tx.planId,
                        exchange: tx.exchange,
                        crypto: tx.crypto,
                        fiat: tx.fiat,
                        fiatAmount: filled.fiatAmount,
                        cryptoAmount: filled.cryptoAmount,
                        price: filled.price,
                        fee: filled.fee,
                        feeAsset: filled.feeAsset,
                        status: filled.status,
                        exchangeOrderId: tx.exchangeOrderId,
                        executedAt: tx.executedAt
                    )
                    try database.transactionDao.update(updated)
                    resolvedCount += 1

                    logger.info("Resolved pending tx \(tx.id): \(updated.cryptoAmount) \(tx.crypto) for \(updated.fiatAmount) \(tx.fiat)")
                } catch {
                    logger.warning("Failed to resolve pending tx \(tx.id): \(error.localizedDescription)")
                }
            }

            if resolvedCount > 0 {
                logger.info("Resolved \(resolvedCount)/\(pendingTxs.count) pending transactions")
            }

            return resolvedCount
        } catch {
            logger.warning("Failed to fetch pending transactions: \(error.localizedDescription)")
            return 0
        }
    }
}
