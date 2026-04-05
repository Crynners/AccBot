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
            try DcaPlanRecord.fetchAll(db).map { $0.toDomain() }
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
            _ = try DcaPlanRecord.deleteOne(db, key: id)
        }
    }

    func deleteAll() throws {
        try dbPool.write { db in
            _ = try DcaPlanRecord.deleteAll(db)
        }
    }

    func setEnabled(id: Int64, enabled: Bool) throws {
        try dbPool.write { db in
            try db.execute(
                sql: "UPDATE dca_plans SET isEnabled = ? WHERE id = ?",
                arguments: [enabled, id]
            )
        }
    }

    func setEnabledAndNextExecution(id: Int64, enabled: Bool, nextExecutionAt: Date) throws {
        try dbPool.write { db in
            try db.execute(
                sql: "UPDATE dca_plans SET isEnabled = ?, nextExecutionAt = ? WHERE id = ?",
                arguments: [enabled, nextExecutionAt.timeIntervalSince1970, id]
            )
        }
    }

    func setNextExecution(id: Int64, nextExecutionAt: Date) throws {
        try dbPool.write { db in
            try db.execute(
                sql: "UPDATE dca_plans SET nextExecutionAt = ? WHERE id = ?",
                arguments: [nextExecutionAt.timeIntervalSince1970, id]
            )
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

    /// Atomically claim a plan for execution by advancing nextExecutionAt,
    /// but only if it is still in the past (or null). Returns true if claimed,
    /// false if another task already claimed it.
    /// Note: lastExecutedAt is NOT set here — it is only set after a transaction
    /// is actually saved, so the UI never shows a "last executed" time without
    /// a corresponding transaction.
    func claimPlanForExecution(id: Int64, now: Date, nextExecutionAt: Date) throws -> Bool {
        try dbPool.write { db in
            try db.execute(
                sql: """
                    UPDATE dca_plans
                    SET nextExecutionAt = ?
                    WHERE id = ? AND (nextExecutionAt IS NULL OR nextExecutionAt <= ?)
                    """,
                arguments: [
                    nextExecutionAt.timeIntervalSince1970,
                    id,
                    now.timeIntervalSince1970,
                ]
            )
            return db.changesCount > 0
        }
    }

    // MARK: - Retry / Missed

    /// Set network retry state on a plan after a retryable failure.
    func setNetworkRetry(id: Int64, count: Int, nextRetryAt: Date, originalScheduledAt: Date?) throws {
        try dbPool.write { db in
            try db.execute(
                sql: """
                    UPDATE dca_plans
                    SET networkRetryCount = ?,
                        nextNetworkRetryAt = ?,
                        nextExecutionAt = ?,
                        originalScheduledAt = COALESCE(originalScheduledAt, ?)
                    WHERE id = ?
                    """,
                arguments: [
                    count,
                    nextRetryAt.timeIntervalSince1970,
                    nextRetryAt.timeIntervalSince1970,
                    originalScheduledAt?.timeIntervalSince1970,
                    id,
                ]
            )
        }
    }

    /// Clear retry state after a successful purchase.
    func clearNetworkRetry(id: Int64) throws {
        try dbPool.write { db in
            try db.execute(
                sql: """
                    UPDATE dca_plans
                    SET networkRetryCount = 0,
                        nextNetworkRetryAt = NULL,
                        originalScheduledAt = NULL
                    WHERE id = ?
                    """,
                arguments: [id]
            )
        }
    }

    /// Set missed purchase count on a plan.
    func setMissedPurchaseCount(id: Int64, count: Int) throws {
        try dbPool.write { db in
            try db.execute(
                sql: "UPDATE dca_plans SET missedPurchaseCount = ? WHERE id = ?",
                arguments: [count, id]
            )
        }
    }

    /// Get plans that are in network retry state.
    func getPlansInRetry() throws -> [DcaPlan] {
        try dbPool.read { db in
            try DcaPlanRecord
                .filter(Column("networkRetryCount") > 0)
                .fetchAll(db)
                .map { $0.toDomain() }
        }
    }

    /// Get plans with missed purchases.
    func getPlansWithMissedPurchases() throws -> [DcaPlan] {
        try dbPool.read { db in
            try DcaPlanRecord
                .filter(Column("missedPurchaseCount") > 0)
                .fetchAll(db)
                .map { $0.toDomain() }
        }
    }

    // MARK: - Observation (reactive)

    func observeAll() -> DatabasePublishers.Value<[DcaPlan]> {
        ValueObservation.tracking { db in
            try DcaPlanRecord.fetchAll(db).map { $0.toDomain() }
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
