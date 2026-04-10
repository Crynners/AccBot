import Foundation
import Combine
import GRDB
import os

private let logger = Logger(subsystem: "com.accbot.dca", category: "ExchangeConnectionRepository")

/// Repository for exchange connections (envelopes). Wraps ExchangeConnectionDao with
/// convenience operations and side effects (cascade-clean credentials/balances on delete).
///
/// A "connection" is one set of API credentials targeting a specific Exchange. Multiple
/// connections can exist for the same exchange (e.g. two Coinmate sub-accounts named
/// "Hlavni" and "Sporeni"). Each connection has its own credentials, balance cache,
/// and withdrawal thresholds.
final class ExchangeConnectionRepository {
    private let connectionDao: ExchangeConnectionDao
    private let planDao: DcaPlanDao
    private let exchangeBalanceDao: ExchangeBalanceDao
    private let withdrawalThresholdDao: WithdrawalThresholdDao
    private let credentialsStore: CredentialsStore
    private let userPreferences: UserPreferences

    init(
        connectionDao: ExchangeConnectionDao,
        planDao: DcaPlanDao,
        exchangeBalanceDao: ExchangeBalanceDao,
        withdrawalThresholdDao: WithdrawalThresholdDao,
        credentialsStore: CredentialsStore,
        userPreferences: UserPreferences
    ) {
        self.connectionDao = connectionDao
        self.planDao = planDao
        self.exchangeBalanceDao = exchangeBalanceDao
        self.withdrawalThresholdDao = withdrawalThresholdDao
        self.credentialsStore = credentialsStore
        self.userPreferences = userPreferences
    }

    // MARK: - Queries

    func getAll() throws -> [ExchangeConnection] {
        try connectionDao.getAll()
    }

    func getById(_ id: Int64) throws -> ExchangeConnection? {
        try connectionDao.getById(id)
    }

    func getByExchange(_ exchange: Exchange) throws -> [ExchangeConnection] {
        try connectionDao.getByExchange(exchange)
    }

    func getDefaultByExchange(_ exchange: Exchange) throws -> ExchangeConnection? {
        try connectionDao.getDefaultByExchange(exchange)
    }

    func countByExchange(_ exchange: Exchange) throws -> Int {
        try connectionDao.countByExchange(exchange)
    }

    func observeAll() -> DatabasePublishers.Value<[ExchangeConnection]> {
        connectionDao.observeAll()
    }

    // MARK: - Mutations

    /// Create a new connection. Caller should validate name uniqueness for better UX.
    @discardableResult
    func create(exchange: Exchange, name: String) throws -> Int64 {
        try connectionDao.insert(ExchangeConnection(exchange: exchange, name: name))
    }

    func rename(connectionId: Int64, newName: String) throws {
        guard var connection = try connectionDao.getById(connectionId) else { return }
        // ExchangeConnection uses let, so recreate
        let updated = ExchangeConnection(
            id: connection.id,
            exchange: connection.exchange,
            name: newName,
            createdAt: connection.createdAt,
            displayOrder: connection.displayOrder
        )
        try connectionDao.update(updated)
    }

    /// Delete a connection and cascade to all dependent rows.
    /// Transaction history is NOT deleted - connectionId becomes orphaned (nullable).
    ///
    /// - Parameter deletePlans: if true, deletes DCA plans tied to this connection.
    ///   If false and plans still reference this connection, throws.
    func delete(connectionId: Int64, deletePlans: Bool) throws {
        let planCount = try planDao.countPlansByConnection(connectionId)
        if planCount > 0 && !deletePlans {
            throw ExchangeConnectionError.hasActivePlans(connectionId: connectionId, planCount: planCount)
        }

        let isSandbox = userPreferences.sandboxMode

        // Cascade delete in order
        if deletePlans && planCount > 0 {
            try planDao.deletePlansByConnection(connectionId)
        }
        try withdrawalThresholdDao.deleteByConnection(connectionId)
        try exchangeBalanceDao.deleteByConnection(connectionId)
        credentialsStore.delete(connectionId: connectionId, isSandbox: isSandbox)
        try connectionDao.deleteById(connectionId)

        logger.info("Deleted connection \(connectionId) (deletePlans=\(deletePlans), planCount=\(planCount))")
    }
}

enum ExchangeConnectionError: LocalizedError {
    case hasActivePlans(connectionId: Int64, planCount: Int)

    var errorDescription: String? {
        switch self {
        case .hasActivePlans(let id, let count):
            return "Cannot delete connection \(id): \(count) active plan(s) reference it."
        }
    }
}
