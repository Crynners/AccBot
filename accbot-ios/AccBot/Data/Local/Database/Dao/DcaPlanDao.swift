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
