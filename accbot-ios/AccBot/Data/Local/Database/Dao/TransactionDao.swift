import Foundation
import GRDB
import Combine

/// DAO for transactions table
final class TransactionDao {
    private let dbPool: DatabasePool

    init(dbPool: DatabasePool) {
        self.dbPool = dbPool
    }

    // MARK: - Queries

    func getAll(limit: Int = 100, offset: Int = 0) throws -> [Transaction] {
        try dbPool.read { db in
            try TransactionRecord
                .order(Column("executedAt").desc)
                .limit(limit, offset: offset)
                .fetchAll(db)
                .map { $0.toDomain() }
        }
    }

    func getById(_ id: Int64) throws -> Transaction? {
        try dbPool.read { db in
            try TransactionRecord.fetchOne(db, key: id)?.toDomain()
        }
    }

    func getByPlanId(_ planId: Int64, limit: Int = 50) throws -> [Transaction] {
        try dbPool.read { db in
            try TransactionRecord
                .filter(Column("planId") == planId)
                .order(Column("executedAt").desc)
                .limit(limit)
                .fetchAll(db)
                .map { $0.toDomain() }
        }
    }

    func getPendingTransactions() throws -> [Transaction] {
        try dbPool.read { db in
            try TransactionRecord
                .filter(Column("status") == TransactionStatus.pending.rawValue)
                .fetchAll(db)
                .map { $0.toDomain() }
        }
    }

    func getFiltered(
        crypto: String? = nil,
        fiat: String? = nil,
        exchange: Exchange? = nil,
        status: TransactionStatus? = nil,
        from: Date? = nil,
        to: Date? = nil,
        limit: Int = 100,
        offset: Int = 0
    ) throws -> [Transaction] {
        try dbPool.read { db in
            var request = TransactionRecord.all()

            if let crypto = crypto {
                request = request.filter(Column("crypto") == crypto)
            }
            if let fiat = fiat {
                request = request.filter(Column("fiat") == fiat)
            }
            if let exchange = exchange {
                request = request.filter(Column("exchange") == exchange.rawValue)
            }
            if let status = status {
                request = request.filter(Column("status") == status.rawValue)
            }
            if let from = from {
                request = request.filter(Column("executedAt") >= from.timeIntervalSince1970)
            }
            if let to = to {
                request = request.filter(Column("executedAt") <= to.timeIntervalSince1970)
            }

            return try request
                .order(Column("executedAt").desc)
                .limit(limit, offset: offset)
                .fetchAll(db)
                .map { $0.toDomain() }
        }
    }

    func getCompletedTransactions(crypto: String, fiat: String) throws -> [Transaction] {
        try dbPool.read { db in
            try TransactionRecord
                .filter(Column("crypto") == crypto)
                .filter(Column("fiat") == fiat)
                .filter(Column("status") == TransactionStatus.completed.rawValue)
                .order(Column("executedAt").asc)
                .fetchAll(db)
                .map { $0.toDomain() }
        }
    }

    func getTotalCount() throws -> Int {
        try dbPool.read { db in
            try TransactionRecord.fetchCount(db)
        }
    }

    func getDistinctCryptos() throws -> [String] {
        try dbPool.read { db in
            let rows = try Row.fetchAll(db, sql: """
                SELECT DISTINCT crypto FROM transactions ORDER BY crypto
                """)
            return rows.map { $0["crypto"] }
        }
    }

    func getDistinctPairs() throws -> [(crypto: String, fiat: String)] {
        try dbPool.read { db in
            let rows = try Row.fetchAll(db, sql: """
                SELECT DISTINCT crypto, fiat FROM transactions
                WHERE status IN ('COMPLETED', 'PARTIAL')
                ORDER BY crypto, fiat
                """)
            return rows.map { (crypto: $0["crypto"], fiat: $0["fiat"]) }
        }
    }

    func getHoldingSummaries(fiat: String? = nil) throws -> [(crypto: String, fiat: String, totalCrypto: Decimal, totalInvested: Decimal, txCount: Int)] {
        try dbPool.read { db in
            // Fetch raw TEXT values and aggregate in Swift to avoid CAST(... AS REAL) precision loss
            var sql = """
                SELECT crypto, fiat, cryptoAmount, fiatAmount
                FROM transactions
                WHERE status IN ('COMPLETED', 'PARTIAL')
                """
            var args: [any DatabaseValueConvertible] = []
            if let fiat {
                sql += " AND fiat = ?"
                args.append(fiat)
            }
            sql += " ORDER BY crypto, fiat"
            let rows = try Row.fetchAll(db, sql: sql, arguments: StatementArguments(args))

            // Group and sum with Decimal precision
            var groups: [String: (crypto: String, fiat: String, totalCrypto: Decimal, totalInvested: Decimal, txCount: Int)] = [:]
            for row in rows {
                let crypto: String = row["crypto"]
                let fiat: String = row["fiat"]
                let key = "\(crypto)/\(fiat)"
                let cryptoAmount = Decimal(string: row["cryptoAmount"] as String? ?? "0") ?? 0
                let fiatAmount = Decimal(string: row["fiatAmount"] as String? ?? "0") ?? 0
                var entry = groups[key] ?? (crypto: crypto, fiat: fiat, totalCrypto: 0, totalInvested: 0, txCount: 0)
                entry.totalCrypto += cryptoAmount
                entry.totalInvested += fiatAmount
                entry.txCount += 1
                groups[key] = entry
            }
            return groups.values.sorted { "\($0.crypto)/\($0.fiat)" < "\($1.crypto)/\($1.fiat)" }
        }
    }

    func getAccumulatedCryptoByPlan(_ planId: Int64) throws -> Decimal {
        try dbPool.read { db in
            let rows = try Row.fetchAll(db, sql: """
                SELECT cryptoAmount FROM transactions
                WHERE planId = ? AND status = 'COMPLETED'
                """, arguments: [planId])
            return rows.reduce(Decimal.zero) { sum, row in
                sum + (Decimal(string: row["cryptoAmount"] as String? ?? "0") ?? 0)
            }
        }
    }

    // MARK: - Mutations

    @discardableResult
    func insert(_ transaction: Transaction) throws -> Int64 {
        try dbPool.write { db in
            let record = TransactionRecord.fromDomain(transaction)
            try record.insert(db)
            return db.lastInsertedRowID
        }
    }

    func update(_ transaction: Transaction) throws {
        try dbPool.write { db in
            let record = TransactionRecord.fromDomain(transaction)
            try record.update(db)
        }
    }

    func delete(id: Int64) throws {
        try dbPool.write { db in
            _ = try TransactionRecord.deleteOne(db, key: id)
        }
    }

    func deleteAll() throws {
        try dbPool.write { db in
            _ = try TransactionRecord.deleteAll(db)
        }
    }

    func deleteByPlanId(_ planId: Int64) throws {
        try dbPool.write { db in
            _ = try TransactionRecord
                .filter(Column("planId") == planId)
                .deleteAll(db)
        }
    }

    func getAllTransactionsOnce() throws -> [Transaction] {
        try dbPool.read { db in
            try TransactionRecord
                .order(Column("executedAt").desc)
                .fetchAll(db)
                .map { $0.toDomain() }
        }
    }

    func getExchangeOrderIdsByPlan(_ planId: Int64) throws -> [String] {
        try dbPool.read { db in
            let rows = try Row.fetchAll(db, sql: """
                SELECT exchangeOrderId FROM transactions
                WHERE planId = ? AND exchangeOrderId IS NOT NULL
                """, arguments: [planId])
            return rows.compactMap { $0["exchangeOrderId"] as String? }
        }
    }

    func getLatestTransactionTimestamp(_ planId: Int64) throws -> Date? {
        try dbPool.read { db in
            let row = try Row.fetchOne(db, sql: """
                SELECT MAX(executedAt) as maxDate FROM transactions WHERE planId = ?
                """, arguments: [planId])
            guard let epoch = row?["maxDate"] as? Double else { return nil }
            return Date(timeIntervalSince1970: epoch)
        }
    }

    func getHoldingsByPair() throws -> [(crypto: String, fiat: String)] {
        try dbPool.read { db in
            let rows = try Row.fetchAll(db, sql: """
                SELECT DISTINCT crypto, fiat FROM transactions
                WHERE status = 'COMPLETED'
                ORDER BY crypto, fiat
                """)
            return rows.map { (crypto: $0["crypto"], fiat: $0["fiat"]) }
        }
    }

    func getEarliestTransactionDate(crypto: String, fiat: String) throws -> Date? {
        try dbPool.read { db in
            let row = try Row.fetchOne(db, sql: """
                SELECT MIN(executedAt) as minDate FROM transactions
                WHERE crypto = ? AND fiat = ? AND status = 'COMPLETED'
                """, arguments: [crypto, fiat])
            guard let epoch = row?["minDate"] as? Double else { return nil }
            return Date(timeIntervalSince1970: epoch)
        }
    }

    @discardableResult
    func insertBatch(_ transactions: [Transaction]) throws -> Int {
        try dbPool.write { db in
            var count = 0
            for tx in transactions {
                var record = TransactionRecord.fromDomain(tx)
                try record.insert(db)
                count += 1
            }
            return count
        }
    }

    // MARK: - Observation

    func observeCount() -> DatabasePublishers.Value<Int> {
        ValueObservation.tracking { db in
            try TransactionRecord.fetchCount(db)
        }
        .publisher(in: dbPool, scheduling: .immediate)
    }

    func observeAll() -> DatabasePublishers.Value<[Transaction]> {
        ValueObservation.tracking { db in
            try TransactionRecord
                .order(Column("executedAt").desc)
                .limit(100)
                .fetchAll(db)
                .map { $0.toDomain() }
        }
        .publisher(in: dbPool, scheduling: .immediate)
    }

    func observeByPlanId(_ planId: Int64) -> DatabasePublishers.Value<[Transaction]> {
        ValueObservation.tracking { db in
            try TransactionRecord
                .filter(Column("planId") == planId)
                .order(Column("executedAt").desc)
                .fetchAll(db)
                .map { $0.toDomain() }
        }
        .publisher(in: dbPool, scheduling: .immediate)
    }
}
