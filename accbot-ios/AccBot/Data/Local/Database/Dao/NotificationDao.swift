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

    func getActive() throws -> [AppNotification] {
        try dbPool.read { db in
            try NotificationRecord
                .filter(Column("isArchived") == false)
                .order(Column("createdAt").desc)
                .fetchAll(db)
                .map { $0.toDomain() }
        }
    }

    func getArchived() throws -> [AppNotification] {
        try dbPool.read { db in
            try NotificationRecord
                .filter(Column("isArchived") == true)
                .order(Column("createdAt").desc)
                .fetchAll(db)
                .map { $0.toDomain() }
        }
    }

    func getUnreadCount() throws -> Int {
        try dbPool.read { db in
            try NotificationRecord
                .filter(Column("isRead") == false)
                .filter(Column("isArchived") == false)
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
            try db.execute(sql: "UPDATE notifications SET isRead = 1 WHERE isArchived = 0")
        }
    }

    func archive(id: Int64) throws {
        try dbPool.write { db in
            try db.execute(
                sql: "UPDATE notifications SET isArchived = 1, isRead = 1 WHERE id = ?",
                arguments: [id]
            )
        }
    }

    func archiveAll() throws {
        try dbPool.write { db in
            try db.execute(sql: "UPDATE notifications SET isArchived = 1, isRead = 1 WHERE isArchived = 0")
        }
    }

    func clearArchive() throws {
        try dbPool.write { db in
            _ = try NotificationRecord
                .filter(Column("isArchived") == true)
                .deleteAll(db)
        }
    }

    func deleteAll() throws {
        try dbPool.write { db in
            _ = try NotificationRecord.deleteAll(db)
        }
    }

    // MARK: - Observation

    func observeActive() -> DatabasePublishers.Value<[AppNotification]> {
        ValueObservation.tracking { db in
            try NotificationRecord
                .filter(Column("isArchived") == false)
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
                .filter(Column("isArchived") == false)
                .fetchCount(db)
        }
        .publisher(in: dbPool, scheduling: .immediate)
    }
}
