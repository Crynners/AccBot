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

    func get(crypto: String, exchange: Exchange) throws -> WithdrawalThreshold? {
        try dbPool.read { db in
            try WithdrawalThresholdRecord
                .filter(Column("crypto") == crypto)
                .filter(Column("exchange") == exchange.rawValue)
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

    func delete(crypto: String, exchange: Exchange) throws {
        try dbPool.write { db in
            _ = try WithdrawalThresholdRecord
                .filter(Column("crypto") == crypto)
                .filter(Column("exchange") == exchange.rawValue)
                .deleteAll(db)
        }
    }

    func deleteAll() throws {
        try dbPool.write { db in
            _ = try WithdrawalThresholdRecord.deleteAll(db)
        }
    }
}
