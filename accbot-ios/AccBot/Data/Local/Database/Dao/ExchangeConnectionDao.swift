import Foundation
import GRDB
import Combine

/// DAO for exchange_connections table
final class ExchangeConnectionDao {
    private let dbPool: DatabasePool

    init(dbPool: DatabasePool) {
        self.dbPool = dbPool
    }

    // MARK: - Queries

    func getAll() throws -> [ExchangeConnection] {
        try dbPool.read { db in
            try ExchangeConnectionRecord
                .order(Column("displayOrder").asc, Column("createdAt").asc)
                .fetchAll(db)
                .map { $0.toDomain() }
        }
    }

    func getById(_ id: Int64) throws -> ExchangeConnection? {
        try dbPool.read { db in
            try ExchangeConnectionRecord.fetchOne(db, key: id)?.toDomain()
        }
    }

    func getByExchange(_ exchange: Exchange) throws -> [ExchangeConnection] {
        try dbPool.read { db in
            try ExchangeConnectionRecord
                .filter(Column("exchange") == exchange.rawValue)
                .order(Column("displayOrder").asc, Column("createdAt").asc)
                .fetchAll(db)
                .map { $0.toDomain() }
        }
    }

    /// Returns the default (first) connection for an exchange, ordered by displayOrder then createdAt.
    func getDefaultByExchange(_ exchange: Exchange) throws -> ExchangeConnection? {
        try dbPool.read { db in
            try ExchangeConnectionRecord
                .filter(Column("exchange") == exchange.rawValue)
                .order(Column("displayOrder").asc, Column("createdAt").asc)
                .fetchOne(db)?
                .toDomain()
        }
    }

    func countByExchange(_ exchange: Exchange) throws -> Int {
        try dbPool.read { db in
            try ExchangeConnectionRecord
                .filter(Column("exchange") == exchange.rawValue)
                .fetchCount(db)
        }
    }

    // MARK: - Mutations

    @discardableResult
    func insert(_ connection: ExchangeConnection) throws -> Int64 {
        try dbPool.write { db in
            var record = ExchangeConnectionRecord.fromDomain(connection)
            try record.insert(db)
            return record.id ?? db.lastInsertedRowID
        }
    }

    func update(_ connection: ExchangeConnection) throws {
        try dbPool.write { db in
            let record = ExchangeConnectionRecord.fromDomain(connection)
            try record.update(db)
        }
    }

    func deleteById(_ id: Int64) throws {
        try dbPool.write { db in
            _ = try ExchangeConnectionRecord.deleteOne(db, key: id)
        }
    }

    // MARK: - Observation

    func observeAll() -> DatabasePublishers.Value<[ExchangeConnection]> {
        ValueObservation.tracking { db in
            try ExchangeConnectionRecord
                .order(Column("displayOrder").asc, Column("createdAt").asc)
                .fetchAll(db)
                .map { $0.toDomain() }
        }
        .publisher(in: dbPool, scheduling: .immediate)
    }
}
