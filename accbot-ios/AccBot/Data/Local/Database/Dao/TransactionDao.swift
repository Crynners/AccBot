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

    func hasUnidentifiedPurchase(for planId: Int64) throws -> Bool {
        try dbPool.read { db in
            try TransactionRecord.filter(Column("planId") == planId)
                .filter(Column("status") == TransactionStatus.pending.rawValue)
                .filter(Column("exchangeOrderId") == nil || Column("exchangeOrderId") == "")
                .fetchCount(db) > 0
        }
    }

    /// Keep an exact confirmed trade from an import, or retain a failed attempt when
    /// the user verified independently that no order was placed.
    func resolvePurchaseReview(_ transaction: Transaction, confirmedOrderId: String?, confirmedPurchase: Transaction? = nil) throws {
        try dbPool.write { db in
            guard let pending = try TransactionRecord.fetchOne(db, key: transaction.id)?.toDomain(),
                  pending == transaction, pending.status == .pending else {
                throw PurchaseReviewError.noPendingPurchase
            }

            if let confirmedOrderId {
                guard pending.needsPurchaseReview || pending.exchangeOrderId == confirmedOrderId else {
                    throw PurchaseReviewError.noPendingPurchase
                }
                let earliest = pending.executedAt.addingTimeInterval(-60).timeIntervalSince1970
                let latest = pending.executedAt.addingTimeInterval(5 * 60).timeIntervalSince1970
                let recentPurchases = TransactionRecord.filter(Column("planId") == pending.planId)
                    .filter(Column("status") == TransactionStatus.completed.rawValue || Column("status") == TransactionStatus.partial.rawValue)
                    .filter(Column("executedAt") >= earliest && Column("executedAt") <= latest)
                if try recentPurchases.filter(Column("exchangeOrderId") == confirmedOrderId).fetchCount(db) > 0 {
                    _ = try TransactionRecord.deleteOne(db, key: pending.id)
                } else if let confirmedPurchase {
                    // Imports may identify individual fills separately from their order. Avoid
                    // recording a second purchase when nearby imported fills cannot be matched.
                    guard confirmedPurchase.id == pending.id, confirmedPurchase.planId == pending.planId,
                          confirmedPurchase.exchangeOrderId == confirmedOrderId,
                          (confirmedPurchase.status == .completed || confirmedPurchase.status == .partial),
                          try recentPurchases.fetchCount(db) == 0 else {
                        throw PurchaseReviewError.orderNotInHistory
                    }
                    let alreadyRecorded = try TransactionRecord.filter(Column("exchange") == pending.exchange.rawValue)
                        .filter(Column("crypto") == pending.crypto && Column("fiat") == pending.fiat)
                        .filter(Column("exchangeOrderId") == confirmedOrderId && Column("id") != pending.id)
                        .fetchCount(db) > 0
                    guard !alreadyRecorded else { throw PurchaseReviewError.orderNotInHistory }
                    try TransactionRecord.fromDomain(confirmedPurchase).update(db)
                } else {
                    throw PurchaseReviewError.orderNotInHistory
                }
            } else {
                try db.execute(
                    sql: "UPDATE transactions SET status = ?, errorMessage = ?, warningMessage = NULL WHERE id = ?",
                    arguments: [TransactionStatus.failed.rawValue, String(localized: "No fill was confirmed on the exchange."), pending.id]
                )
            }
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
            try Self.accumulatedCrypto(in: db, for: planId)
        }
    }

    static func accumulatedCrypto(in db: Database, for planId: Int64) throws -> Decimal {
        let rows = try Row.fetchAll(db, sql: """
            SELECT cryptoAmount FROM transactions
            WHERE planId = ? AND status IN ('COMPLETED', 'PARTIAL')
            """, arguments: [planId])
        return rows.reduce(Decimal.zero) { sum, row in
            sum + (Decimal(string: row["cryptoAmount"] as String? ?? "0") ?? 0)
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
            if try TransactionRecord.fetchOne(db, key: id)?.status == TransactionStatus.pending.rawValue {
                throw PurchaseReviewError.pendingPurchase
            }
            _ = try TransactionRecord.deleteOne(db, key: id)
        }
    }

    func deleteAll() throws {
        try dbPool.write { db in
            try Self.checkPendingPurchases(in: db)
            _ = try TransactionRecord.deleteAll(db)
        }
    }

    func deleteByPlanId(_ planId: Int64) throws {
        try dbPool.write { db in
            try Self.checkPendingPurchases(in: db, for: planId)
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
                SELECT MAX(executedAt) as maxDate FROM transactions
                WHERE planId = ? AND status IN ('COMPLETED', 'PARTIAL')
                """, arguments: [planId])
            guard let epoch = row?["maxDate"] as? Double else { return nil }
            return Date(timeIntervalSince1970: epoch)
        }
    }

    static func checkPendingPurchases(in db: Database, for planId: Int64? = nil) throws {
        var request = TransactionRecord.filter(Column("status") == TransactionStatus.pending.rawValue)
        if let planId { request = request.filter(Column("planId") == planId) }
        if try request.fetchCount(db) > 0 { throw PurchaseReviewError.pendingPurchase }
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

    /// Latest filled purchase of every plan, and the pending purchases (oldest first).
    func observePurchaseOverview() -> DatabasePublishers.Value<(lastByPlan: [Int64: Transaction], pending: [Transaction])> {
        ValueObservation.tracking { db in
            let lastRows = try TransactionRecord.fetchAll(db, sql: """
                SELECT * FROM transactions t WHERE t.status IN ('COMPLETED', 'PARTIAL')
                  AND t.executedAt = (
                    SELECT MAX(t2.executedAt) FROM transactions t2
                    WHERE t2.planId = t.planId AND t2.status IN ('COMPLETED', 'PARTIAL')
                  )
                """)
            var lastByPlan: [Int64: Transaction] = [:]
            for row in lastRows { lastByPlan[row.planId] = row.toDomain() }
            let pending = try TransactionRecord.filter(Column("status") == TransactionStatus.pending.rawValue)
                .order(Column("executedAt").asc)
                .fetchAll(db)
                .map { $0.toDomain() }
            return (lastByPlan: lastByPlan, pending: pending)
        }
        .publisher(in: dbPool, scheduling: .immediate)
    }

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

enum PurchaseReviewError: LocalizedError {
    case purchaseInProgress
    case pendingPurchase
    case noPendingPurchase
    case orderNotInHistory

    var errorDescription: String? {
        switch self {
        case .purchaseInProgress:
            return String(localized: "A purchase check is in progress. Try again when it finishes.")
        case .pendingPurchase:
            return String(localized: "Review pending purchases before deleting their history.")
        case .noPendingPurchase:
            return String(localized: "This purchase is no longer awaiting review.")
        case .orderNotInHistory:
            return String(localized: "The purchase could not be matched. Check the order ID or use the ID shown in this plan’s imported history.")
        }
    }
}
