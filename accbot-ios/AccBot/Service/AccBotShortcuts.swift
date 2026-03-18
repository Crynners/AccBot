import AppIntents

/// Registers AccBot shortcuts in the Shortcuts app automatically.
/// The "Run DCA Plans" action appears without any user setup.
struct AccBotShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: RunDcaIntent(),
            phrases: [
                "Run DCA plans in \(.applicationName)",
                "Execute DCA in \(.applicationName)",
                "Spustit DCA v \(.applicationName)"
            ],
            shortTitle: "Run DCA",
            systemImageName: "bolt.fill"
        )
    }
}
