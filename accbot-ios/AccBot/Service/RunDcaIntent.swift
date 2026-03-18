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

    private static let logger = Logger(subsystem: "com.accbot.dca", category: "RunDcaIntent")

    @MainActor
    func perform() async throws -> some IntentResult & ReturnsValue<String> {
        Self.logger.info("RunDcaIntent triggered via Shortcuts")

        let deps = AppDependencies.shared ?? AppDependencies()
        if AppDependencies.shared == nil {
            AppDependencies.shared = deps
        }

        await deps.dcaExecutionEngine.executeDuePlans()

        // Update last background run timestamp
        deps.userPreferences.lastBackgroundRun = Date()

        // Reschedule background tasks
        DcaBackgroundService.shared.scheduleAppRefresh(using: deps.activeDatabase)

        Self.logger.info("RunDcaIntent completed")
        return .result(value: String(localized: "DCA plans executed"))
    }
}
