import Foundation

/// Notification type for in-app notification history
enum NotificationType: String, Codable {
    case purchase = "PURCHASE"
    case error = "ERROR"
    case lowBalance = "LOW_BALANCE"
    case withdrawalThreshold = "WITHDRAWAL_THRESHOLD"
}

/// In-app notification for display in the Notifications tab
struct AppNotification: Identifiable, Equatable {
    let id: Int64
    let type: NotificationType
    let title: String
    let message: String
    let planId: Int64?
    let crypto: String?
    let exchange: Exchange?
    let isRead: Bool
    let isArchived: Bool
    let createdAt: Date

    init(
        id: Int64 = 0,
        type: NotificationType,
        title: String,
        message: String,
        planId: Int64? = nil,
        crypto: String? = nil,
        exchange: Exchange? = nil,
        isRead: Bool = false,
        isArchived: Bool = false,
        createdAt: Date = Date()
    ) {
        self.id = id
        self.type = type
        self.title = title
        self.message = message
        self.planId = planId
        self.crypto = crypto
        self.exchange = exchange
        self.isRead = isRead
        self.isArchived = isArchived
        self.createdAt = createdAt
    }
}
