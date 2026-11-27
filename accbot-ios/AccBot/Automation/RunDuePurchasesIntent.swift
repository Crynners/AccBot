import AppIntents

/// The action a Shortcuts automation runs at the user's purchase time.
/// It buys only plans that are due, so running it often never buys twice.
struct RunDuePurchasesIntent: AppIntent {
    static var title: LocalizedStringResource = "Run due purchases"
    static var description = IntentDescription(
        "Buys every AccBot plan that is due now. Plans that are not due are left alone, so it is safe to run as often as you like."
    )
    /// Runs in the background, without opening the app.
    static var openAppWhenRun: Bool = false
    /// Lets a time-of-day automation run it while the iPhone is locked.
    static var authenticationPolicy: IntentAuthenticationPolicy = .alwaysAllowed

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        let finished = await AutomationCoordinator.shared.runDuePurchases(trigger: .shortcut)
        let dialog: IntentDialog = finished
            ? "AccBot checked the due purchases."
            : "Some purchases could not run. Open AccBot for details."
        return .result(dialog: dialog)
    }
}

/// Makes the action available in Shortcuts, Spotlight and Siri right after install,
/// so the user only has to add the time automation.
struct AccBotShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: RunDuePurchasesIntent(),
            phrases: [
                "Run purchases in \(.applicationName)",
                "Run due purchases in \(.applicationName)",
            ],
            shortTitle: "Run purchases",
            systemImageName: "bitcoinsign.circle"
        )
    }
}
