import Foundation
import GRDB

/// GRDB Record for notifications table
struct NotificationRecord: Codable, FetchableRecord, PersistableRecord, Identifiable {
    static let databaseTableName = "notifications"

    var id: Int64?
    var type: String
    var title: String
    var message: String
    var planId: Int64?
    var crypto: String?
    var exchange: String?
    var isRead: Bool
    var isArchived: Bool
    var createdAt: Double

    func toDomain() -> AppNotification {
        AppNotification(
            id: id ?? 0,
            type: NotificationType(rawValue: type) ?? .error,
            title: title,
            message: message,
            planId: planId,
            crypto: crypto,
            exchange: exchange.flatMap { Exchange(rawValue: $0) },
            isRead: isRead,
            isArchived: isArchived,
            createdAt: Date(timeIntervalSince1970: createdAt)
        )
    }

    static func fromDomain(_ n: AppNotification) -> NotificationRecord {
        NotificationRecord(
            id: n.id == 0 ? nil : n.id,
            type: n.type.rawValue,
            title: n.title,
            message: n.message,
            planId: n.planId,
            crypto: n.crypto,
            exchange: n.exchange?.rawValue,
            isRead: n.isRead,
            isArchived: n.isArchived,
            createdAt: n.createdAt.timeIntervalSince1970
        )
    }
}
