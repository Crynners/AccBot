import Foundation
import GRDB

/// DAO for monthly_summaries table
final class MonthlySummaryDao {
    private let dbPool: DatabasePool

    init(dbPool: DatabasePool) {
        self.dbPool = dbPool
    }

    func getAll() throws -> [MonthlySummaryRecord] {
        try dbPool.read { db in
            try MonthlySummaryRecord
                .order(Column("year").desc, Column("month").desc)
                .fetchAll(db)
        }
    }

    func getRecent(limit: Int) throws -> [MonthlySummaryRecord] {
        try dbPool.read { db in
            try MonthlySummaryRecord
                .order(Column("year").desc, Column("month").desc)
                .limit(limit)
                .fetchAll(db)
        }
    }

    func get(id: String) throws -> MonthlySummaryRecord? {
        try dbPool.read { db in
            try MonthlySummaryRecord.fetchOne(db, key: id)
        }
    }

    func insertOrReplace(_ summary: MonthlySummaryRecord) throws {
        try dbPool.write { db in
            try summary.save(db)
        }
    }

    func deleteAll() throws {
        try dbPool.write { db in
            _ = try MonthlySummaryRecord.deleteAll(db)
        }
    }
}
