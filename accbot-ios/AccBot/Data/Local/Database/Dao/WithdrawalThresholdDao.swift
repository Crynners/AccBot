import Foundation
import GRDB

/// DAO for withdrawal_thresholds table
final class WithdrawalThresholdDao {
    private let dbPool: DatabasePool

    init(dbPool: DatabasePool) {
        self.dbPool = dbPool
    }

    func getAll() throws -> [WithdrawalThreshold] {
        try dbPool.read { db in
            try WithdrawalThresholdRecord.fetchAll(db).map { $0.toDomain() }
        }
    }

    func get(crypto: String, connectionId: Int64) throws -> WithdrawalThreshold? {
        try dbPool.read { db in
            try WithdrawalThresholdRecord
                .filter(Column("crypto") == crypto)
                .filter(Column("connectionId") == connectionId)
                .fetchOne(db)?
                .toDomain()
        }
    }

    func upsert(_ threshold: WithdrawalThreshold) throws {
        try dbPool.write { db in
            let record = WithdrawalThresholdRecord.fromDomain(threshold)
            try record.save(db)
        }
    }

    func delete(crypto: String, connectionId: Int64) throws {
        try dbPool.write { db in
            _ = try WithdrawalThresholdRecord
                .filter(Column("crypto") == crypto)
                .filter(Column("connectionId") == connectionId)
                .deleteAll(db)
        }
    }

    func deleteByConnection(_ connectionId: Int64) throws {
        try dbPool.write { db in
            _ = try WithdrawalThresholdRecord
                .filter(Column("connectionId") == connectionId)
                .deleteAll(db)
        }
    }

    func deleteAll() throws {
        try dbPool.write { db in
            _ = try WithdrawalThresholdRecord.deleteAll(db)
        }
    }
}
