import Foundation
import BackgroundTasks
import os

/// BGTaskScheduler orchestration for iOS background DCA execution.
final class DcaBackgroundService {
    static let shared = DcaBackgroundService()

    private let logger = Logger(subsystem: "com.accbot.dca", category: "DcaBackgroundService")

    private init() {}

    // MARK: - Layer 1: BGAppRefreshTask

    /// Handle a background app refresh task
    func handleAppRefresh(task: BGAppRefreshTask) async {
        await run(task)
    }

    // MARK: - Layer 2: BGProcessingTask

    /// Handle a background processing task (longer runtime)
    func handleProcessing(task: BGProcessingTask) async {
        await run(task)
    }

    private func run(_ task: BGTask) async {
        let work = Task { @MainActor in
            let deps = AppDependencies.shared
            scheduleNext(using: deps)
            let finished = await deps.dcaExecutionEngine.executeDuePlans()
            scheduleNext(using: deps)
            if finished && !Task.isCancelled {
                deps.userPreferences.lastBackgroundRun = Date()
                return true
            }
            return false
        }
        task.expirationHandler = { work.cancel() }
        let finished = await work.value
        task.expirationHandler = nil
        task.setTaskCompleted(success: finished)
    }

    // MARK: - Scheduling

    @MainActor
    func scheduleNext(using dependencies: AppDependencies) {
        let db = dependencies.activeDatabase
        let now = Date()
        let nextPlan = try? db.planDao.getNextExecutionDate()
        let nextCheck = nextPlan ?? now.addingTimeInterval(3600)
        // Use a floor for overdue plans and revisit receipts within an hour.
        let earliest = max(now.addingTimeInterval(15 * 60), min(nextCheck, now.addingTimeInterval(3600)))
        scheduleAppRefresh(at: earliest)
        scheduleProcessingTask()
    }

    private func scheduleAppRefresh(at date: Date) {
        let request = BGAppRefreshTaskRequest(identifier: AppDelegate.bgRefreshIdentifier)
        request.earliestBeginDate = date

        do {
            try BGTaskScheduler.shared.submit(request)
            logger.info("Scheduled BGAppRefreshTask for \(date)")
        } catch {
            logger.error("Failed to schedule BGAppRefreshTask: \(error.localizedDescription)")
        }
    }

    private func scheduleProcessingTask() {
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
