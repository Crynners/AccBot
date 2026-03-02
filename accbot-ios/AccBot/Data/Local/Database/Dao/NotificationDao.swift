import Foundation
import GRDB
import Combine

/// DAO for notifications table
final class NotificationDao {
    private let dbPool: DatabasePool

    init(dbPool: DatabasePool) {
        self.dbPool = dbPool
    }

    // MARK: - Queries

    func getAll() throws -> [AppNotification] {
        try dbPool.read { db in
            try NotificationRecord
                .order(Column("createdAt").desc)
                .fetchAll(db)
                .map { $0.toDomain() }
        }
    }

    func getUnreadCount() throws -> Int {
        try dbPool.read { db in
            try NotificationRecord
                .filter(Column("isRead") == false)
                .fetchCount(db)
        }
    }

    // MARK: - Mutations

    @discardableResult
    func insert(_ notification: AppNotification) throws -> Int64 {
        try dbPool.write { db in
            let record = NotificationRecord.fromDomain(notification)
            try record.insert(db)
            return db.lastInsertedRowID
        }
    }

    func markAsRead(id: Int64) throws {
        try dbPool.write { db in
            try db.execute(
                sql: "UPDATE notifications SET isRead = 1 WHERE id = ?",
                arguments: [id]
            )
        }
    }

    func markAllAsRead() throws {
        try dbPool.write { db in
            try db.execute(sql: "UPDATE notifications SET isRead = 1 WHERE isRead = 0")
        }
    }

    func delete(id: Int64) throws {
        try dbPool.write { db in
            try db.execute(
                sql: "DELETE FROM notifications WHERE id = ?",
                arguments: [id]
            )
        }
    }

    func deleteAllRead() throws {
        try dbPool.write { db in
            try db.execute(sql: "DELETE FROM notifications WHERE isRead = 1")
        }
    }

    func deleteAll() throws {
        try dbPool.write { db in
            _ = try NotificationRecord.deleteAll(db)
        }
    }

    // MARK: - Observation

    func observeAll() -> DatabasePublishers.Value<[AppNotification]> {
        ValueObservation.tracking { db in
            try NotificationRecord
                .order(Column("createdAt").desc)
                .fetchAll(db)
                .map { $0.toDomain() }
        }
        .publisher(in: dbPool, scheduling: .immediate)
    }

    func observeUnreadCount() -> DatabasePublishers.Value<Int> {
        ValueObservation.tracking { db in
            try NotificationRecord
                .filter(Column("isRead") == false)
                .fetchCount(db)
        }
        .publisher(in: dbPool, scheduling: .immediate)
    }
}
