import Foundation
import UserNotifications

/// Manages system (push) and in-app notifications
final class NotificationService {

    // MARK: - Notification Categories (matching Android channels)
    static let purchaseCategory = "accbot_purchase"
    static let errorCategory = "accbot_error"
    static let lowBalanceCategory = "accbot_low_balance"
    static let withdrawalThresholdCategory = "accbot_withdrawal_threshold"
    static let dcaReminderCategory = "accbot_dca_reminder"

    init() {
        registerCategories()
    }

    // MARK: - Permission

    func requestPermission() async -> Bool {
        do {
            return try await UNUserNotificationCenter.current()
                .requestAuthorization(options: [.alert, .sound, .badge])
        } catch {
            return false
        }
    }

    func isAuthorized() async -> Bool {
        let settings = await UNUserNotificationCenter.current().notificationSettings()
        return settings.authorizationStatus == .authorized
    }

    // MARK: - Post Notifications

    func postPurchaseNotification(crypto: String, fiat: String, amount: Decimal, exchange: Exchange) {
        let content = UNMutableNotificationContent()
        content.title = String(localized: "DCA Purchase Completed")
        content.body = String(localized: "Bought \(crypto) for \(amount as NSDecimalNumber) \(fiat) on \(exchange.displayName)")
        content.sound = .default
        content.categoryIdentifier = Self.purchaseCategory

        let request = UNNotificationRequest(
            identifier: UUID().uuidString,
            content: content,
            trigger: nil
        )
        UNUserNotificationCenter.current().add(request)
    }

    func postErrorNotification(exchange: Exchange, message: String) {
        let content = UNMutableNotificationContent()
        content.title = String(localized: "DCA Error")
        content.body = "\(exchange.displayName): \(message)"
        content.sound = .default
        content.categoryIdentifier = Self.errorCategory

        let request = UNNotificationRequest(
            identifier: UUID().uuidString,
            content: content,
            trigger: nil
        )
        UNUserNotificationCenter.current().add(request)
    }

    func postLowBalanceNotification(exchange: Exchange, fiat: String, balance: Decimal, daysLeft: Int) {
        let content = UNMutableNotificationContent()
        content.title = String(localized: "Low Balance Warning")
        content.body = String(localized: "\(exchange.displayName): \(balance as NSDecimalNumber) \(fiat) remaining (~\(daysLeft) days)")
        content.sound = .default
        content.categoryIdentifier = Self.lowBalanceCategory

        let request = UNNotificationRequest(
            identifier: UUID().uuidString,
            content: content,
            trigger: nil
        )
        UNUserNotificationCenter.current().add(request)
    }

    func postWithdrawalThresholdNotification(crypto: String, exchange: Exchange, amount: Decimal) {
        let content = UNMutableNotificationContent()
        content.title = String(localized: "Withdrawal Threshold Reached")
        content.body = String(localized: "\(amount as NSDecimalNumber) \(crypto) on \(exchange.displayName) ready for withdrawal")
        content.sound = .default
        content.categoryIdentifier = Self.withdrawalThresholdCategory

        let request = UNNotificationRequest(
            identifier: UUID().uuidString,
            content: content,
            trigger: nil
        )
        UNUserNotificationCenter.current().add(request)
    }

    // MARK: - DCA Reminder (Layer 3)

    func scheduleDcaReminder(at date: Date, planDescription: String) {
        let content = UNMutableNotificationContent()
        content.title = String(localized: "DCA Plan Due")
        content.body = String(localized: "Tap to execute: \(planDescription)")
        content.sound = .default
        content.categoryIdentifier = Self.dcaReminderCategory

        let trigger = UNTimeIntervalNotificationTrigger(
            timeInterval: max(1, date.timeIntervalSinceNow),
            repeats: false
        )

        let request = UNNotificationRequest(
            identifier: "dca_reminder_\(planDescription)",
            content: content,
            trigger: trigger
        )
        UNUserNotificationCenter.current().add(request)
    }

    func cancelAllDcaReminders() {
        let center = UNUserNotificationCenter.current()
        center.getPendingNotificationRequests { requests in
            let reminderIds = requests
                .filter { $0.identifier.hasPrefix("dca_reminder_") }
                .map { $0.identifier }
            center.removePendingNotificationRequests(withIdentifiers: reminderIds)
        }
    }

    // MARK: - Badge

    func updateBadge(count: Int) {
        UNUserNotificationCenter.current().setBadgeCount(count)
    }

    // MARK: - Private

    private func registerCategories() {
        let categories: Set<UNNotificationCategory> = [
            UNNotificationCategory(identifier: Self.purchaseCategory, actions: [], intentIdentifiers: []),
            UNNotificationCategory(identifier: Self.errorCategory, actions: [], intentIdentifiers: []),
            UNNotificationCategory(identifier: Self.lowBalanceCategory, actions: [], intentIdentifiers: []),
            UNNotificationCategory(identifier: Self.withdrawalThresholdCategory, actions: [], intentIdentifiers: []),
            UNNotificationCategory(identifier: Self.dcaReminderCategory, actions: [], intentIdentifiers: []),
        ]
        UNUserNotificationCenter.current().setNotificationCategories(categories)
    }
}
