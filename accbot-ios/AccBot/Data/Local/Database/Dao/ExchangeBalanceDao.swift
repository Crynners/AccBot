import Foundation
import GRDB

/// DAO for exchange_balances table
final class ExchangeBalanceDao {
    private let dbPool: DatabasePool

    init(dbPool: DatabasePool) {
        self.dbPool = dbPool
    }

    func getBalance(exchange: Exchange, currency: String) throws -> Decimal? {
        try dbPool.read { db in
            let id = "\(exchange.rawValue)_\(currency)"
            let record = try ExchangeBalanceRecord.fetchOne(db, key: id)
            return record.flatMap { Decimal(string: $0.balance) }
        }
    }

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

    func upsert(exchange: Exchange, currency: String, balance: Decimal) throws {
        try dbPool.write { db in
            let record = ExchangeBalanceRecord(
                id: "\(exchange.rawValue)_\(currency)",
                exchange: exchange.rawValue,
                currency: currency,
                balance: "\(balance)",
                lastUpdated: Date().timeIntervalSince1970
            )
            try record.save(db)
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
}
