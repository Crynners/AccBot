import Foundation
import GRDB
import Combine

/// DAO for dca_plans table
final class DcaPlanDao {
    private let dbPool: DatabasePool

    init(dbPool: DatabasePool) {
        self.dbPool = dbPool
    }

    // MARK: - Queries

    func getAll() throws -> [DcaPlan] {
        try dbPool.read { db in
            try DcaPlanRecord.order(Column("displayOrder"), Column("id")).fetchAll(db).map { $0.toDomain() }
        }
    }

    func getById(_ id: Int64) throws -> DcaPlan? {
        try dbPool.read { db in
            try DcaPlanRecord.fetchOne(db, key: id)?.toDomain()
        }
    }

    func getEnabledPlans() throws -> [DcaPlan] {
        try dbPool.read { db in
            try DcaPlanRecord
                .filter(Column("isEnabled") == true)
                .fetchAll(db)
                .map { $0.toDomain() }
        }
    }

    func getDuePlans(before date: Date = Date()) throws -> [DcaPlan] {
        try dbPool.read { db in
            try DcaPlanRecord
                .filter(Column("isEnabled") == true)
                .filter(Column("nextExecutionAt") != nil)
                .filter(Column("nextExecutionAt") <= date.timeIntervalSince1970)
                .fetchAll(db)
                .map { $0.toDomain() }
        }
    }

    func getPlansByExchange(_ exchange: Exchange) throws -> [DcaPlan] {
        try dbPool.read { db in
            try DcaPlanRecord
                .filter(Column("exchange") == exchange.rawValue)
                .fetchAll(db)
                .map { $0.toDomain() }
        }
    }

    func getNextExecutionDate() throws -> Date? {
        try dbPool.read { db in
            let row = try Row.fetchOne(db, sql: """
                SELECT MIN(nextExecutionAt) as minNext
                FROM dca_plans
                WHERE isEnabled = 1 AND nextExecutionAt IS NOT NULL
                """)
            guard let timestamp = row?["minNext"] as? Double else { return nil }
            return Date(timeIntervalSince1970: timestamp)
        }
    }

    // MARK: - Mutations

    @discardableResult
    func insert(_ plan: DcaPlan) throws -> Int64 {
        try dbPool.write { db in
            let record = DcaPlanRecord.fromDomain(plan)
            try record.insert(db)
            return db.lastInsertedRowID
        }
    }

    func update(_ plan: DcaPlan) throws {
        try dbPool.write { db in
            let record = DcaPlanRecord.fromDomain(plan)
            try record.update(db)
        }
    }

    func delete(id: Int64) throws {
        try dbPool.write { db in
            try TransactionDao.checkPendingPurchases(in: db, for: id)
            _ = try DcaPlanRecord.deleteOne(db, key: id)
        }
    }

    func deleteAll() throws {
        try dbPool.write { db in
            try TransactionDao.checkPendingPurchases(in: db)
            _ = try DcaPlanRecord.deleteAll(db)
        }
    }

    /// Resuming a plan whose due time passed while it was paused makes it due now, so the
    /// paused period does not count as missed purchases.
    func setEnabled(id: Int64, enabled: Bool, now: Date = Date()) throws {
        try dbPool.write { db in
            try db.execute(
                sql: "UPDATE dca_plans SET isEnabled = ? WHERE id = ?",
                arguments: [enabled, id]
            )
            if enabled {
                try db.execute(
                    sql: "UPDATE dca_plans SET nextExecutionAt = ? WHERE id = ? AND nextExecutionAt < ?",
                    arguments: [now.timeIntervalSince1970, id, now.timeIntervalSince1970]
                )
            } else {
                try db.execute(sql: "DELETE FROM missed_purchases WHERE planId = ?", arguments: [id])
            }
        }
    }

    func updateExecution(id: Int64, lastExecutedAt: Date, nextExecutionAt: Date?) throws {
        try dbPool.write { db in
            try db.execute(
                sql: """
                    UPDATE dca_plans
                    SET lastExecutedAt = ?, nextExecutionAt = ?
                    WHERE id = ?
                    """,
                arguments: [
                    lastExecutedAt.timeIntervalSince1970,
                    nextExecutionAt?.timeIntervalSince1970,
                    id,
                ]
            )
        }
    }

    /// Reserve the schedule and record the intent in one write before the order is sent.
    /// `missedDelta` changes the plan's missed purchase count in the same write: positive when
    /// a late run skipped schedule slots, -1 when this purchase buys one of them.
    func beginPurchase(
        _ intent: Transaction,
        for plan: DcaPlan,
        nextExecution: Date,
        missedDelta: Int = 0
    ) throws -> PurchaseClaim {
        try dbPool.write { db in
            guard let current = try DcaPlanRecord.fetchOne(db, key: plan.id)?.toDomain(),
                  current == plan, current.isEnabled else { return .blocked }

            if try Self.pauseIfTargetReached(current, in: db) {
                return .targetReached
            }

            let pending = TransactionRecord.filter(Column("planId") == plan.id)
                .filter(Column("status") == TransactionStatus.pending.rawValue)
            let blocksPurchase = if let target = plan.targetAmount, target > 0 {
                try pending.fetchCount(db) > 0
            } else {
                try pending.filter(Column("exchangeOrderId") == nil || Column("exchangeOrderId") == "")
                    .fetchCount(db) > 0
            }
            guard !blocksPurchase else { return .blocked }

            if missedDelta < 0, try MissedPurchaseDao.count(in: db, for: plan.id) < -missedDelta {
                return .blocked
            }

            try TransactionRecord.fromDomain(intent).insert(db)
            let intentId = db.lastInsertedRowID
            try MissedPurchaseDao.add(missedDelta, in: db, for: plan.id)
            try db.execute(
                sql: "UPDATE dca_plans SET lastExecutedAt = ?, nextExecutionAt = ? WHERE id = ?",
                arguments: [intent.executedAt.timeIntervalSince1970, nextExecution.timeIntervalSince1970, plan.id]
            )
            return .started(intentId)
        }
    }

    func pauseIfTargetReached(_ planId: Int64) throws -> DcaPlan? {
        try dbPool.write { db in
            guard let plan = try DcaPlanRecord.fetchOne(db, key: planId)?.toDomain(),
                  try Self.pauseIfTargetReached(plan, in: db) else { return nil }
            return plan
        }
    }

    private static func pauseIfTargetReached(_ plan: DcaPlan, in db: Database) throws -> Bool {
        guard plan.isEnabled, let target = plan.targetAmount, target > 0,
              try TransactionDao.accumulatedCrypto(in: db, for: plan.id) >= target else { return false }
        try db.execute(sql: "UPDATE dca_plans SET isEnabled = 0 WHERE id = ?", arguments: [plan.id])
        return true
    }

    // MARK: - Observation (reactive)

    func observeAll() -> DatabasePublishers.Value<[DcaPlan]> {
        ValueObservation.tracking { db in
            try DcaPlanRecord.order(Column("displayOrder"), Column("id")).fetchAll(db).map { $0.toDomain() }
        }
        .publisher(in: dbPool, scheduling: .immediate)
    }

    func observeEnabled() -> DatabasePublishers.Value<[DcaPlan]> {
        ValueObservation.tracking { db in
            try DcaPlanRecord
                .filter(Column("isEnabled") == true)
                .fetchAll(db)
                .map { $0.toDomain() }
        }
        .publisher(in: dbPool, scheduling: .immediate)
    }
}

enum PurchaseClaim: Equatable {
    case started(Int64)
    case targetReached
    case blocked
}
