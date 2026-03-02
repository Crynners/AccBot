import UIKit
import BackgroundTasks

/// AppDelegate for BGTaskScheduler registration
class AppDelegate: NSObject, UIApplicationDelegate {
    static let bgRefreshIdentifier = "com.accbot.dca.refresh"
    static let bgProcessingIdentifier = "com.accbot.dca.processing"

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        registerBackgroundTasks()
        return true
    }

    private func registerBackgroundTasks() {
        BGTaskScheduler.shared.register(
            forTaskWithIdentifier: Self.bgRefreshIdentifier,
            using: nil
        ) { task in
            guard let refreshTask = task as? BGAppRefreshTask else { return }
            Task {
                await DcaBackgroundService.shared.handleAppRefresh(task: refreshTask)
            }
        }

        BGTaskScheduler.shared.register(
            forTaskWithIdentifier: Self.bgProcessingIdentifier,
            using: nil
        ) { task in
            guard let processingTask = task as? BGProcessingTask else { return }
            Task {
                await DcaBackgroundService.shared.handleProcessing(task: processingTask)
            }
        }
    }
}
