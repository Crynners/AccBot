import AppIntents
import os

/// App Intent that executes all due DCA plans.
/// Available in Shortcuts app as "Run DCA Plans" action.
/// Users can create time-based automations in Shortcuts for precise execution.
struct RunDcaIntent: AppIntent {
    static var title: LocalizedStringResource = "Run DCA Plans"
    static var description: IntentDescription = IntentDescription(
        "Execute all due DCA purchase plans. Use this in Shortcuts automations for precise scheduling.",
        categoryName: "DCA"
    )
    static var openAppWhenRun: Bool = false

    /// Shortcuts automations may fire slightly before the exact scheduled time.
    /// A 2-minute tolerance ensures plans due within this window are included.
    private static let toleranceSeconds: TimeInterval = 120

    private static let logger = Logger(subsystem: "com.accbot.dca", category: "RunDcaIntent")

    @MainActor
    func perform() async throws -> some IntentResult & ReturnsValue<String> {
        Self.logger.info("RunDcaIntent triggered via Shortcuts")

        let deps = AppDependencies.shared ?? AppDependencies()
        if AppDependencies.shared == nil {
            AppDependencies.shared = deps
        }

        // Use a tolerance window so plans scheduled at the same time as
        // the Shortcuts automation are picked up even if Shortcuts fires
        // a few seconds early.
        let cutoff = Date().addingTimeInterval(Self.toleranceSeconds)
        await deps.dcaExecutionEngine.executeDuePlans(before: cutoff)

        // Update last background run timestamp
        deps.userPreferences.lastBackgroundRun = Date()

        // Reschedule all background layers
        DcaBackgroundService.shared.rescheduleAllLayers(
            using: deps.activeDatabase,
            notificationService: deps.notificationService
        )

        Self.logger.info("RunDcaIntent completed")
        return .result(value: String(localized: "DCA plans executed"))
    }
}
