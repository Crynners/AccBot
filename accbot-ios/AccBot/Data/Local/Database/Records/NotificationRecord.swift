import Foundation
import GRDB

/// GRDB Record for notifications table
struct NotificationRecord: FetchableRecord, PersistableRecord, Identifiable {
    static let databaseTableName = "notifications"

    var id: Int64?
    var type: String
    var title: String
    var message: String
    var planId: Int64?
    var crypto: String?
    var exchange: String?
    var connectionId: Int64?
    var isRead: Bool
    var isArchived: Bool
    var templateArgs: String?
    var createdAt: Double

    // Custom row init to safely handle optional columns (templateArgs, connectionId)
    init(row: Row) {
        id = row["id"]
        type = row["type"]
        title = row["title"]
        message = row["message"]
        planId = row["planId"]
        crypto = row["crypto"]
        exchange = row["exchange"]
        connectionId = row["connectionId"]
        isRead = row["isRead"]
        isArchived = row["isArchived"]
        templateArgs = row["templateArgs"]
        createdAt = row["createdAt"]
    }

    // For PersistableRecord
    func encode(to container: inout PersistenceContainer) {
        container["id"] = id
        container["type"] = type
        container["title"] = title
        container["message"] = message
        container["planId"] = planId
        container["crypto"] = crypto
        container["exchange"] = exchange
        container["connectionId"] = connectionId
        container["isRead"] = isRead
        container["isArchived"] = isArchived
        container["templateArgs"] = templateArgs
        container["createdAt"] = createdAt
    }

    init(id: Int64?, type: String, title: String, message: String,
         planId: Int64?, crypto: String?, exchange: String?, connectionId: Int64?,
         isRead: Bool, isArchived: Bool, templateArgs: String?, createdAt: Double) {
        self.id = id
        self.type = type
        self.title = title
        self.message = message
        self.planId = planId
        self.crypto = crypto
        self.exchange = exchange
        self.connectionId = connectionId
        self.isRead = isRead
        self.isArchived = isArchived
        self.templateArgs = templateArgs
        self.createdAt = createdAt
    }

    func toDomain() -> AppNotification {
        AppNotification(
            id: id ?? 0,
            type: NotificationType(rawValue: type) ?? .error,
            title: title,
            message: message,
            planId: planId,
            crypto: crypto,
            exchange: exchange.flatMap { Exchange(rawValue: $0) },
            connectionId: connectionId,
            isRead: isRead,
            isArchived: isArchived,
            templateArgs: templateArgs.flatMap { NotificationTemplateArgs.fromJSON($0) },
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
            connectionId: n.connectionId,
            isRead: n.isRead,
            isArchived: n.isArchived,
            templateArgs: n.templateArgs?.toJSON(),
            createdAt: n.createdAt.timeIntervalSince1970
        )
    }
}
