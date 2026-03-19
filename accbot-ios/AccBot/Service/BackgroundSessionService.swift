import Foundation
import os

/// Background URLSession layer for DCA execution.
/// Uses iOS nsurlsessiond daemon to wake the app via a lightweight exchange ping,
/// independent of BGTaskScheduler budget.
final class BackgroundSessionService: NSObject, URLSessionDownloadDelegate {
    static let shared = BackgroundSessionService()
    static let sessionIdentifier = "com.accbot.dca.bgSession"

    private let logger = Logger(subsystem: "com.accbot.dca", category: "BackgroundSession")
    private var completionHandler: (() -> Void)?

    private lazy var session: URLSession = {
        let config = URLSessionConfiguration.background(
            withIdentifier: Self.sessionIdentifier
        )
        config.sessionSendsLaunchEvents = true
        config.isDiscretionary = false
        config.allowsCellularAccess = true
        config.timeoutIntervalForResource = 60
        return URLSession(configuration: config, delegate: self, delegateQueue: nil)
    }()

    private override init() { super.init() }

    /// Schedule a background download timed to the next DCA plan execution.
    func scheduleNextPing(using database: DcaDatabase) {
        session.getAllTasks { [weak self] tasks in
            guard let self else { return }
            tasks.forEach { $0.cancel() }

            guard let nextDate = try? database.planDao.getNextExecutionDate(),
                  nextDate > Date()
            else {
                self.logger.info("No future plans, skipping background ping")
                return
            }

            let url = URL(string: "https://api.binance.com/api/v3/ping")!
            let task = self.session.downloadTask(with: url)
            task.earliestBeginDate = nextDate
            task.resume()

            self.logger.info("Scheduled background ping for \(nextDate)")
        }
    }

    /// Store the system completion handler (called from AppDelegate).
    func setCompletionHandler(_ handler: @escaping () -> Void) {
        completionHandler = handler
    }

    // MARK: - URLSessionDownloadDelegate

    func urlSession(
        _ session: URLSession,
        downloadTask: URLSessionDownloadTask,
        didFinishDownloadingTo location: URL
    ) {
        logger.info("Background ping completed, triggering DCA execution")

        Task {
            let deps = await MainActor.run {
                let d = AppDependencies.shared ?? AppDependencies()
                if AppDependencies.shared == nil { AppDependencies.shared = d }
                return d
            }

            await deps.dcaExecutionEngine.executeDuePlans()

            let (db, notifService) = await MainActor.run {
                deps.userPreferences.lastBackgroundRun = Date()
                return (deps.activeDatabase, deps.notificationService)
            }
            DcaBackgroundService.shared.rescheduleAllLayers(
                using: db, notificationService: notifService
            )
        }
    }

    func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        didCompleteWithError error: Error?
    ) {
        if let error {
            logger.warning("Background ping failed: \(error.localizedDescription)")
        }
        DispatchQueue.main.async { [weak self] in
            self?.completionHandler?()
            self?.completionHandler = nil
        }
    }
}
