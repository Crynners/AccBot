import Foundation
import GRDB
import Combine

/// DAO for withdrawals table
final class WithdrawalDao {
    private let dbPool: DatabasePool

    init(dbPool: DatabasePool) {
        self.dbPool = dbPool
    }

    func getAll() throws -> [Withdrawal] {
        try dbPool.read { db in
            try WithdrawalRecord
                .order(Column("createdAt").desc)
                .fetchAll(db)
                .map { $0.toDomain() }
        }
    }

    func getByPlanId(_ planId: Int64) throws -> [Withdrawal] {
        try dbPool.read { db in
            try WithdrawalRecord
                .filter(Column("planId") == planId)
                .order(Column("createdAt").desc)
                .fetchAll(db)
                .map { $0.toDomain() }
        }
    }

    func getPending() throws -> [Withdrawal] {
        try dbPool.read { db in
            try WithdrawalRecord
                .filter(Column("status") == WithdrawalStatus.pending.rawValue)
                .fetchAll(db)
                .map { $0.toDomain() }
        }
    }

    @discardableResult
    func insert(_ withdrawal: Withdrawal) throws -> Int64 {
        try dbPool.write { db in
            let record = WithdrawalRecord.fromDomain(withdrawal)
            try record.insert(db)
            return db.lastInsertedRowID
        }
    }

    func update(_ withdrawal: Withdrawal) throws {
        try dbPool.write { db in
            let record = WithdrawalRecord.fromDomain(withdrawal)
            try record.update(db)
        }
    }

    func deleteAll() throws {
        try dbPool.write { db in
            _ = try WithdrawalRecord.deleteAll(db)
        }
    }
}
