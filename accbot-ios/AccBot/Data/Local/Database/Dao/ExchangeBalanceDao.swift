import Foundation
import GRDB

/// DAO for exchange_balances table
final class ExchangeBalanceDao {
    private let dbPool: DatabasePool

    init(dbPool: DatabasePool) {
        self.dbPool = dbPool
    }

    func getBalance(connectionId: Int64, currency: String) throws -> Decimal? {
        try dbPool.read { db in
            let record = try ExchangeBalanceRecord
                .filter(Column("connectionId") == connectionId)
                .filter(Column("currency") == currency)
                .fetchOne(db)
            return record.flatMap { Decimal(string: $0.balance) }
        }
    }

    func getBalancesByConnection(_ connectionId: Int64) throws -> [(currency: String, balance: Decimal)] {
        try dbPool.read { db in
            try ExchangeBalanceRecord
                .filter(Column("connectionId") == connectionId)
                .fetchAll(db)
                .compactMap { record in
                    guard let balance = Decimal(string: record.balance) else { return nil }
                    return (currency: record.currency, balance: balance)
                }
        }
    }

    /// Legacy: get balances by exchange enum (for backward compat during transition)
    func getBalancesByExchange(_ exchange: Exchange) throws -> [(currency: String, balance: Decimal)] {
        try dbPool.read { db in
            try ExchangeBalanceRecord
                .filter(Column("exchange") == exchange.rawValue)
                .fetchAll(db)
                .compactMap { record in
                    guard let balance = Decimal(string: record.balance) else { return nil }
                    return (currency: record.currency, balance: balance)
                }
        }
    }

    func upsert(connectionId: Int64, exchange: Exchange, currency: String, balance: Decimal) throws {
        try dbPool.write { db in
            let record = ExchangeBalanceRecord(
                connectionId: connectionId,
                currency: currency,
                exchange: exchange.rawValue,
                balance: "\(balance)",
                lastUpdated: Date().timeIntervalSince1970
            )
            try record.save(db)
        }
    }

    func deleteByConnection(_ connectionId: Int64) throws {
        try dbPool.write { db in
            _ = try ExchangeBalanceRecord
                .filter(Column("connectionId") == connectionId)
                .deleteAll(db)
        }
    }

    func deleteByExchange(_ exchange: Exchange) throws {
        try dbPool.write { db in
            _ = try ExchangeBalanceRecord
                .filter(Column("exchange") == exchange.rawValue)
                .deleteAll(db)
        }
    }

    func deleteAll() throws {
        try dbPool.write { db in
            _ = try ExchangeBalanceRecord.deleteAll(db)
        }
    }

    // MARK: - Legacy exchange-based API (for transitional compatibility)

    /// Legacy: get balance by exchange + currency. Uses first matching record.
    func getBalance(exchange: Exchange, currency: String) throws -> Decimal? {
        try dbPool.read { db in
            let record = try ExchangeBalanceRecord
                .filter(Column("exchange") == exchange.rawValue)
                .filter(Column("currency") == currency)
                .fetchOne(db)
            return record.flatMap { Decimal(string: $0.balance) }
        }
    }

    /// Legacy: upsert by exchange + currency. Uses exchange as both connection lookup and fallback.
    func upsert(exchange: Exchange, currency: String, balance: Decimal, using connectionDao: ExchangeConnectionDao) throws {
        let connectionId: Int64
        if let conn = try connectionDao.getDefaultByExchange(exchange) {
            connectionId = conn.id
        } else {
            connectionId = try connectionDao.insert(ExchangeConnection(exchange: exchange))
        }
        try upsert(connectionId: connectionId, exchange: exchange, currency: currency, balance: balance)
    }
}
