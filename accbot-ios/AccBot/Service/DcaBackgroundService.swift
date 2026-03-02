import Foundation
import BackgroundTasks
import os

/// BGTaskScheduler orchestration for iOS background DCA execution.
/// Implements a 4-layer approach to ensure reliable execution.
final class DcaBackgroundService {
    static let shared = DcaBackgroundService()

    private let logger = Logger(subsystem: "com.accbot.dca", category: "DcaBackgroundService")

    private init() {}

    // MARK: - Layer 1: BGAppRefreshTask

    /// Handle a background app refresh task
    func handleAppRefresh(task: BGAppRefreshTask) async {
        logger.info("BGAppRefreshTask started")

        task.expirationHandler = {
            self.logger.warning("BGAppRefreshTask expired")
        }

        // Schedule the next refresh first (self-perpetuating)
        scheduleAppRefresh()

        // Execute due plans (runtime ~30s, enough for 1-2 API calls)
        let deps = await MainActor.run { AppDependencies.shared ?? AppDependencies() }
        await deps.dcaExecutionEngine.executeDuePlans()

        // Update last background run timestamp
        await MainActor.run {
            deps.userPreferences.lastBackgroundRun = Date()
        }

        task.setTaskCompleted(success: true)
        logger.info("BGAppRefreshTask completed")
    }

    // MARK: - Layer 2: BGProcessingTask

    /// Handle a background processing task (longer runtime)
    func handleProcessing(task: BGProcessingTask) async {
        logger.info("BGProcessingTask started")

        task.expirationHandler = {
            self.logger.warning("BGProcessingTask expired")
        }

        // Schedule next processing task
        scheduleProcessingTask()

        let deps = await MainActor.run { AppDependencies.shared ?? AppDependencies() }

        // Resolve pending transactions
        await deps.dcaExecutionEngine.executeDuePlans()

        // Sync daily prices for portfolio charts
        // await deps.marketDataService.syncDailyPrices(...)

        await MainActor.run {
            deps.userPreferences.lastBackgroundRun = Date()
        }

        task.setTaskCompleted(success: true)
        logger.info("BGProcessingTask completed")
    }

    // MARK: - Scheduling

    func scheduleAppRefresh() {
        let request = BGAppRefreshTaskRequest(identifier: AppDelegate.bgRefreshIdentifier)

        // Set earliest begin date to next plan's execution time
        // If no plans, default to 1 hour
        // The actual date should be fetched from the database
        request.earliestBeginDate = Date(timeIntervalSinceNow: 3600)

        do {
            try BGTaskScheduler.shared.submit(request)
            logger.info("Scheduled BGAppRefreshTask")
        } catch {
            logger.error("Failed to schedule BGAppRefreshTask: \(error.localizedDescription)")
        }
    }

    func scheduleAppRefresh(at date: Date) {
        let request = BGAppRefreshTaskRequest(identifier: AppDelegate.bgRefreshIdentifier)
        request.earliestBeginDate = date

        do {
            try BGTaskScheduler.shared.submit(request)
            logger.info("Scheduled BGAppRefreshTask for \(date)")
        } catch {
            logger.error("Failed to schedule BGAppRefreshTask: \(error.localizedDescription)")
        }
    }

    func scheduleProcessingTask() {
        let request = BGProcessingTaskRequest(identifier: AppDelegate.bgProcessingIdentifier)
        request.requiresNetworkConnectivity = true
        request.requiresExternalPower = false
        request.earliestBeginDate = Date(timeIntervalSinceNow: 3600 * 4) // Every ~4 hours

        do {
            try BGTaskScheduler.shared.submit(request)
            logger.info("Scheduled BGProcessingTask")
        } catch {
            logger.error("Failed to schedule BGProcessingTask: \(error.localizedDescription)")
        }
    }

    /// Cancel all scheduled background tasks
    func cancelAll() {
        BGTaskScheduler.shared.cancelAllTaskRequests()
        logger.info("Cancelled all background tasks")
    }
}
