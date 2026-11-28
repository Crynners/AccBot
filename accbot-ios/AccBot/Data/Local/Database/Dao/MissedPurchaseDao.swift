import Foundation
import GRDB

/// Scheduled purchases that passed while nothing ran (iOS did not start the app in time).
/// One row per plan with the number of missed slots; the user can buy them later or skip them.
final class MissedPurchaseDao {
    private let dbPool: DatabasePool

    init(dbPool: DatabasePool) {
        self.dbPool = dbPool
    }

    func count(for planId: Int64) throws -> Int {
        try dbPool.read { db in try Self.count(in: db, for: planId) }
    }

    /// Missed purchase counts of enabled plans, keyed by plan id.
    func getAll() throws -> [Int64: Int] {
        try dbPool.read { db in try Self.fetchAll(in: db) }
    }

    func clear(planId: Int64) throws {
        try dbPool.write { db in
            try db.execute(sql: "DELETE FROM missed_purchases WHERE planId = ?", arguments: [planId])
        }
    }

    func observeAll() -> DatabasePublishers.Value<[Int64: Int]> {
        ValueObservation.tracking { db in try Self.fetchAll(in: db) }
            .publisher(in: dbPool, scheduling: .immediate)
    }

    // MARK: - Shared with DcaPlanDao's purchase claim

    static func count(in db: Database, for planId: Int64) throws -> Int {
        try Int.fetchOne(db, sql: "SELECT count FROM missed_purchases WHERE planId = ?", arguments: [planId]) ?? 0
    }

    /// Adds `delta` (negative to consume) and keeps the count at zero or above.
    static func add(_ delta: Int, in db: Database, for planId: Int64) throws {
        guard delta != 0 else { return }
        let updated = max(0, try count(in: db, for: planId) + delta)
        if updated == 0 {
            try db.execute(sql: "DELETE FROM missed_purchases WHERE planId = ?", arguments: [planId])
        } else {
            try db.execute(
                sql: "INSERT OR REPLACE INTO missed_purchases (planId, count) VALUES (?, ?)",
                arguments: [planId, updated]
            )
        }
    }

    private static func fetchAll(in db: Database) throws -> [Int64: Int] {
        let rows = try Row.fetchAll(db, sql: """
            SELECT m.planId, m.count FROM missed_purchases m
            JOIN dca_plans p ON p.id = m.planId
            WHERE p.isEnabled = 1 AND m.count > 0
            """)
        var result: [Int64: Int] = [:]
        for row in rows {
            let planId: Int64 = row["planId"]
            let count: Int = row["count"]
            result[planId] = count
        }
        return result
    }
}
