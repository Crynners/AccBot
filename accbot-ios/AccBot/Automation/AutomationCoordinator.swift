import Foundation
import os
#if canImport(WidgetKit)
import WidgetKit
#endif

/// One entry point for every way purchases can start on iOS: the Shortcuts automation,
/// background tasks, the "Buy now" notification action and opening the app.
///
/// After each run it brings the safety nets up to date: the reminder that fires when a
/// purchase did not happen, the widget, and the next background task.
@MainActor
final class AutomationCoordinator {
    static let shared = AutomationCoordinator()

    private let logger = Logger(subsystem: "com.accbot.dca", category: "AutomationCoordinator")

    private init() {}

    private var dependencies: AppDependencies { AppDependencies.shared }

    /// Runs the purchases that are due and refreshes reminders, widget and background tasks.
    /// - Returns: true when every due plan was handled.
    @discardableResult
    func runDuePurchases(trigger: PurchaseTrigger) async -> Bool {
        let deps = dependencies
        guard deps.onboardingPreferences.onboardingCompleted else { return true }
        deps.userPreferences.recordAutomationRun(trigger)
        logger.info("Running due purchases (trigger: \(trigger.rawValue, privacy: .public))")
        let finished = await deps.dcaExecutionEngine.executeDuePlans()
        await refreshSafetyNets()
        return finished
    }

    /// Buys the missed purchases of one plan and refreshes the safety nets.
    @discardableResult
    func buyMissedPurchases(planId: Int64) async -> Bool {
        let finished = await dependencies.dcaExecutionEngine.buyMissedPurchases(planId: planId)
        await refreshSafetyNets()
        return finished
    }

    /// Reschedules reminders, updates the widget and plans the next background task.
    /// Call after anything that changes when the next purchase is due.
    func refreshSafetyNets() async {
        let deps = dependencies
        let plans = (try? deps.activeDatabase.planDao.getAll()) ?? []
        await PurchaseReminderScheduler.reschedule(
            plans: plans,
            enabled: deps.userPreferences.notificationsEnabled,
            isSandbox: deps.userPreferences.sandboxMode
        )
        WidgetSnapshotStore.write(WidgetSnapshot.make(
            plans: plans,
            lastPurchaseAt: try? deps.activeDatabase.transactionDao.lastFilledPurchaseDate(),
            isSandbox: deps.userPreferences.sandboxMode
        ))
        #if canImport(WidgetKit)
        WidgetCenter.shared.reloadAllTimelines()
        #endif
        DcaBackgroundService.shared.scheduleNext(using: deps)
    }
}
