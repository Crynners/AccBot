import Foundation
import UserNotifications

/// The safety net for purchases iOS did not run in time.
///
/// Every enabled plan gets a local notification shortly after its due time and a second one
/// a few hours later. Whenever purchases run, the reminders are rescheduled from the plans'
/// new due times, so a reminder only shows up when its purchase really did not happen.
/// The notification has a "Buy now" action that buys in the background after Face ID.
enum PurchaseReminderScheduler {
    static let category = NotificationService.dcaReminderCategory
    static let buyNowAction = "accbot_buy_now"
    static let identifierPrefix = "dca_reminder_"

    /// How long after the due time each reminder fires.
    static let delays: [TimeInterval] = [15 * 60, 6 * 3600]

    struct Reminder: Equatable {
        let planId: Int64
        let stage: Int
        let fireAt: Date

        var identifier: String { "\(PurchaseReminderScheduler.identifierPrefix)\(planId)_\(stage)" }
    }

    /// Reminders that should be pending for these plans at `now`. Pure, for tests.
    static func plannedReminders(plans: [DcaPlan], now: Date) -> [Reminder] {
        plans.flatMap { plan -> [Reminder] in
            guard plan.isEnabled, let due = plan.nextExecutionAt else { return [] }
            return delays.enumerated().compactMap { index, delay in
                let fireAt = due.addingTimeInterval(delay)
                return fireAt > now ? Reminder(planId: plan.id, stage: index + 1, fireAt: fireAt) : nil
            }
        }
    }

    /// Plans whose first reminder time has passed without a purchase.
    static func latePlanIds(plans: [DcaPlan], now: Date) -> Set<Int64> {
        Set(plans.compactMap { plan in
            guard plan.isEnabled, let due = plan.nextExecutionAt,
                  due.addingTimeInterval(delays[0]) <= now else { return nil }
            return plan.id
        })
    }

    static func planId(fromIdentifier identifier: String) -> Int64? {
        guard identifier.hasPrefix(identifierPrefix) else { return nil }
        let rest = identifier.dropFirst(identifierPrefix.count)
        return rest.split(separator: "_").first.flatMap { Int64($0) }
    }

    /// Replaces all pending reminders and clears delivered ones whose purchase has happened since.
    static func reschedule(plans: [DcaPlan], enabled: Bool, isSandbox: Bool, now: Date = Date()) async {
        let center = UNUserNotificationCenter.current()

        let pending = await center.pendingNotificationRequests()
        center.removePendingNotificationRequests(
            withIdentifiers: pending.map(\.identifier).filter { $0.hasPrefix(identifierPrefix) }
        )

        let late = latePlanIds(plans: plans, now: now)
        let delivered = await center.deliveredNotifications()
        let resolved = delivered.map(\.request.identifier).filter { identifier in
            guard let planId = planId(fromIdentifier: identifier) else { return false }
            return !late.contains(planId)
        }
        center.removeDeliveredNotifications(withIdentifiers: resolved)

        guard enabled else { return }
        let plansById = Dictionary(plans.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
        for reminder in plannedReminders(plans: plans, now: now) {
            guard let plan = plansById[reminder.planId] else { continue }
            let request = UNNotificationRequest(
                identifier: reminder.identifier,
                content: content(for: plan, stage: reminder.stage, isSandbox: isSandbox),
                trigger: UNTimeIntervalNotificationTrigger(
                    timeInterval: max(1, reminder.fireAt.timeIntervalSince(now)), repeats: false
                )
            )
            try? await center.add(request)
        }
    }

    private static func content(for plan: DcaPlan, stage: Int, isSandbox: Bool) -> UNNotificationContent {
        let content = UNMutableNotificationContent()
        let amount = AccBotFormatters.formatFiat(plan.amount, symbol: plan.fiat)
        content.title = stage == 1
            ? String(localized: "Purchase is waiting")
            : String(localized: "Purchase still hasn't run")
        let body = String(localized: "\(plan.displayName): the \(amount) purchase hasn't run yet because iOS didn't start AccBot. Tap to buy now.")
        content.body = isSandbox ? "[Sandbox] \(body)" : body
        content.sound = .default
        content.categoryIdentifier = category
        content.threadIdentifier = category
        content.interruptionLevel = .timeSensitive
        content.userInfo = ["planId": plan.id]
        return content
    }
}

/// Handles taps on AccBot notifications, including "Buy now" without opening the app.
final class NotificationActionHandler: NSObject, UNUserNotificationCenterDelegate {
    static let shared = NotificationActionHandler()

    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse
    ) async {
        guard response.notification.request.content.categoryIdentifier == PurchaseReminderScheduler.category,
              response.actionIdentifier == PurchaseReminderScheduler.buyNowAction else {
            // A plain tap opens the app, which runs due purchases on its own.
            return
        }
        await AutomationCoordinator.shared.runDuePurchases(trigger: .notification)
    }

    /// While the app is open it runs purchases itself, so nothing needs to show.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification
    ) async -> UNNotificationPresentationOptions {
        []
    }
}
