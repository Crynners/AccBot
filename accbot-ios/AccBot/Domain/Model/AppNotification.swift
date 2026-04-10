import Foundation

/// Notification type for in-app notification history
enum NotificationType: String, Codable {
    case purchase = "PURCHASE"
    case error = "ERROR"
    case lowBalance = "LOW_BALANCE"
    case withdrawalThreshold = "WITHDRAWAL_THRESHOLD"
    case networkRetry = "NETWORK_RETRY"
    case missedPurchases = "MISSED_PURCHASES"
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
    let connectionId: Int64?
    let isRead: Bool
    let isArchived: Bool
    let templateArgs: NotificationTemplateArgs?
    let createdAt: Date

    init(
        id: Int64 = 0,
        type: NotificationType,
        title: String,
        message: String,
        planId: Int64? = nil,
        crypto: String? = nil,
        exchange: Exchange? = nil,
        connectionId: Int64? = nil,
        isRead: Bool = false,
        isArchived: Bool = false,
        templateArgs: NotificationTemplateArgs? = nil,
        createdAt: Date = Date()
    ) {
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

    /// Returns dynamically localized title. If templateArgs exist, renders from
    /// them (supports language switching). Falls back to stored title.
    var localizedTitle: String {
        templateArgs?.render().title ?? title
    }

    /// Returns dynamically localized message. If templateArgs exist, renders from
    /// them (supports language switching). Falls back to stored message.
    var localizedMessage: String {
        templateArgs?.render().message ?? message
    }
}
